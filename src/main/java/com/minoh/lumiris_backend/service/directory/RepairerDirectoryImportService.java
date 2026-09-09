package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerSource;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
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
 * masquées de la recherche publique tant qu'un admin ne les a pas promues.
 */
@Slf4j
@Service
public class RepairerDirectoryImportService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final RepairerProfileRepository repairerRepo;
    private final Map<RepairerSource, RepairerDirectorySource> sources = new EnumMap<>(RepairerSource.class);

    public RepairerDirectoryImportService(RepairerProfileRepository repairerRepo,
                                          List<RepairerDirectorySource> directorySources) {
        this.repairerRepo = repairerRepo;
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

            if (existing == null) {
                repairerRepo.save(newProfile(source, entry));
                created++;
            } else if (existing.getUser() == null) {
                apply(existing, entry);
                repairerRepo.save(existing);
                updated++;
            } else {
                skipped++; // fiche déjà réclamée par un vrai compte — on n'y touche pas
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
        if (entry.latitude() != null && entry.longitude() != null) {
            profile.setLocation(GEOMETRY_FACTORY.createPoint(
                    new Coordinate(entry.longitude(), entry.latitude())));
        }
    }
}
