package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.WardrobeItem;
import com.minoh.lumiris_backend.entity.WardrobeItemOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WardrobeItemRepository extends JpaRepository<WardrobeItem, UUID> {

    // Garde-Robe de l'acheteur : le passeport et ses symboles d'entretien font partie de la vue
    // (ce sont eux qui portent les rappels), donc une seule requête plutôt qu'un N+1 paresseux.
    @Query("""
            select distinct w from WardrobeItem w
            left join fetch w.dppForm f
            left join fetch f.careInstructions
            where w.user.id = :userId
            order by w.acquiredAt desc
            """)
    List<WardrobeItem> findOwnedWithPassport(@Param("userId") UUID userId);

    List<WardrobeItem> findByUser_IdAndOriginAndClientKeyIn(UUID userId,
                                                            WardrobeItemOrigin origin,
                                                            List<String> clientKeys);

    @Modifying
    @Query("""
            delete from WardrobeItem w
            where w.user.id = :userId
              and w.origin = :origin
              and w.clientKey in :clientKeys
            """)
    int deleteByUserAndOriginAndClientKeys(@Param("userId") UUID userId,
                                           @Param("origin") WardrobeItemOrigin origin,
                                           @Param("clientKeys") List<String> clientKeys);

    // Idempotence du fulfillment webhook : une commande n'ajoute qu'une fois la pièce.
    boolean existsByOrder_Id(UUID orderId);

    // KPI vendeur : nombre de pièces de cet atelier entrées dans la Garde-Robe d'acheteurs.
    long countByOrder_Seller_Id(UUID sellerId);

    // Commande annulée ou intégralement remboursée : la pièce n'appartient plus à l'acheteur, elle
    // doit quitter sa Garde-Robe (sinon il conserve le passeport d'un vêtement qu'il a rendu).
    @Modifying
    int deleteByOrder_Id(UUID orderId);

    // ── Garde-Robe active (balayage quotidien) ──────────────────────────────

    // Pièces à qui l'on n'a encore rien dit CETTE saison. Le destinataire, le passeport et ses
    // symboles d'entretien sont ramenés dans la MÊME requête : le balayage n'a pas de session
    // ouverte (l'OSIV ne couvre que les requêtes web), donc tout ce que l'envoi lira doit être
    // initialisé ici — sans quoi le job casse sur un accès paresseux.
    //
    // `acquiredBefore` laisse passer la période de retour : rappeler l'entretien d'une pièce reçue
    // il y a trois jours, alors que l'acheteur hésite encore à la garder, sonne faux.
    @Query("""
            select distinct w from WardrobeItem w
            join fetch w.user
            join fetch w.dppForm f
            left join fetch f.careInstructions
            where w.acquiredAt < :acquiredBefore
              and (w.careReminderSeason is null or w.careReminderSeason <> :seasonKey)
            order by w.acquiredAt asc
            """)
    List<WardrobeItem> findCareReminderCandidates(@Param("seasonKey") String seasonKey,
                                                  @Param("acquiredBefore") Instant acquiredBefore);

    // Garanties qui s'achèvent bientôt et jamais annoncées. C'est la dernière fenêtre où l'acheteur
    // peut encore faire valoir un droit : ne rien dire reviendrait à le laisser l'oublier.
    @Query("""
            select w from WardrobeItem w
            join fetch w.user
            left join fetch w.dppForm
            where w.warrantyUntil is not null
              and w.warrantyAlertSentAt is null
              and w.warrantyUntil between :now and :horizon
            order by w.warrantyUntil asc
            """)
    List<WardrobeItem> findExpiringWarranties(@Param("now") Instant now, @Param("horizon") Instant horizon);

    // Revendication conditionnelle : le job tourne sur chaque instance, un double tir serait un
    // e-mail en double. On ne notifie que si l'update a effectivement pris la ligne.
    @Modifying
    @Query("""
            update WardrobeItem w set w.careReminderSeason = :seasonKey, w.careReminderSentAt = :now
            where w.id = :id and (w.careReminderSeason is null or w.careReminderSeason <> :seasonKey)
            """)
    int claimCareReminder(@Param("id") UUID id, @Param("seasonKey") String seasonKey, @Param("now") Instant now);

    @Modifying
    @Query("""
            update WardrobeItem w set w.warrantyAlertSentAt = :now
            where w.id = :id and w.warrantyAlertSentAt is null
            """)
    int claimWarrantyAlert(@Param("id") UUID id, @Param("now") Instant now);
}
