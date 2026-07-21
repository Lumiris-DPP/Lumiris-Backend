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
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairerOnboardingService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double DEFAULT_RADIUS_KM = 20.0;

    private final RepairerProfileRepository repairerRepo;
    private final RepairerReviewRepository reviewRepo;
    private final UserRepository userRepo;
    private final SireneService sireneService;
    private final GeocodingService geocodingService;
    private final KybMapper kybMapper;
    private final StorageService storageService;
    private final MailService mailService;

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
    public RepairerProfileResponse uploadKybDocument(String userEmail, KybDocumentLabel label, MultipartFile file) {
        User user = findUser(userEmail);
        RepairerProfile profile = repairerRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable"));

        FileUploadResponse uploaded = storageService.upload(file, userEmail);
        switch (label) {
            case legal_representative_id_doc -> profile.getKyb().setIdDocFileId(uploaded.id());
            case kbis -> profile.getKyb().setKbisFileId(uploaded.id());
            case proof_of_address -> profile.getKyb().setProofOfAddressFileId(uploaded.id());
            case rib -> profile.getKyb().setRibFileId(uploaded.id());
        }

        return toResponse(repairerRepo.save(profile));
    }

    // Admin actions

    public List<RepairerProfileResponse> findPending() {
        return repairerRepo.findByStatus(RepairerStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RepairerProfileResponse verify(UUID profileId) {
        RepairerProfile profile = repairerRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable : " + profileId));
        profile.setStatus(RepairerStatus.VERIFIED);
        RepairerProfileResponse response = toResponse(repairerRepo.save(profile));
        if (profile.getUser() != null) {
            mailService.sendVerified(profile.getUser().getEmail(), profile.getUser().getName());
        }
        return response;
    }

    @Transactional
    public RepairerProfileResponse reject(UUID profileId, RejectionRequest request) {
        RepairerProfile profile = repairerRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable : " + profileId));
        profile.setStatus(RepairerStatus.REJECTED);
        RepairerProfileResponse response = toResponse(repairerRepo.save(profile));
        if (profile.getUser() != null) {
            mailService.sendRejected(profile.getUser().getEmail(), profile.getUser().getName(), request.reason());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public RepairerPublicProfileResponse findPublicById(UUID id) {
        RepairerProfile p = repairerRepo.findById(id)
                .filter(profile -> profile.getStatus() == RepairerStatus.VERIFIED)
                .orElseThrow(() -> new ResourceNotFoundException("Retoucheur introuvable"));
        return new RepairerPublicProfileResponse(
                p.getId(), p.getDisplayName(), p.getCompanyName(), p.getSpecialties(), p.getZones(),
                p.getSchedule(), p.getAddress(), p.getCity(), p.getRegion(),
                reviewRepo.averageRating(p), reviewRepo.countByRepairerProfile(p)
        );
    }

    @Transactional(readOnly = true)
    public List<RepairerSearchResult> search(double lat, double lng, String specialty, Double radiusKm) {
        double radiusMeters = (radiusKm != null ? radiusKm : DEFAULT_RADIUS_KM) * 1000;
        return repairerRepo.searchNearby(lat, lng, specialty, radiusMeters).stream()
                .map(this::toSearchResult)
                .toList();
    }

    private RepairerSearchResult toSearchResult(Object[] row) {
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
                ((Number) row[9]).doubleValue() / 1000.0
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
