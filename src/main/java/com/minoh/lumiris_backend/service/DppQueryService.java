package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.dto.out.DppAccessTokenResponse;
import com.minoh.lumiris_backend.dto.out.DppFormDocumentResponse;
import com.minoh.lumiris_backend.dto.out.DppFormPublicResponse;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.dto.out.DppFormSummaryResponse;
import com.minoh.lumiris_backend.dto.out.DppPublicJsonLdResponse;
import com.minoh.lumiris_backend.dto.out.DppVerificationResponse;
import com.minoh.lumiris_backend.dto.out.IrisScoreResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
import com.minoh.lumiris_backend.entity.DppAccessLevel;
import com.minoh.lumiris_backend.entity.DppDocumentVisibility;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.IrisScoreRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.service.scoring.IrisScoreCalculator;
import com.minoh.lumiris_backend.util.DppHashUtil;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lectures d'un DPP, privées (artisan propriétaire, retoucheur en intervention) comme publiques
 * (scan d'un QR, export JSON-LD), ainsi que la vérification de l'ancrage blockchain.
 * Le périmètre des documents exposés est décidé ici, jamais côté front.
 */
@Service
@RequiredArgsConstructor
public class DppQueryService {

    // Même règle que DppEventService.isServicingRepairer : un retoucheur en cours d'intervention
    // (ou l'ayant terminée) sur ce DPP peut consulter la fiche, pas seulement y écrire un événement.
    private static final Set<RepairRequestStatus> REPAIRER_VIEW_STATUSES =
            Set.of(RepairRequestStatus.ACCEPTED, RepairRequestStatus.IN_PROGRESS, RepairRequestStatus.COMPLETED);

    private final DppFormRepository dppFormRepository;
    private final UserRepository userRepository;
    private final ArtisanProfileRepository artisanProfileRepository;
    private final IrisScoreRepository irisScoreRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final AtelierStatsService atelierStatsService;
    private final DppAccessTokenService accessTokenService;
    private final DppFormMapper dppFormMapper;
    private final IrisScoreCalculator irisScoreCalculator;
    private final DppHashUtil dppHashUtil;
    private final BlockchainService blockchainService;
    private final DppDocumentService documentService;
    private final DppOwnershipGuard ownershipGuard;

    @Transactional(readOnly = true)
    public List<DppFormSummaryResponse> findAllByUser(String userEmail) {
        User user = ownershipGuard.currentUser(userEmail);
        return dppFormRepository.findByUserId(user.getId()).stream()
                .map(dppFormMapper::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DppFormResponse findById(UUID id, String userEmail) {
        User user = ownershipGuard.currentUser(userEmail);
        DppForm form = ownershipGuard.loadForm(id);
        if (!DppOwnershipGuard.isOwner(form, user) && !isServicingRepairer(form, user)) {
            throw new ResourceNotFoundException("DPP not found");
        }
        initializeChildren(form);

        // Le propriétaire voit ses propres documents, toutes visibilités confondues.
        List<DppFormDocumentResponse> documents =
                documentService.visibleDocuments(form, EnumSet.allOf(DppDocumentVisibility.class));

        String artisanSlug = artisanProfileRepository.findByUser(form.getUser())
                .map(ArtisanProfile::getSlug)
                .orElse(null);

        return dppFormMapper.toResponse(form, documentService.mainPhotoUrl(form), documents, artisanSlug);
    }

    @Transactional(readOnly = true)
    public IrisScoreResponse getIrisScore(UUID id, String userEmail) {
        ownershipGuard.loadOwned(id, userEmail);
        IrisScore score = irisScoreRepository.findByDppFormId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Score not found"));

        return toScoreResponse(score);
    }

    /** Aperçu du score pendant la saisie : rien n'est persisté, les labels viennent du profil. */
    @Transactional(readOnly = true)
    public IrisScoreResponse computeIrisScore(DppScoreInput input, String userEmail) {
        DppScoreInput.Labels labels = userRepository.findByEmail(userEmail)
                .map(User::getArtisanProfile)
                .map(profile -> new DppScoreInput.Labels(profile.isEpvLabeled(), profile.isOfgLabeled(),
                        profile.isGotsLabeled(), profile.isOekoTexLabeled()))
                .orElse(DppScoreInput.Labels.NONE);
        return irisScoreCalculator.compute(input.withLabels(labels));
    }

    @Transactional(readOnly = true)
    public DppFormPublicResponse findByPublicCode(String publicCode, String accessToken) {
        DppForm form = loadByPublicCode(publicCode);
        initializeChildren(form);

        DppAccessLevel accessLevel = accessTokenService.resolve(publicCode, accessToken);
        List<DppFormDocumentResponse> documents = documentService.visibleDocuments(form, accessLevel.visibilities());
        String artisanSlug = publicArtisanSlug(form);

        DppFormResponse dppResponse =
                dppFormMapper.toResponse(form, documentService.mainPhotoUrl(form), documents, artisanSlug);

        atelierStatsService.trackScan(form);

        return new DppFormPublicResponse(dppResponse, publishedScore(form), artisanSlug, accessLevel);
    }

    /** Représentation machine strictement publique : aucun jeton ne peut élargir son périmètre. */
    @Transactional(readOnly = true)
    public DppPublicJsonLdResponse findPublicJsonLd(String publicCode, String canonicalId) {
        DppForm form = loadByPublicCode(publicCode);
        initializeChildren(form);

        return dppFormMapper.toPublicJsonLd(
                form,
                documentService.mainPhotoUrl(form),
                documentService.visibleDocuments(form, DppAccessLevel.PUBLIC.visibilities()),
                publishedScore(form),
                publicArtisanSlug(form),
                canonicalId
        );
    }

    /** Les trois QR d'un passeport publié : permanents, dérivés du code public, rien à générer. */
    @Transactional(readOnly = true)
    public List<DppAccessTokenResponse> listAccessTokens(UUID id, String userEmail) {
        DppForm form = ownershipGuard.loadOwned(id, userEmail);
        // Un brouillon n'a pas de code public : les QR n'auraient aucune cible.
        if (form.getPublicCode() == null) {
            throw new ConflictException("Publiez le passeport pour obtenir ses QR codes.");
        }

        return Arrays.stream(DppAccessLevel.values())
                .map(level -> new DppAccessTokenResponse(
                        level, accessTokenService.tokenFor(form.getPublicCode(), level)))
                .toList();
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

    private DppForm loadByPublicCode(String publicCode) {
        return dppFormRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
    }

    // Les collections sont LAZY : on les charge tant que la session est ouverte, avant le mapping.
    private static void initializeChildren(DppForm form) {
        Hibernate.initialize(form.getMaterials());
        Hibernate.initialize(form.getCareInstructions());
        Hibernate.initialize(form.getDocuments());
    }

    private boolean isServicingRepairer(DppForm form, User user) {
        return repairRequestRepository.existsByDppFormAndRepairerProfileUserAndStatusIn(
                form, user, REPAIRER_VIEW_STATUSES);
    }

    // Un lien vers la vitrine n'est exposé que si l'artisan l'a publiée et a été vérifié.
    private String publicArtisanSlug(DppForm form) {
        return artisanProfileRepository.findByUser(form.getUser())
                .filter(ArtisanProfile::isPublished)
                .filter(profile -> profile.getStatus() == ArtisanStatus.VERIFIED)
                .map(ArtisanProfile::getSlug)
                .orElse(null);
    }

    private IrisScoreResponse publishedScore(DppForm form) {
        return irisScoreRepository.findByDppFormId(form.getId())
                .map(DppQueryService::toScoreResponse)
                .orElse(null);
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
}
