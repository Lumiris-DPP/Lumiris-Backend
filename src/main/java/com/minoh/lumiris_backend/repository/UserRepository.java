package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByStripeCustomerId(String stripeCustomerId);

    // Comptes en suppression douce dont la fenêtre de rétention est écoulée — à anonymiser.
    List<User> findByDeletedAtBeforeAndAnonymizedAtIsNull(Instant cutoff);

    default User getByEmail(String email) {
        return findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
