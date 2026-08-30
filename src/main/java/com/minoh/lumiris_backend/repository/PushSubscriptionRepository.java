package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUser_Id(UUID userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByUser_IdAndEndpoint(UUID userId, String endpoint);
}
