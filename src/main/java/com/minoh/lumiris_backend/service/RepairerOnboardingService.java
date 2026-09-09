package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.KybDetailsRequest;
import com.minoh.lumiris_backend.dto.in.RepairerProfileUpdateRequest;
import com.minoh.lumiris_backend.dto.in.RepairerRegisterRequest;
import com.minoh.lumiris_backend.dto.in.RejectionRequest;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.dto.out.RepairerProfileResponse;
import com.minoh.lumiris_backend.dto.out.RepairerPublicProfileResponse;
import com.minoh.lumiris_backend.dto.out.RepairerSearchResult;
import com.minoh.lumiris_backend.entity.KybDocumentLabel;
import com.minoh.lumiris_backend.entity.KybStatus;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.RepairerReviewRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairerOnboardingService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double DEFAULT_RADIUS_KM = 20.0;

    private final RepairerProfileRepository repairerRepo;
    private final RepairerReviewRepository reviewRepo;
    private final RepairRequestRepository requestRepo;
    private final UserRepository userRepo;
    private final SireneService sireneService;
    private final GeocodingService geocodingService;
    private final KybMapper kybMapper;
    private final StorageService storageService;
    private final MailService mailService;
    private final OcrService ocrService;
    private final CoverageGapService coverageGapService;

    @Transactional(readOnly = true)
    public RepairerProfileResponse findByUserEmail(String userEmail) {
        User user = findUser(userEmail);
        return repairerRepo.findByUser(user)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable"));
    }

    // Inscription rapide (SIRET seul) : crée le profil et snapshot les données SIRENE pour
    // comparaison admin. Le dossier KYB complet (PUT /me/kyb) + une revue admin restent requis
    // avant le passage à VERIFIED.
    @Transactional
    public RepairerProfileResponse register(String userEmail, RepairerRegisterRequest request) {
        User user = findUser(userEmail);
        SireneService.SireneData sirene = sireneService.validate(request.siret());

        RepairerProfile profile = repairerRepo.findByUser(user).orElseGet(() -> {
            RepairerProfile p = new RepairerProfile();
            p.setUser(user);
            return p;
        });

        profile.setSiret(request.siret());
        profile.setCompanyName(sirene.companyName());
        profile.setStatus(RepairerStatus.PENDING);
        if (profile.getDisplayName() == null || profile.getDisplayName().isBlank()) {
            profile.setDisplayName(sirene.companyName());
        }
        profile.getKyb().setSireneSiren(sirene.siren());
        profile.getKyb().setSireneSiegeAddress(sirene.siegeAddress());
        profile.getKyb().setSireneNatureJuridique(sirene.natureJuridique());
        profile.getKyb().setSireneDirigeantsJson(sirene.dirigeantsJson());

        return toResponse(repairerRepo.save(profile));
    }

    @Transactional
    public RepairerProfileResponse updateProfile(String userEmail, RepairerProfileUpdateRequest request) {
        User user = findUser(userEmail);
        RepairerProfile profile = repairerRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable"));

        profile.setDisplayName(request.displayName());
        profile.setSpecialties(request.specialties());
        profile.setZones(request.zones());
        profile.setSchedule(request.schedule());
        profile.setAddress(request.address());
        profile.setCity(request.city());
        profile.setRegion(request.region());

        String query = String.join(", ", nonBlank(request.address()), nonBlank(request.city()), nonBlank(request.region()));
        geocodingService.geocode(query).ifPresent(coords -> {
            if (coords.latitude() != null && coords.longitude() != null) {
                profile.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(coords.longitude(), coords.latitude())));
            }
        });

        return toResponse(repairerRepo.save(profile));
    }

    @Transactional
    public RepairerProfileResponse submitKyb(String userEmail, KybDetailsRequest request) {
        User user = findUser(userEmail);
        RepairerProfile profile = repairerRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable"));

        kybMapper.applyRequest(profile.getKyb(), request);

        return toResponse(repairerRepo.save(profile));
    }

    @Transactional
    public RepairerProfileResponse uploadKybDocument(
            String userEmail, KybDocumentLabel label, MultipartFile file, LocalDate expiresAt
    ) {
        User user = findUser(userEmail);
        RepairerProfile profile = repairerRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable"));

        FileUploadResponse uploaded = storageService.upload(file, userEmail);
        switch (label) {
            case legal_representative_id_doc -> {
                profile.getKyb().setIdDocFileId(uploaded.id());
                profile.getKyb().setIdDocExpiresAt(expiresAt);
                ocrService.extractText(file).ifPresent(profile.getKyb()::setIdDocOcrText);
            }
            case kbis -> {
                profile.getKyb().setKbisFileId(uploaded.id());
                profile.getKyb().setKbisExpiresAt(expiresAt);
            }
            case proof_of_address -> {
                profile.getKyb().setProofOfAddressFileId(uploaded.id());
                profile.getKyb().setProofOfAddressExpiresAt(expiresAt);
            }
            case rib -> {
                profile.getKyb().setRibFileId(uploaded.id());
                profile.getKyb().setRibExpiresAt(expiresAt);
            }
        }

        return toResponse(repairerRepo.save(profile));
    }

    // Admin actions

    public List<RepairerProfileResponse> findPending() {
        return repairerRepo.findByStatus(RepairerStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    // Every registered repairer account (any status), for the admin's general account browser —
    // as opposed to findPending() which only surfaces dossiers awaiting review.
    public List<RepairerProfileResponse> findAll() {
        return repairerRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RepairerProfileResponse verify(UUID profileId) {
        return updateKybStatus(profileId, KybStatus.VALIDATED, null);
    }

    @Transactional
    public RepairerProfileResponse reject(UUID profileId, RejectionRequest request) {
        return updateKybStatus(profileId, KybStatus.REJECTED, request.reason());
    }

    // Marks a dossier as under active review — no account-status or email side effect.
    @Transactional
    public RepairerProfileResponse markKybOngoing(UUID profileId) {
        RepairerProfile profile = findProfile(profileId);
        profile.getKyb().setKybStatus(KybStatus.ONGOING);
        return toResponse(repairerRepo.save(profile));
    }

    // Sends the dossier back to the repairer with a note on what's missing/wrong, without a hard
    // rejection — the account stays PENDING so they can fix and resubmit.
    @Transactional
    public RepairerProfileResponse markKybIncomplete(UUID profileId, String note) {
        RepairerProfile profile = findProfile(profileId);
        profile.getKyb().setKybStatus(KybStatus.INCOMPLETE);
        profile.getKyb().setKybReviewNote(note);
        RepairerProfileResponse response = toResponse(repairerRepo.save(profile));
        if (profile.getUser() != null) {
            mailService.sendKybIncomplete(profile.getUser().getEmail(), profile.getUser().getName(), note);
        }
        return response;
    }

    private RepairerProfileResponse updateKybStatus(UUID profileId, KybStatus status, String note) {
        RepairerProfile profile = findProfile(profileId);
        profile.getKyb().setKybStatus(status);
        profile.getKyb().setKybReviewNote(note);
        if (status == KybStatus.VALIDATED) {
            profile.setStatus(RepairerStatus.VERIFIED);
        } else if (status == KybStatus.REJECTED) {
            profile.setStatus(RepairerStatus.REJECTED);
        }
        RepairerProfileResponse response = toResponse(repairerRepo.save(profile));
        if (profile.getUser() != null) {
            if (status == KybStatus.VALIDATED) {
                mailService.sendVerified(profile.getUser().getEmail(), profile.getUser().getName());
            } else if (status == KybStatus.REJECTED) {
                mailService.sendRejected(profile.getUser().getEmail(), profile.getUser().getName(), note);
            }
        }
        return response;
    }

    private RepairerProfile findProfile(UUID id) {
        return repairerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public RepairerPublicProfileResponse findPublicById(UUID id) {
        RepairerProfile p = repairerRepo.findById(id)
                .filter(profile -> profile.getStatus() == RepairerStatus.VERIFIED)
                .orElseThrow(() -> new ResourceNotFoundException("Retoucheur introuvable"));
        Double medianSeconds = requestRepo.medianResponseSeconds(p.getId());
        Double medianHours = medianSeconds != null ? Math.round(medianSeconds / 360.0) / 10.0 : null;
        long completedJobs = requestRepo.countByRepairerProfileAndStatusAndQuoteSubmittedAtIsNotNull(
                p, RepairRequestStatus.COMPLETED);

        long accepted = requestRepo.countAcceptedQuotes(p.getId());
        long refused = requestRepo.countRefusedQuotes(p.getId());
        Double acceptanceRate = (accepted + refused) > 0
                ? Math.round((double) accepted / (accepted + refused) * 100) / 100.0
                : null;

        return new RepairerPublicProfileResponse(
                p.getId(), p.getDisplayName(), p.getCompanyName(), p.getSpecialties(), p.getZones(),
                p.getSchedule(), p.getAddress(), p.getCity(), p.getRegion(),
                reviewRepo.averageRating(p), reviewRepo.countByRepairerProfile(p),
                medianHours, acceptanceRate, completedJobs
        );
    }

    @Transactional(readOnly = true)
    public List<RepairerSearchResult> search(double lat, double lng, String specialty, Double radiusKm,
                                             String sort, Integer page, Integer size) {
        double radiusMeters = (radiusKm != null ? radiusKm : DEFAULT_RADIUS_KM) * 1000;
        String effectiveSort = switch (sort == null ? "" : sort) {
            case "rating", "responsiveness" -> sort;
            default -> "distance";
        };
        int pageSize = size != null && size > 0 && size <= 100 ? size : 20;
        int offset = page != null && page > 0 ? page * pageSize : 0;

        List<RepairerSearchResult> results = repairerRepo
                .searchNearby(lat, lng, specialty, radiusMeters, effectiveSort, pageSize, offset).stream()
                .map(this::toSearchResult)
                .toList();
        // Une première page vide = demande non satisfaite ; les pages suivantes vides, non.
        if (results.isEmpty() && offset == 0) {
            coverageGapService.recordMiss(lat, lng, specialty);
        }
        return results;
    }

    private RepairerSearchResult toSearchResult(Object[] row) {
        long reviewCount = row[13] != null ? ((Number) row[13]).longValue() : 0L;
        return new RepairerSearchResult(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                toStringList(row[3]),
                toStringList(row[4]),
                (String) row[5],
                (String) row[6],
                (String) row[7],
                (String) row[8],
                ((Number) row[9]).doubleValue() / 1000.0,
                ((Number) row[10]).doubleValue(),
                ((Number) row[11]).doubleValue(),
                reviewCount > 0 ? ((Number) row[12]).doubleValue() : null,
                reviewCount,
                row[14] != null ? Math.round(((Number) row[14]).doubleValue() / 360.0) / 10.0 : null
        );
    }

    // Postgres text[] columns come back as String[] (JDBC array), not List, from a native query.
    private List<String> toStringList(Object column) {
        if (column instanceof String[] array) {
            return List.of(array);
        }
        return List.of();
    }

    private String nonBlank(String s) {
        return s == null ? "" : s;
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    RepairerProfileResponse toResponse(RepairerProfile p) {
        return new RepairerProfileResponse(
                p.getId(),
                p.getUser() != null ? p.getUser().getEmail() : null,
                p.getStatus(),
                p.getSource(),
                p.getImportedAt(),
                p.getClaimedAt(),
                p.getSiret(),
                p.getCompanyName(),
                p.getDisplayName(),
                p.getSpecialties(),
                p.getZones(),
                p.getSchedule(),
                p.getAddress(),
                p.getCity(),
                p.getRegion(),
                reviewRepo.averageRating(p),
                reviewRepo.countByRepairerProfile(p),
                p.getCreatedAt(),
                kybMapper.toResponse(p.getKyb())
        );
    }
}
