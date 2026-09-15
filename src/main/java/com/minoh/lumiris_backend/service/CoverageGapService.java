package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.CoverageGapResponse;
import com.minoh.lumiris_backend.entity.RepairerCoverageGap;
import com.minoh.lumiris_backend.repository.RepairerCoverageGapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Journalise les recherches de retoucheur sans résultat (demande non satisfaite) et les restitue
 * agrégées pour l'admin, afin de cibler la prospection là où elle manque.
 */
@Service
@RequiredArgsConstructor
public class CoverageGapService {

    private final RepairerCoverageGapRepository gapRepo;

    // Écrit hors de la transaction (lecture seule) de la recherche.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMiss(double lat, double lng, String specialty) {
        gapRepo.save(new RepairerCoverageGap(lat, lng, specialty));
    }

    @Transactional(readOnly = true)
    public List<CoverageGapResponse> hotspots(int days) {
        Instant since = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        return gapRepo.aggregateSince(since).stream()
                .map(row -> new CoverageGapResponse(
                        ((Number) row[0]).doubleValue(),
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).longValue(),
                        ((java.sql.Timestamp) row[3]).toInstant()))
                .toList();
    }
}
