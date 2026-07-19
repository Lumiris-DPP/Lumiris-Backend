package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.SellerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SellerAccountRepository extends JpaRepository<SellerAccount, UUID> {

    Optional<SellerAccount> findByUser_Id(UUID userId);

    Optional<SellerAccount> findByStripeAccountId(String stripeAccountId);
}
