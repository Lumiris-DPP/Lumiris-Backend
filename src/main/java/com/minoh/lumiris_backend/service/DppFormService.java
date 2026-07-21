package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.in.DppScoreInput;
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
    private final com.minoh.lumiris_backend.repository.MarketplaceProductRepository marketplaceProductRepository;

    // Retrait d'un passeport publié : la conception rend un DPP publié IMMUABLE (aucun retour à DRAFT),
    // mais un passeport erroné/rappelé doit pouvoir être retiré. On passe VALID → INVALID (transition
    // autorisée par le trigger d'immuabilité) et on ARCHIVE l'annonce liée (retirée de la vente/recherche).
    @Transactional
    public DppFormCreatedResponse withdraw(UUID id, String userEmail) {
        DppForm form = dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Passeport introuvable"));
        if (!form.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new ResourceNotFoundException("Passeport introuvable");
        }
        if (form.getStatus() != DppStatus.VALID) {
            throw new com.minoh.lumiris_backend.exception.ConflictException(
                    "Seul un passeport publié (VALID) peut être retiré.");
        }
        form.setStatus(DppStatus.INVALID);
        dppFormRepository.save(form);
        // Retire aussi l'annonce marketplace liée de la vente (archivage), le cas échéant.
        marketplaceProductRepository.findByDppFormId(id).ifPresent(p -> {
            p.setStatus(com.minoh.lumiris_backend.entity.MarketplaceProductStatus.ARCHIVED);
            marketplaceProductRepository.save(p);
        });
        return new DppFormCreatedResponse(form.getId());
    }

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

            attachUploads(form, uploadedIds, false);

            DppForm savedForm = dppFormRepository.save(form);

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

            // Replace children wholesale. Persist the rebuilt rows directly through their repos
            // (the child @ManyToOne owns the FK) rather than through the form's lazy collections —
            // clearing/adding an uninitialized collection queues inserts with a null dpp_form_id.
            dppMaterialRepository.deleteByDppForm(form);
            dppCareInstructionRepository.deleteByDppForm(form);
            saveChildrenDirect(form, request);

            attachUploads(form, uploadedIds, true);

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

        Set<DocumentType> docTypes = savedForm.getDocuments().stream()
                .map(DppFormDocument::getDocumentType)
                .collect(Collectors.toSet());
        if (savedForm.getMainPhotoFile() != null) {
            docTypes.add(DocumentType.PRODUCT_PHOTO);
        }
        saveIrisScore(savedForm, docTypes);
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

    /** Attach uploaded parts to the form; when {@code replaceExisting}, a part supersedes the stored document of the same type. */
    private void attachUploads(DppForm form, Map<String, UUID> uploadedIds, boolean replaceExisting) {
        uploadedIds.forEach((partName, fileId) -> {
            StoredFile storedFile = storedFileRepository.getReferenceById(fileId);
            if ("productPhoto".equals(partName)) {
                form.setMainPhotoFile(storedFile);
                return;
            }
            DocumentType.fromPartName(partName).ifPresent(docType -> {
                if (replaceExisting) {
                    List<DppFormDocument> stale = form.getDocuments().stream()
                            .filter(d -> d.getDocumentType() == docType)
                            .toList();
                    stale.forEach(d -> {
                        form.getDocuments().remove(d);
                        dppFormDocumentRepository.delete(d);
                    });
                }
                DppFormDocument doc = new DppFormDocument();
                doc.setDppForm(form);
                doc.setFile(storedFile);
                doc.setDocumentType(docType);
                doc.setVisibility(docType.defaultVisibility());
                form.getDocuments().add(doc);
            });
        });
    }

    /** Persist materials and care instructions straight through their repos (owning side sets the FK). */
    private void saveChildrenDirect(DppForm form, DppFormRequest request) {
        if (request.materials() != null) {
            request.materials().forEach(m -> {
                DppMaterial material = new DppMaterial();
                material.setDppForm(form);
                material.setFiber(m.fiber());
                material.setPercentage(m.percentage());
                material.setOriginCountry(m.originCountry());
                dppMaterialRepository.save(material);
            });
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
            throw new ConflictException("Seul un DPP en brouillon peut être modifié, supprimé ou publié.");
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

        List<DppFormDocumentResponse> documents = form.getDocuments().stream()
                .map(d -> new DppFormDocumentResponse(
                        d.getFile().getId(),
                        d.getDocumentType().name(),
                        d.getVisibility().name(),
                        d.getFile().getOriginalFilename(),
                        storageService.getPresignedUrl(d.getFile().getId())
                ))
                .toList();

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

        return new IrisScoreResponse(
                score.getTotal(),
                score.getGrade(),
                new IrisScoreResponse.Breakdown(
                        score.getTransparency(),
                        score.getCraftsmanship(),
                        score.getImpact(),
                        score.getRepairability()
                ),
                new IrisScoreResponse.Weights(0.4, 0.25, 0.25, 0.1),
                List.of()
        );
    }

    public IrisScoreResponse computeIrisScore(DppScoreInput input) {
        return irisScoreCalculator.compute(input);
    }

    @Transactional(readOnly = true)
    public DppFormPublicResponse findByPublicCode(String publicCode) {
        DppForm form = dppFormRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));

        Hibernate.initialize(form.getMaterials());
        Hibernate.initialize(form.getCareInstructions());
        Hibernate.initialize(form.getDocuments());

        String mainPhotoUrl = form.getMainPhotoFile() != null
                ? storageService.getPresignedUrl(form.getMainPhotoFile().getId())
                : null;

        List<DppFormDocumentResponse> documents = form.getDocuments().stream()
                .map(d -> new DppFormDocumentResponse(
                        d.getFile().getId(),
                        d.getDocumentType().name(),
                        d.getVisibility().name(),
                        d.getFile().getOriginalFilename(),
                        storageService.getPresignedUrl(d.getFile().getId())
                ))
                .toList();

        String artisanSlug = artisanProfileRepository.findByUser(form.getUser())
                .filter(ArtisanProfile::isPublished)
                .filter(p -> p.getStatus() == ArtisanStatus.VERIFIED)
                .map(ArtisanProfile::getSlug)
                .orElse(null);

        DppFormResponse dppResponse = dppFormMapper.toResponse(form, mainPhotoUrl, documents, artisanSlug);

        IrisScoreResponse scoreResponse = irisScoreRepository.findByDppFormId(form.getId())
                .map(score -> new IrisScoreResponse(
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
                ))
                .orElse(null);

        atelierStatsService.trackScan(form);

        return new DppFormPublicResponse(dppResponse, scoreResponse, artisanSlug);
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
