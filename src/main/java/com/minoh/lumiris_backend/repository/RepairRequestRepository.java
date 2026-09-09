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

    // Gate for DppEventService: a repairer may only log history on a DPP they're actually
    // servicing (accepted the job at least once), not any DPP with a pending/refused request.
    boolean existsByDppFormAndRepairerProfileUserAndStatusIn(
            DppForm dppForm, User repairerUser, Collection<RepairRequestStatus> statuses);
}
