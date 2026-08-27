package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardInfoResponse(
        String artisanName,
        String artisanPhotoUrl,
        boolean profileComplete,
        long published,
        long inCompletion,
        long drafts,
        double averageIrisScore,
        Map<String, Long> gradeDistribution,
        List<RecentPassport> recentPassports,
        long quotaUsed,
        Integer quotaLimit,
        // Placeholders : ni les certificats ni les factures fournisseurs ne sont implémenté à date de ce commit
        long expiringCertificates,
        long supplierInvoices
) {
    public record RecentPassport(
            UUID id,
            String productName,
            String status,
            String grade,
            double score,
            Instant updatedAt
    ) {}
}
