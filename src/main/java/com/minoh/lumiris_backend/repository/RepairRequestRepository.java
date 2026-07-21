package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {
    List<RepairRequest> findByRepairerProfileOrderByCreatedAtDesc(RepairerProfile repairerProfile);
    List<RepairRequest> findByConsumerUserOrderByCreatedAtDesc(User consumerUser);

    // Gate for DppEventService: a repairer may only log history on a DPP they're actually
    // servicing (accepted the job at least once), not any DPP with a pending/refused request.
    boolean existsByDppFormAndRepairerProfileUserAndStatusIn(
            DppForm dppForm, User repairerUser, Collection<RepairRequestStatus> statuses);
}
