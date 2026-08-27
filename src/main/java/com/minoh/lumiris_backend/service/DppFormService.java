package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.dto.out.DppAccessTokenResponse;
import com.minoh.lumiris_backend.dto.out.DppFormCreatedResponse;
import com.minoh.lumiris_backend.dto.out.DppFormDocumentResponse;
import com.minoh.lumiris_backend.dto.out.DppFormPublicResponse;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.dto.out.DppFormSummaryResponse;
import com.minoh.lumiris_backend.dto.out.IrisScoreResponse;
import com.minoh.lumiris_backend.entity.*;
import com.minoh.lumiris_backend.dto.out.DppVerificationResponse;
import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.DppCareInstructionRepository;
import com.minoh.lumiris_backend.repository.DppEventRepository;
import com.minoh.lumiris_backend.repository.DppFormDocumentRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.DppMaterialRepository;
import com.minoh.lumiris_backend.repository.IrisScoreRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.service.scoring.IrisScoreCalculator;
import com.minoh.lumiris_backend.util.DppHashUtil;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DppFormService {

    private static final String PUBLIC_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int PUBLIC_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DppFormRepository dppFormRepository;
    private final DppMaterialRepository dppMaterialRepository;
    private final DppCareInstructionRepository dppCareInstructionRepository;
    private final DppFormDocumentRepository dppFormDocumentRepository;
    private final DppEventRepository dppEventRepository;
    private final ArtisanProfileRepository artisanProfileRepository;
    private final AtelierStatsService atelierStatsService;
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final IrisScoreRepository irisScoreRepository;
    private final StorageService storageService;
    private final DppFormMapper dppFormMapper;
    private final IrisScoreCalculator irisScoreCalculator;
    private final TransactionTemplate transactionTemplate;
    private final DppHashUtil dppHashUtil;
    private final BlockchainService blockchainService;
    private final QuotaService quotaService;
    private final DppAccessTokenService accessTokenService;

    // Invariants minimaux d'un passeport PUBLIÉ (un brouillon reste volontairement tolérant). Défense
    // en profondeur : le front valide déjà, mais un publish direct / hors UI ne doit pas créer un
    // passeport public incomplet. 400 avec le détail des champs manquants.
    private void assertPublishable(DppForm form) {
        java.util.List<String> missing = new java.util.ArrayList<>();
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

    public DppFormCreatedResponse create(DppFormRequest request, Map<String, MultipartFile> files,
                                         String userEmail, boolean draft) {
        Map<String, UUID> uploadedIds = uploadFiles(files, userEmail);

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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

            attachMainPhoto(form, uploadedIds);

            DppForm savedForm = dppFormRepository.save(form);
            saveDocuments(savedForm, uploadedIds, false);

            if (!draft) {
                Set<DocumentType> uploadedDocTypes = uploadedIds.keySet().stream()
                        .flatMap(partName -> DocumentType.fromPartName(partName).stream())
                        .collect(Collectors.toSet());
                saveIrisScore(savedForm, uploadedDocTypes);
                anchorAfterCommit(savedForm.getId(), savedForm.getDataHash());
            }

            return new DppFormCreatedResponse(savedForm.getId());
        }));
    }

    public DppFormCreatedResponse update(UUID id, DppFormRequest request, Map<String, MultipartFile> files,
                                         String userEmail) {
        Map<String, UUID> uploadedIds = uploadFiles(files, userEmail);

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            DppForm form = loadOwnedDraft(id, userEmail);

            dppFormMapper.applyScalars(form, request);

            dppMaterialRepository.deleteByDppForm(form);
            dppCareInstructionRepository.deleteByDppForm(form);
            saveChildrenDirect(form, request);

            attachMainPhoto(form, uploadedIds);
            saveDocuments(form, uploadedIds, true);

            dppFormRepository.save(form);
            return new DppFormCreatedResponse(form.getId());
        }));
    }

    @Transactional
    public void delete(UUID id, String userEmail) {
        DppForm form = loadOwnedDraft(id, userEmail);

        dppMaterialRepository.deleteByDppForm(form);
        dppCareInstructionRepository.deleteByDppForm(form);
        dppFormDocumentRepository.deleteByDppForm(form);
        dppEventRepository.deleteByDppFormId(form.getId());
        irisScoreRepository.findByDppFormId(form.getId()).ifPresent(irisScoreRepository::delete);
        dppFormRepository.delete(form);
    }

    // Recopie un brouillon à l'identique, enfants compris. Les documents et la photo pointent vers
    // les mêmes fichiers stockés : ce sont des blobs immuables, inutile de les dupliquer.
    @Transactional
    public DppFormCreatedResponse duplicate(UUID id, String userEmail) {
        DppForm source = loadOwnedDraft(id, userEmail);

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

        for (DppFormDocument document : source.getDocuments()) {
            DppFormDocument documentCopy = new DppFormDocument();
            documentCopy.setDppForm(copy);
            documentCopy.setFile(document.getFile());
            documentCopy.setDocumentType(document.getDocumentType());
            documentCopy.setVisibility(document.getVisibility());
            dppFormDocumentRepository.save(documentCopy);
        }

        return new DppFormCreatedResponse(copy.getId());
    }

    @Transactional
    public DppFormCreatedResponse publish(UUID id, String userEmail) {
        DppForm form = loadOwnedDraft(id, userEmail);

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

        saveIrisScore(savedForm, savedForm.attachedDocumentTypes());
        anchorAfterCommit(savedForm.getId(), savedForm.getDataHash());

        return new DppFormCreatedResponse(savedForm.getId());
    }

    private Map<String, UUID> uploadFiles(Map<String, MultipartFile> files, String userEmail) {
        Map<String, UUID> uploadedIds = new LinkedHashMap<>();
        files.forEach((partName, file) -> {
            if (file != null && !file.isEmpty()) {
                uploadedIds.put(partName, storageService.upload(file, userEmail).id());
            }
        });
        return uploadedIds;
    }

    /** The main photo is carried by the form itself, so it must be applied before the form is saved. */
    private void attachMainPhoto(DppForm form, Map<String, UUID> uploadedIds) {
        UUID photoId = uploadedIds.get("productPhoto");
        if (photoId != null) {
            form.setMainPhotoFile(storedFileRepository.getReferenceById(photoId));
        }
    }

    /**
     * Persist uploaded documents straight through their repo (the child @ManyToOne owns the FK);
     * when {@code replaceExisting}, a part supersedes the stored document of the same type.
     * Going through {@code form.getDocuments()} instead would insert an empty row with a null
     * dpp_form_id: on an unloaded lazy collection Hibernate queues the add and flushes it without
     * the entity's state — the same trap materials and care instructions already avoid.
     * The form must already be persisted.
     */
    private void saveDocuments(DppForm form, Map<String, UUID> uploadedIds, boolean replaceExisting) {
        uploadedIds.forEach((partName, fileId) -> {
            if ("productPhoto".equals(partName)) return;
            DocumentType.fromPartName(partName).ifPresent(docType -> {
                if (replaceExisting) {
                    dppFormDocumentRepository.deleteByDppFormAndDocumentType(form, docType);
                }
                DppFormDocument doc = new DppFormDocument();
                doc.setDppForm(form);
                doc.setFile(storedFileRepository.getReferenceById(fileId));
                doc.setDocumentType(docType);
                doc.setVisibility(docType.defaultVisibility());
                dppFormDocumentRepository.save(doc);
            });
        });
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

    private DppForm loadOwnedDraft(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        DppForm form = dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        if (!form.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("DPP not found");
        }
        if (form.getStatus() != DppStatus.DRAFT) {
            throw new ConflictException("Seul un DPP en brouillon peut être modifié, dupliqué, supprimé ou publié.");
        }
        return form;
    }

    @Transactional(readOnly = true)
    public List<DppFormSummaryResponse> findAllByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return dppFormRepository.findByUserId(user.getId()).stream()
                .map(dppFormMapper::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DppFormResponse findById(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        DppForm form = dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        if (!form.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("DPP not found");
        }
        Hibernate.initialize(form.getMaterials());
        Hibernate.initialize(form.getCareInstructions());
        Hibernate.initialize(form.getDocuments());

        String mainPhotoUrl = form.getMainPhotoFile() != null
                ? storageService.getPresignedUrl(form.getMainPhotoFile().getId())
                : null;

        // Le propriétaire voit ses propres documents, toutes visibilités confondues.
        List<DppFormDocumentResponse> documents = mapDocuments(form, EnumSet.allOf(DppDocumentVisibility.class));

        String artisanSlug = artisanProfileRepository.findByUser(form.getUser())
                .map(ArtisanProfile::getSlug)
                .orElse(null);

        return dppFormMapper.toResponse(form, mainPhotoUrl, documents, artisanSlug);
    }

    @Transactional(readOnly = true)
    public IrisScoreResponse getIrisScore(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        DppForm form = dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        if (!form.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("DPP not found");
        }
        IrisScore score = irisScoreRepository.findByDppFormId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Score not found"));

        return toScoreResponse(score);
    }

    private static IrisScoreResponse toScoreResponse(IrisScore score) {
        return new IrisScoreResponse(
                score.getTotal(),
                score.getGrade(),
                new IrisScoreResponse.Breakdown(
                        score.getTransparency(),
                        score.getCraftsmanship(),
                        score.getImpact(),
                        score.getRepairability()
                ),
                IrisScoreResponse.FIXED_WEIGHTS,
                List.of()
        );
    }

    @Transactional
    public int backfillMissingIrisScores() {
        List<DppForm> forms = dppFormRepository.findWithoutIrisScore(DppStatus.VALID);
        forms.forEach(form -> saveIrisScore(form, form.attachedDocumentTypes()));
        return forms.size();
    }

    public IrisScoreResponse computeIrisScore(DppScoreInput input) {
        return irisScoreCalculator.compute(input);
    }

    @Transactional(readOnly = true)
    public DppFormPublicResponse findByPublicCode(String publicCode, String accessToken) {
        DppForm form = dppFormRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));

        Hibernate.initialize(form.getMaterials());
        Hibernate.initialize(form.getCareInstructions());
        Hibernate.initialize(form.getDocuments());

        DppAccessLevel accessLevel = accessTokenService.resolve(publicCode, accessToken);

        String mainPhotoUrl = form.getMainPhotoFile() != null
                ? storageService.getPresignedUrl(form.getMainPhotoFile().getId())
                : null;

        List<DppFormDocumentResponse> documents = mapDocuments(form, accessLevel.visibilities());

        String artisanSlug = artisanProfileRepository.findByUser(form.getUser())
                .filter(ArtisanProfile::isPublished)
                .filter(p -> p.getStatus() == ArtisanStatus.VERIFIED)
                .map(ArtisanProfile::getSlug)
                .orElse(null);

        DppFormResponse dppResponse = dppFormMapper.toResponse(form, mainPhotoUrl, documents, artisanSlug);

        IrisScoreResponse scoreResponse = irisScoreRepository.findByDppFormId(form.getId())
                .map(DppFormService::toScoreResponse)
                .orElse(null);

        atelierStatsService.trackScan(form);

        return new DppFormPublicResponse(dppResponse, scoreResponse, artisanSlug, accessLevel);
    }

    /** Les trois QR d'un passeport publié : permanents, dérivés du code public, rien à générer. */
    @Transactional(readOnly = true)
    public List<DppAccessTokenResponse> listAccessTokens(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        DppForm form = dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        if (!form.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("DPP not found");
        }
        // Un brouillon n'a pas de code public : les QR n'auraient aucune cible.
        if (form.getPublicCode() == null) {
            throw new ConflictException("Publiez le passeport pour obtenir ses QR codes.");
        }

        return Arrays.stream(DppAccessLevel.values())
                .map(level -> new DppAccessTokenResponse(
                        level, accessTokenService.tokenFor(form.getPublicCode(), level)))
                .toList();
    }

    /**
     * Ne cartographie que les documents dont la visibilité est couverte par {@code scopes}, et ne
     * signe une URL MinIO que pour ceux-là. Le filtrage doit rester ici : une URL présignée émise
     * est un accès accordé, qu'un front la masque ensuite ou non.
     */
    private List<DppFormDocumentResponse> mapDocuments(DppForm form, Set<DppDocumentVisibility> scopes) {
        return form.getDocuments().stream()
                .filter(d -> scopes.contains(d.getVisibility()))
                .map(d -> new DppFormDocumentResponse(
                        d.getFile().getId(),
                        d.getDocumentType().name(),
                        d.getVisibility().name(),
                        d.getFile().getOriginalFilename(),
                        storageService.getPresignedUrl(d.getFile().getId())
                ))
                .toList();
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

    public DppVerificationResponse verify(UUID id) {
        DppForm form = dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DPP form not found: " + id));

        BlockchainAnchorStatus status = form.getBlockchainAnchorStatus();

        if (status == BlockchainAnchorStatus.PENDING) {
            return new DppVerificationResponse(id, false, null, null,
                    form.getBlockchainTxHash(), status, "Blockchain anchor in progress");
        }

        if (status == BlockchainAnchorStatus.FAILED) {
            return new DppVerificationResponse(id, false, null, null,
                    form.getBlockchainTxHash(), status, "Blockchain anchor failed");
        }

        try {
            String blockchainHash = blockchainService.retrieveHash(form.getBlockchainTxHash());
            String recomputedHash = dppHashUtil.generateDppHash(dppFormMapper.toHashableData(form));
            boolean verified = recomputedHash.equals(blockchainHash);
            return new DppVerificationResponse(id, verified, blockchainHash, recomputedHash,
                    form.getBlockchainTxHash(), status, null);
        } catch (Exception e) {
            return new DppVerificationResponse(id, false, null, null,
                    form.getBlockchainTxHash(), status, "Failed to retrieve hash from blockchain: " + e.getMessage());
        }
    }
}
