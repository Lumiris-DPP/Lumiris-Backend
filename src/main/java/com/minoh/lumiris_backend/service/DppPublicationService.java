package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.dto.out.DppFormCreatedResponse;
import com.minoh.lumiris_backend.dto.out.IrisScoreResponse;
import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.entity.DppCareInstruction;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppMaterial;
import com.minoh.lumiris_backend.entity.DppStatus;
import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.DppCareInstructionRepository;
import com.minoh.lumiris_backend.repository.DppEventRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.DppMaterialRepository;
import com.minoh.lumiris_backend.repository.IrisScoreRepository;
import com.minoh.lumiris_backend.service.scoring.IrisScoreCalculator;
import com.minoh.lumiris_backend.util.DppHashUtil;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cycle de vie en écriture d'un DPP : brouillon (création, édition, duplication, suppression)
 * puis publication. Publier fige le passeport : code public (QR), hash des données, score Iris
 * et ancrage blockchain. Au-delà, les triggers PostgreSQL interdisent toute modification.
 */
@Service
@RequiredArgsConstructor
public class DppPublicationService {

    private static final String PUBLIC_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int PUBLIC_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DppFormRepository dppFormRepository;
    private final DppMaterialRepository dppMaterialRepository;
    private final DppCareInstructionRepository dppCareInstructionRepository;
    private final DppEventRepository dppEventRepository;
    private final IrisScoreRepository irisScoreRepository;
    private final DppFormMapper dppFormMapper;
    private final IrisScoreCalculator irisScoreCalculator;
    private final TransactionTemplate transactionTemplate;
    private final DppHashUtil dppHashUtil;
    private final BlockchainService blockchainService;
    private final QuotaService quotaService;
    private final NotificationService notificationService;
    private final DppDocumentService documentService;
    private final DppOwnershipGuard ownershipGuard;

