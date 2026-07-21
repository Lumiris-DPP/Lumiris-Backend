package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.SellerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SellerAccountRepository extends JpaRepository<SellerAccount, UUID> {

    Optional<SellerAccount> findByUser_Id(UUID userId);

    Optional<SellerAccount> findByStripeAccountId(String stripeAccountId);

    // Vendeurs encaissables (Stripe Connect actif) parmi un ensemble d'utilisateurs — sert à ne
    // rendre en recherche publique que les produits d'ateliers réellement payables (LUMIRIS :
    // "publier mais masquer aux acheteurs tant que le vendeur n'est pas encaissable").
    @Query("select sa.user.id from SellerAccount sa where sa.chargesEnabled = true and sa.user.id in :ids")
    Set<UUID> payableUserIds(@Param("ids") Collection<UUID> ids);
}
