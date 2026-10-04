package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.SellerAccount;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerAccountRepository extends JpaRepository<SellerAccount, UUID> {

    Optional<SellerAccount> findByUser_Id(UUID userId);

    Optional<SellerAccount> findByStripeAccountId(String stripeAccountId);

    @Query("select sa.user.id from SellerAccount sa where sa.chargesEnabled = true and sa.user.id in :ids")
    Set<UUID> payableUserIds(@Param("ids") Collection<UUID> ids);
}