    public DppFormCreatedResponse create(DppFormRequest request, Map<String, MultipartFile> files,
                                         String userEmail, boolean draft) {
        Map<String, UUID> uploadedIds = documentService.upload(files, userEmail);

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            User user = ownershipGuard.currentUser(userEmail);
            documentService.resolveCertificateLibraryRefs(request, files, uploadedIds, user);

            // Billing gate: require an active passport-granting subscription within quota.
            // Inside the create transaction so assertCanCreate's SELECT ... FOR UPDATE is TOCTOU-safe.
            // Drafts don't consume quota — the gate runs at publication instead.
            if (!draft) {
                quotaService.assertCanCreate(user);
            }

            DppForm form = dppFormMapper.toEntity(request, user);
            if (draft) {
                // No QR identity, no frozen hash, no score, no blockchain anchor until publication.
                form.setStatus(DppStatus.DRAFT);
            } else {
                // Publication directe : les invariants d'un passeport public doivent être remplis
                // (un brouillon, lui, reste tolérant).
                assertPublishable(form);
                form.setPublicCode(generateUniquePublicCode());
                form.setDataHash(dppHashUtil.generateDppHash(dppFormMapper.toHashableData(form)));
                form.setBlockchainAnchorStatus(BlockchainAnchorStatus.PENDING);
            }

            documentService.attachMainPhoto(form, uploadedIds);

            DppForm savedForm = dppFormRepository.save(form);
            documentService.saveDocuments(savedForm, uploadedIds, false);

            if (!draft) {
                Set<DocumentType> uploadedDocTypes = uploadedIds.keySet().stream()
                        .flatMap(partName -> DocumentType.fromPartName(partName).stream())
                        .collect(Collectors.toSet());
                finalizePublication(savedForm, uploadedDocTypes);
            }

            return new DppFormCreatedResponse(savedForm.getId());
        }));
    }

    public DppFormCreatedResponse update(UUID id, DppFormRequest request, Map<String, MultipartFile> files,
                                         String userEmail) {
        Map<String, UUID> uploadedIds = documentService.upload(files, userEmail);

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            DppForm form = ownershipGuard.loadOwnedDraft(id, userEmail);
            documentService.resolveCertificateLibraryRefs(request, files, uploadedIds, form.getUser());

            dppFormMapper.applyScalars(form, request);

            dppMaterialRepository.deleteByDppForm(form);
            dppCareInstructionRepository.deleteByDppForm(form);
            saveChildrenDirect(form, request);

            documentService.attachMainPhoto(form, uploadedIds);
            documentService.saveDocuments(form, uploadedIds, true);

            dppFormRepository.save(form);
            return new DppFormCreatedResponse(form.getId());
        }));
    }

    @Transactional
    public void delete(UUID id, String userEmail) {
        DppForm form = ownershipGuard.loadOwnedDraft(id, userEmail);

        dppMaterialRepository.deleteByDppForm(form);
        dppCareInstructionRepository.deleteByDppForm(form);
        documentService.deleteDocuments(form);
        dppEventRepository.deleteByDppFormId(form.getId());
        irisScoreRepository.findByDppFormId(form.getId()).ifPresent(irisScoreRepository::delete);
        dppFormRepository.delete(form);
    }

    @Transactional
    public DppFormCreatedResponse duplicate(UUID id, String userEmail) {
        DppForm source = ownershipGuard.loadOwned(id, userEmail);

        DppForm copy = new DppForm();
        copy.setUser(source.getUser());
        copy.setStatus(DppStatus.DRAFT);
        copy.setProductName(source.getProductName() == null ? null : source.getProductName() + " (copie)");
        copy.setProductDescription(source.getProductDescription());
        copy.setProductCategory(source.getProductCategory());
        copy.setOriginCountry(source.getOriginCountry());
        copy.setManufacturedAt(source.getManufacturedAt());
        copy.setBatchNumber(source.getBatchNumber());
        copy.setQuantity(source.getQuantity());
        // Pas de gtin : la colonne est UNIQUE, deux passeports ne peuvent pas porter le même.
        copy.setSku(source.getSku());
        copy.setReachCompliant(source.getReachCompliant());
        copy.setRecycledPct(source.getRecycledPct());
        copy.setWarrantyDescription(source.getWarrantyDescription());
        copy.setWarrantyMonths(source.getWarrantyMonths());
        copy.setWeightGrams(source.getWeightGrams());
        copy.setIsRepairable(source.getIsRepairable());
        copy.setEndOfLifeInstructions(source.getEndOfLifeInstructions());
        copy.setAvailableSizes(source.getAvailableSizes() == null ? null : new ArrayList<>(source.getAvailableSizes()));
        copy.setColors(source.getColors() == null ? null : new ArrayList<>(source.getColors()));
        copy.setCareNotes(source.getCareNotes());
        copy.setMainPhotoFile(source.getMainPhotoFile());
        dppFormRepository.save(copy);

        for (DppMaterial material : source.getMaterials()) {
            DppMaterial materialCopy = new DppMaterial();
            materialCopy.setDppForm(copy);
            materialCopy.setFiber(material.getFiber());
            materialCopy.setPercentage(material.getPercentage());
            materialCopy.setOriginCountry(material.getOriginCountry());
            materialCopy.setLatitude(material.getLatitude());
            materialCopy.setLongitude(material.getLongitude());
            dppMaterialRepository.save(materialCopy);
        }

        for (DppCareInstruction care : source.getCareInstructions()) {
            DppCareInstruction careCopy = new DppCareInstruction();
            careCopy.setDppForm(copy);
            careCopy.setCareCode(care.getCareCode());
            dppCareInstructionRepository.save(careCopy);
        }

        documentService.copyDocuments(source, copy);

        return new DppFormCreatedResponse(copy.getId());
    }

    @Transactional
    public DppFormCreatedResponse publish(UUID id, String userEmail) {
        DppForm form = ownershipGuard.loadOwnedDraft(id, userEmail);

        // The billing gate deferred at draft creation runs here.
        quotaService.assertCanCreate(form.getUser());
        // DRAFT → VALID : le passeport doit être complet avant d'obtenir un QR public + un score.
        assertPublishable(form);

        Hibernate.initialize(form.getMaterials());
        Hibernate.initialize(form.getCareInstructions());
        Hibernate.initialize(form.getDocuments());

        // Compute code + hash while the form is still clean: generateUniquePublicCode() runs a
        // SELECT that would auto-flush the session. If status were already VALID at that flush,
        // the trigger's published branch (NEW := OLD) would silently drop the later code/hash
        // update. Set every field only after, so publication commits as one DRAFT→VALID UPDATE.
        String publicCode = generateUniquePublicCode();
        String dataHash = dppHashUtil.generateDppHash(dppFormMapper.toHashableData(form));
        form.setStatus(DppStatus.VALID);
        form.setPublicCode(publicCode);
        form.setDataHash(dataHash);
        form.setBlockchainAnchorStatus(BlockchainAnchorStatus.PENDING);
        DppForm savedForm = dppFormRepository.save(form);

        finalizePublication(savedForm, savedForm.attachedDocumentTypes());

        return new DppFormCreatedResponse(savedForm.getId());
    }

    @Transactional
    public int backfillMissingIrisScores() {
        List<DppForm> forms = dppFormRepository.findWithoutIrisScore(DppStatus.VALID);
        forms.forEach(form -> saveIrisScore(form, form.attachedDocumentTypes()));
        return forms.size();
    }

    // Invariants minimaux d'un passeport PUBLIÉ (un brouillon reste volontairement tolérant). Défense
    // en profondeur : le front valide déjà, mais un publish direct / hors UI ne doit pas créer un
    // passeport public incomplet. 400 avec le détail des champs manquants.
    private void assertPublishable(DppForm form) {
        List<String> missing = new ArrayList<>();
        if (isBlank(form.getProductName())) missing.add("le nom du produit");
        if (isBlank(form.getProductCategory())) missing.add("la catégorie");
        if (isBlank(form.getOriginCountry())) missing.add("le pays d'origine");
        if (form.getRecycledPct() != null && (form.getRecycledPct() < 0 || form.getRecycledPct() > 100)) {
            missing.add("un pourcentage recyclé entre 0 et 100");
        }
        if (form.getQuantity() < 1) missing.add("une quantité d'au moins 1");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Passeport incomplet : renseignez " + String.join(", ", missing) + ".");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Ce qui suit toute publication, directe ou depuis un brouillon : score figé, ancrage, notification. */
    private void finalizePublication(DppForm form, Set<DocumentType> docTypes) {
        saveIrisScore(form, docTypes);
        anchorAfterCommit(form.getId(), form.getDataHash());
        notificationService.notifyPassportPublished(form.getUser(), form.getProductName(), publicPath(form));
    }

    /** Persist materials and care instructions straight through their repos (owning side sets the FK). */
    private void saveChildrenDirect(DppForm form, DppFormRequest request) {
        if (request.materials() != null) {
            // Même fabrique que la création : les coordonnées géocodées suivent chaque écriture.
            request.materials().forEach(m -> dppMaterialRepository.save(dppFormMapper.toMaterial(form, m)));
        }
        if (request.careInstructions() != null) {
            request.careInstructions().forEach(code -> {
                DppCareInstruction care = new DppCareInstruction();
                care.setDppForm(form);
                care.setCareCode(code);
                dppCareInstructionRepository.save(care);
            });
        }
    }

    private void saveIrisScore(DppForm form, Set<DocumentType> docTypes) {
        IrisScoreResponse scoreResponse = irisScoreCalculator.compute(DppScoreInput.from(form, docTypes));
        irisScoreRepository.save(new IrisScore(
                form,
                scoreResponse.breakdown().transparency(),
                scoreResponse.breakdown().craftsmanship(),
                scoreResponse.breakdown().repairability(),
                scoreResponse.breakdown().impact(),
                scoreResponse.total(),
                scoreResponse.grade()
        ));
    }

    // Fire async anchor only after the transaction commits so the row exists in DB.
    // Falls back to direct call when no active transaction (e.g. unit tests).
    private void anchorAfterCommit(UUID formId, String hash) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    blockchainService.anchorAsync(formId, hash);
                }
            });
        } else {
            blockchainService.anchorAsync(formId, hash);
        }
    }

    // Chemin relatif de la page publique du passeport (apps/mobile/app/p, route `?c=<code>`) — les
    // hrefs de notification restent relatifs, le front préfixe (voir Notification.href).
    private String publicPath(DppForm form) {
        return "/p?c=" + form.getPublicCode();
    }

    private String generateUniquePublicCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(PUBLIC_CODE_LENGTH);
            for (int i = 0; i < PUBLIC_CODE_LENGTH; i++) {
                sb.append(PUBLIC_CODE_CHARS.charAt(RANDOM.nextInt(PUBLIC_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (dppFormRepository.existsByPublicCode(code));
        return code;
    }
}
