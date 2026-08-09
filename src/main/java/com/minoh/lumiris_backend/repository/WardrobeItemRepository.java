package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.WardrobeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

public interface WardrobeItemRepository extends JpaRepository<WardrobeItem, UUID> {

    List<WardrobeItem> findByUser_IdOrderByAcquiredAtDesc(UUID userId);

    // Idempotence du fulfillment webhook : une commande n'ajoute qu'une fois la pièce.
    boolean existsByOrder_Id(UUID orderId);

    // KPI vendeur : nombre de pièces de cet atelier entrées dans la Garde-Robe d'acheteurs.
    long countByOrder_Seller_Id(UUID sellerId);

    // Commande annulée ou intégralement remboursée : la pièce n'appartient plus à l'acheteur, elle
    // doit quitter sa Garde-Robe (sinon il conserve le passeport d'un vêtement qu'il a rendu).
    @Modifying
    int deleteByOrder_Id(UUID orderId);
}
