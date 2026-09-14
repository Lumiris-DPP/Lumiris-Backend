package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanSource;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.service.GeocodingService;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Injecte des fiches d'annuaire dans {@code artisan_profiles} — mêmes règles que
 * {@link RepairerDirectoryImportService} : upsert par {@code (source, external_ref)}, une fiche
 * déjà réclamée n'est jamais écrasée, les nouvelles arrivent en {@code UNCLAIMED} et publiées
 * directement (pattern "Doctolib" : visibles tout de suite, avec un jeu de champs réduit).
 */
@Slf4j
@Service
public class ArtisanDirectoryImportService {

    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ArtisanProfileRepository artisanRepo;
    private final GeocodingService geocodingService;
    private final Map<ArtisanSource, ArtisanDirectorySource> sources = new EnumMap<>(ArtisanSource.class);

    public ArtisanDirectoryImportService(ArtisanProfileRepository artisanRepo,
                                         GeocodingService geocodingService,
                                         List<ArtisanDirectorySource> directorySources) {
        this.artisanRepo = artisanRepo;
        this.geocodingService = geocodingService;
        directorySources.forEach(s -> sources.put(s.source(), s));
    }

    @Transactional
    public ArtisanImportReport importFrom(ArtisanSource source, ImportCriteria criteria) {
        ArtisanDirectorySource directory = sources.get(source);
        if (directory == null) {
            throw new IllegalArgumentException("Aucune source d'annuaire pour " + source);
        }

        List<DirectoryEntry> entries = directory.fetch(criteria);
        int created = 0, updated = 0, skipped = 0;

        for (DirectoryEntry entry : entries) {
            ArtisanProfile existing = artisanRepo
                    .findBySourceAndExternalRef(source, entry.externalRef())
                    .orElse(null);

            if (existing != null && existing.getUser() != null) {
                skipped++; // fiche déjà réclamée par un vrai compte — on n'y touche pas
                continue;
            }

            if (!entry.active()) {
                if (existing != null && existing.getStatus() != ArtisanStatus.REJECTED) {
                    existing.setStatus(ArtisanStatus.REJECTED);
                    existing.setPublished(false);
                    artisanRepo.save(existing);
                    updated++;
                } else {
                    skipped++;
                }
                continue;
            }

            if (existing == null) {
                artisanRepo.save(newProfile(source, entry));
                created++;
            } else {
                apply(existing, entry);
                artisanRepo.save(existing);
                updated++;
            }
        }

        ArtisanImportReport report = new ArtisanImportReport(source, entries.size(), created, updated, skipped);
        log.info("Import {} : {}", source, report);
        return report;
    }

    private ArtisanProfile newProfile(ArtisanSource source, DirectoryEntry entry) {
        ArtisanProfile profile = new ArtisanProfile();
        profile.setSource(source);
        profile.setExternalRef(entry.externalRef());
        profile.setStatus(ArtisanStatus.UNCLAIMED);
        profile.setImportedAt(Instant.now());
        profile.setJoinedAt(Instant.now());
        profile.setPublished(true);
        profile.setSlug(uniqueSlug(entry.displayName(), entry.externalRef()));
        apply(profile, entry);
        return profile;
    }

    private void apply(ArtisanProfile profile, DirectoryEntry entry) {
        profile.setDisplayName(entry.displayName());
        profile.setAtelierName(entry.displayName());
        profile.setCompanyName(entry.companyName());
        profile.setSiret(entry.siret());
        profile.setCity(entry.city());
        profile.setRegion(entry.region());

        Double lat = entry.latitude();
        Double lng = entry.longitude();
        if ((lat == null || lng == null) && profile.getLocation() == null) {
            // Secours : la source n'a pas géolocalisé — on demande à Nominatim (throttlé côté
            // GeocodingService). Sans coords la fiche n'apparaît pas sur la carte.
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

    private String uniqueSlug(String name, String fallback) {
        String base = slugify(name != null && !name.isBlank() ? name : fallback);
        String candidate = base;
        int suffix = 2;
        while (artisanRepo.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        String slug = NON_SLUG_CHARS.matcher(normalized).replaceAll("-").replaceAll("^-|-$", "");
        return slug.isBlank() ? UUID.randomUUID().toString().substring(0, 8) : slug;
    }
}
