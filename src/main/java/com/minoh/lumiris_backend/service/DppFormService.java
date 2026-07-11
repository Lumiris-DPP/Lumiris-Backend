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
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.DppFormRepository;
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

    public DppFormCreatedResponse create(DppFormRequest request, Map<String, MultipartFile> files, String userEmail) {
        Map<String, UUID> uploadedIds = new LinkedHashMap<>();
        files.forEach((partName, file) -> {
            if (file != null && !file.isEmpty()) {
                uploadedIds.put(partName, storageService.upload(file, userEmail).id());
            }
        });

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            // Billing gate: require an active passport-granting subscription within quota.
            // Inside the create transaction so assertCanCreate's SELECT ... FOR UPDATE is TOCTOU-safe.
            quotaService.assertCanCreate(user);

            DppForm form = dppFormMapper.toEntity(request, user);
            form.setPublicCode(generateUniquePublicCode());
            form.setDataHash(dppHashUtil.generateDppHash(dppFormMapper.toHashableData(form)));
            form.setBlockchainAnchorStatus(BlockchainAnchorStatus.PENDING);

            uploadedIds.forEach((partName, fileId) -> {
                StoredFile storedFile = storedFileRepository.getReferenceById(fileId);
                if ("productPhoto".equals(partName)) {
                    form.setMainPhotoFile(storedFile);
                    return;
                }
                DocumentType.fromPartName(partName).ifPresent(docType -> {
                    DppFormDocument doc = new DppFormDocument();
                    doc.setDppForm(form);
                    doc.setFile(storedFile);
                    doc.setDocumentType(docType);
                    doc.setVisibility(docType.defaultVisibility());
                    form.getDocuments().add(doc);
                });
            });

            DppForm savedForm = dppFormRepository.save(form);

            Set<DocumentType> uploadedDocTypes = uploadedIds.keySet().stream()
                    .flatMap(partName -> DocumentType.fromPartName(partName).stream())
                    .collect(Collectors.toSet());

            IrisScoreResponse scoreResponse = irisScoreCalculator.compute(DppScoreInput.from(savedForm, uploadedDocTypes));
            irisScoreRepository.save(new IrisScore(
                    savedForm,
                    scoreResponse.breakdown().transparency(),
                    scoreResponse.breakdown().craftsmanship(),
                    scoreResponse.breakdown().repairability(),
                    scoreResponse.breakdown().impact(),
                    scoreResponse.total(),
                    scoreResponse.grade()
            ));

            UUID savedId = savedForm.getId();
            String hash = savedForm.getDataHash();
            // Fire async anchor only after the transaction commits so the row exists in DB.
            // Falls back to direct call when no active transaction (e.g. unit tests).
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        blockchainService.anchorAsync(savedId, hash);
                    }
                });
            } else {
                blockchainService.anchorAsync(savedId, hash);
            }

            return new DppFormCreatedResponse(savedForm.getId());
        }));
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

        return dppFormMapper.toResponse(form, mainPhotoUrl, documents);
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

        DppFormResponse dppResponse = dppFormMapper.toResponse(form, mainPhotoUrl, documents);

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

        return new DppFormPublicResponse(dppResponse, scoreResponse);
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
