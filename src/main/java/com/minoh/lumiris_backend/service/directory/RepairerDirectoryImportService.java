package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerSource;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.service.GeocodingService;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Injecte des fiches d'annuaire dans {@code repairer_profiles}. Upsert par
 * {@code (source, external_ref)} : une fiche déjà réclamée (rattachée à un compte) n'est jamais
 * écrasée ; une fiche encore libre est rafraîchie. Les nouvelles arrivent en {@code UNCLAIMED},
 * masquées de la recherche publique tant qu'un admin ne les a pas promues. Un établissement
 * fermé (etat_administratif) est ignoré à l'import et repasse en {@code SUSPENDED} s'il était
 * déjà listé.
 */
@Slf4j
@Service
public class RepairerDirectoryImportService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final RepairerProfileRepository repairerRepo;
    private final GeocodingService geocodingService;
    private final Map<RepairerSource, RepairerDirectorySource> sources = new EnumMap<>(RepairerSource.class);

    public RepairerDirectoryImportService(RepairerProfileRepository repairerRepo,
                                          GeocodingService geocodingService,
                                          List<RepairerDirectorySource> directorySources) {
        this.repairerRepo = repairerRepo;
        this.geocodingService = geocodingService;
        directorySources.forEach(s -> sources.put(s.source(), s));
    }

    @Transactional
    public ImportReport importFrom(RepairerSource source, ImportCriteria criteria) {
        RepairerDirectorySource directory = sources.get(source);
        if (directory == null) {
            throw new IllegalArgumentException("Aucune source d'annuaire pour " + source);
        }

        List<DirectoryEntry> entries = directory.fetch(criteria);
        int created = 0, updated = 0, skipped = 0;

        for (DirectoryEntry entry : entries) {
            RepairerProfile existing = repairerRepo
                    .findBySourceAndExternalRef(source, entry.externalRef())
                    .orElse(null);

            if (existing != null && existing.getUser() != null) {
                skipped++; // fiche déjà réclamée par un vrai compte — on n'y touche pas
                continue;
            }

            if (!entry.active()) {
                if (existing != null && existing.getStatus() != RepairerStatus.SUSPENDED) {
                    existing.setStatus(RepairerStatus.SUSPENDED);
                    repairerRepo.save(existing);
                    updated++;
                } else {
                    skipped++;
                }
                continue;
            }

            if (existing == null) {
                repairerRepo.save(newProfile(source, entry));
                created++;
            } else {
                apply(existing, entry);
                repairerRepo.save(existing);
                updated++;
            }
        }

        ImportReport report = new ImportReport(source, entries.size(), created, updated, skipped);
        log.info("Import {} : {}", source, report);
        return report;
    }

    private RepairerProfile newProfile(RepairerSource source, DirectoryEntry entry) {
        RepairerProfile profile = new RepairerProfile();
        profile.setSource(source);
        profile.setExternalRef(entry.externalRef());
        profile.setStatus(RepairerStatus.UNCLAIMED);
        profile.setImportedAt(Instant.now());
        apply(profile, entry);
        return profile;
    }

    private void apply(RepairerProfile profile, DirectoryEntry entry) {
        profile.setDisplayName(entry.displayName());
        profile.setCompanyName(entry.companyName());
        profile.setSiret(entry.siret());
        profile.setAddress(entry.address());
        profile.setCity(entry.city());
        profile.setRegion(entry.region());

        Double lat = entry.latitude();
        Double lng = entry.longitude();
        if ((lat == null || lng == null) && profile.getLocation() == null) {
            // Secours : la source n'a pas géolocalisé — on demande à Nominatim (throttlé côté
            // GeocodingService). Sans coords la fiche reste invisible en recherche.
            var geo = geocodingService.geocode(String.join(", ",
                    nonBlank(entry.address()), nonBlank(entry.city()), nonBlank(entry.region())));
            if (geo.isPresent() && geo.get().latitude() != null && geo.get().longitude() != null) {
                lat = geo.get().latitude();
                lng = geo.get().longitude();
            }
        }
        if (lat != null && lng != null) {
            profile.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat)));
        }
    }

    private static String nonBlank(String s) {
        return s == null ? "" : s;
    }
}
