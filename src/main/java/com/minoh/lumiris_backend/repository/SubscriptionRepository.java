package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    Optional<UserSubscription> findByUserId(UUID userId);

    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<UserSubscription> findByStripeCustomerId(String stripeCustomerId);

    // SELECT ... FOR UPDATE: serialises concurrent passport creations for one user (quota race guard).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSubscription s where s.user.id = :userId")
    Optional<UserSubscription> findByUserIdForUpdate(@Param("userId") UUID userId);
}
