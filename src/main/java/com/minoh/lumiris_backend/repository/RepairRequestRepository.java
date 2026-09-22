package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {
    List<RepairRequest> findByRepairerProfileOrderByCreatedAtDesc(RepairerProfile repairerProfile);
    List<RepairRequest> findByConsumerUserOrderByCreatedAtDesc(User consumerUser);
    Optional<RepairRequest> findByStripePaymentIntentId(String stripePaymentIntentId);

    // Délai médian de réponse (demande -> devis), en secondes. Null si le retoucheur n'a pas
    // encore chiffré la moindre demande.
    @Query(value = """
            SELECT EXTRACT(EPOCH FROM percentile_cont(0.5) WITHIN GROUP (
                       ORDER BY (quote_submitted_at - created_at)))
            FROM repair_requests
            WHERE repairer_profile_id = :profileId AND quote_submitted_at IS NOT NULL
            """, nativeQuery = true)
    Double medianResponseSeconds(@Param("profileId") UUID profileId);

    long countByRepairerProfileAndStatusAndQuoteSubmittedAtIsNotNull(
            RepairerProfile repairerProfile, RepairRequestStatus status);

    // Devis acceptés (RDV pris ou devis payé, et non refusé).
    @Query(value = """
            SELECT COUNT(*) FROM repair_requests
            WHERE repairer_profile_id = :profileId
              AND quote_submitted_at IS NOT NULL AND quote_refused_at IS NULL
              AND (appointment_at IS NOT NULL OR paid_at IS NOT NULL)
            """, nativeQuery = true)
    long countAcceptedQuotes(@Param("profileId") UUID profileId);

    @Query(value = """
            SELECT COUNT(*) FROM repair_requests
            WHERE repairer_profile_id = :profileId AND quote_refused_at IS NOT NULL
            """, nativeQuery = true)
    long countRefusedQuotes(@Param("profileId") UUID profileId);

    // Gate for DppEventService: a repairer may only log history on a DPP they're actually
    // servicing (accepted the job at least once). COMPLETED alone isn't enough proof of that —
    // it's also the terminal status for a quote the client refused or a request the repairer
    // declined before ever quoting, so those are excluded explicitly via the two "never serviced"
    // timestamps rather than relying on status alone.
    @Query("""
            select case when count(r) > 0 then true else false end
            from RepairRequest r
            where r.dppForm = :dppForm and r.repairerProfile.user = :repairerUser
              and (r.status in :activeStatuses
                   or (r.status = com.minoh.lumiris_backend.entity.RepairRequestStatus.COMPLETED
                       and r.quoteRefusedAt is null and r.repairerDeclinedAt is null))
            """)
    boolean existsServicedByDppFormAndRepairerProfileUser(
            @Param("dppForm") DppForm dppForm, @Param("repairerUser") User repairerUser,
            @Param("activeStatuses") Collection<RepairRequestStatus> activeStatuses);

    // Gate for DppFormService: a repairer may view the passport as soon as a request exists,
    // regardless of status — they need to see the item to quote it and message the client
    // before ever accepting anything.
    boolean existsByDppFormAndRepairerProfileUser(DppForm dppForm, User repairerUser);

    // ── Trésorerie / versements (même mécanique que MarketplaceOrderRepository côté artisan) ──
    @Query("""
            select r from RepairRequest r
            where r.repairerProfile.user.id = :userId
              and r.status in :statuses
              and r.paidAt is not null
              and r.stripeTransferId is null
            """)
    List<RepairRequest> findUnreleasedByRepairerUser(
            @Param("userId") UUID userId, @Param("statuses") Collection<RepairRequestStatus> statuses);

    @Query("select coalesce(sum(r.netCents), 0) from RepairRequest r "
            + "where r.repairerProfile.user.id = :userId and r.stripeTransferId is not null")
    long releasedNetCentsByRepairerUser(@Param("userId") UUID userId);
}
