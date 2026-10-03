package com.minoh.lumiris_backend.marketplace.order.repository;

import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, UUID> {

    Optional<MarketplaceOrder> findByStripeCheckoutSessionId(String sessionId);

    // Une annonce a-t-elle des commandes ? (garde-fou : on archive au lieu de supprimer pour ne pas
    // orpheliner l'historique d'achat / la garde-robe des acheteurs).
    boolean existsByProduct_Id(UUID productId);

    // Fulfillment du paiement embarqué : toutes les lignes de commande d'un même PaymentIntent.
    List<MarketplaceOrder> findByStripePaymentIntentId(String paymentIntentId);

    // Même lecture, lignes verrouillées jusqu'à la fin de la transaction : deux livraisons simultanées
    // du webhook, ou un webhook et le balayage des paiements abandonnés, ne peuvent pas lire toutes
    // deux « en attente ». Ordre fixe pour que deux verrouillages concurrents ne s'interbloquent pas.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketplaceOrder o where o.stripePaymentIntentId = :paymentIntentId order by o.id")
    List<MarketplaceOrder> lockByStripePaymentIntentId(@Param("paymentIntentId") String paymentIntentId);

    // Sérialise jusqu'à la fin de la transaction celles qui préparent le même PaymentIntent (double
    // clic) : la seconde attend que la première ait validé, puis voit ses commandes au lieu de
    // réserver une seconde fois. Verrou consultatif : aucune ligne n'existe encore à verrouiller.
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:paymentIntentId))) as verrou",
            nativeQuery = true)
    int lockPaymentIntent(@Param("paymentIntentId") String paymentIntentId);

    // Quantités déjà réservées par l'acheteur (commandes en attente de paiement), par déclinaison.
    @Query("""
            select o.variant.id, sum(o.quantity) from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PENDING
              and o.buyer.id = :buyerId
              and o.variant.id in :variantIds
            group by o.variant.id
            """)
    List<Object[]> pendingQuantityByVariant(@Param("buyerId") UUID buyerId,
                                            @Param("variantIds") Collection<UUID> variantIds);

    // Rapprochement d'un webhook transporteur : l'agrégateur ne connaît que SON identifiant de colis.
    Optional<MarketplaceOrder> findByCarrierParcelId(String carrierParcelId);

    // Les deux historiques rendent le produit et le contrepartie de chaque ligne : sans graphe de
    // chargement, chaque commande de la page déclenche sa propre lecture de `marketplace_products`
    // puis de `users`.
    @EntityGraph(attributePaths = {"product", "seller", "seller.artisanProfile"})
    List<MarketplaceOrder> findByBuyer_IdOrderByCreatedAtDesc(UUID buyerId);

    @EntityGraph(attributePaths = {"product", "buyer", "buyer.artisanProfile", "shippingLabelFile"})
    List<MarketplaceOrder> findBySeller_IdOrderByCreatedAtDesc(UUID sellerId);

    // File d'arbitrage de la plateforme : le plus ancien litige d'abord (il attend depuis le plus
    // longtemps, et c'est celui qui coûte le plus cher en réputation).
    @EntityGraph(attributePaths = {"product", "buyer", "buyer.artisanProfile", "shippingLabelFile"})
    List<MarketplaceOrder> findByDisputeStatusOrderByDisputeOpenedAtAsc(DisputeStatus disputeStatus);

    // ── Statistiques vendeur (ventes réglées) ───────────────────────────────
    long countBySeller_IdAndStatusIn(UUID sellerId, Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.amountTotalCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses")
    long grossCentsBySeller(@Param("sellerId") UUID sellerId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.commissionCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses")
    long commissionCentsBySeller(@Param("sellerId") UUID sellerId, @Param("statuses") Collection<OrderStatus> statuses);

    // Ventes réglées par produit (pour la colonne "Ventes" du catalogue).
    @Query("select o.product.id, count(o) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses group by o.product.id")
    List<Object[]> salesCountByProduct(@Param("sellerId") UUID sellerId,
                                       @Param("statuses") Collection<OrderStatus> statuses);

    // ── Trésorerie escrow (montants nets, part vendeur) ──────────────────────
    // Encaissé mais retenu : la pièce n'est pas encore réputée livrée.
    @Query("select coalesce(sum(o.netCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses and o.stripeTransferId is null")
    long heldNetCentsBySeller(@Param("sellerId") UUID sellerId,
                              @Param("statuses") Collection<OrderStatus> statuses);

    // Déjà reversé au vendeur (Transfer créé).
    @Query("select coalesce(sum(o.netCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.stripeTransferId is not null")
    long releasedNetCentsBySeller(@Param("sellerId") UUID sellerId);

    // ── Échéances du cycle de vie (job horaire) ──────────────────────────────
    // Expédiée depuis assez longtemps pour être présumée livrée. Un litige ouvert gèle la commande :
    // seule une décision humaine doit alors la faire avancer.
    @Query("""
            select o from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.SHIPPED
              and o.shippedAt < :threshold
              and o.disputeStatus <> com.minoh.lumiris_backend.entity.DisputeStatus.OPEN
            """)
    List<MarketplaceOrder> findStaleShipped(@Param("threshold") Instant threshold);

    // Paniers abandonnés : le stock reste réservé tant que la commande est en attente de paiement.
    // Seuls les identifiants des paiements sont lus : chacun est ensuite tranché ligne verrouillée,
    // sur un état relu, et non sur une copie chargée avant qu'un webhook ne la confirme.
    @Query("""
            select distinct o.stripePaymentIntentId from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PENDING
              and o.stripePaymentIntentId is not null
              and o.createdAt < :threshold
            """)
    List<String> findAbandonedPendingPaymentIntents(@Param("threshold") Instant threshold);

    // Paniers abandonnés de l'ancien parcours Checkout, sans PaymentIntent à relire chez Stripe.
    @Query("""
            select o from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PENDING
              and o.stripePaymentIntentId is null
              and o.createdAt < :threshold
            """)
    List<MarketplaceOrder> findAbandonedPendingWithoutPaymentIntent(@Param("threshold") Instant threshold);

    // Paiements laissés par les tentatives précédentes du même acheteur. Sans leur règlement, chaque
    // carte refusée puis réessayée empile une commande PENDING de plus et retire une unité du
    // catalogue jusqu'au balayage du lendemain.
    @Query("""
            select distinct o.stripePaymentIntentId from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PENDING
              and o.buyer.id = :buyerId
              and o.stripePaymentIntentId <> :currentPaymentIntentId
            """)
    List<String> findSupersededPendingPaymentIntents(@Param("buyerId") UUID buyerId,
                                                     @Param("currentPaymentIntentId") String currentPaymentIntentId);

    // Retour refusé ou réceptionné et laissé sans suite : sans échéance, ces commandes restent
    // éternellement dans l'onglet « Retours » du vendeur et l'acheteur n'a jamais de conclusion.
    @Query("""
            select o from MarketplaceOrder o
            where o.status in (com.minoh.lumiris_backend.entity.OrderStatus.RETURN_REFUSED,
                               com.minoh.lumiris_backend.entity.OrderStatus.RETURN_RECEIVED)
              and o.returnDecidedAt < :threshold
              and o.disputeStatus <> com.minoh.lumiris_backend.entity.DisputeStatus.OPEN
            """)
    List<MarketplaceOrder> findStaleReturns(@Param("threshold") Instant threshold);

    // Commandes payées jamais expédiées : le vendeur doit être relancé avant que l'acheteur
    // n'ouvre un litige de son côté.
    // L'origine est la date d'expédition PROMISE, pas la date d'achat : un atelier qui a annoncé
    // 10 jours de préparation, ou qui est en congés, n'est pas en retard. Une seule relance par
    // commande, et jamais sur une commande gelée par un litige.
    @Query("""
            select o from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PAID
              and o.shipReminderSentAt is null
              and o.disputeStatus <> com.minoh.lumiris_backend.entity.DisputeStatus.OPEN
              and o.shipDueAt < :threshold
            """)
    List<MarketplaceOrder> findOverdueUnshipped(@Param("threshold") Instant threshold);

    // Échéancier de versement : les commandes encaissées dont les fonds sont encore retenus.
    // Même périmètre que heldNetCentsBySeller, mais détaillé ligne à ligne et daté.
    @Query("""
            select o from MarketplaceOrder o
            where o.seller.id = :sellerId
              and o.status in :statuses
              and o.stripeTransferId is null
            """)
    List<MarketplaceOrder> findUnreleasedBySeller(@Param("sellerId") UUID sellerId,
                                                  @Param("statuses") Collection<OrderStatus> statuses);

    // Versements en échec : la commande est livrée ou clôturée mais aucun transfert n'a abouti
    // (compte vendeur non activé au moment du versement, incident Stripe). Sans reprise, l'argent
    // reste indéfiniment chez la plateforme.
    @Query("""
            select o from MarketplaceOrder o
            where o.status in (com.minoh.lumiris_backend.entity.OrderStatus.DELIVERED,
                               com.minoh.lumiris_backend.entity.OrderStatus.COMPLETED)
              and o.stripeTransferId is null
              and o.netCents > 0
            """)
    List<MarketplaceOrder> findUnreleased();

    // Livrée et fenêtre de rétractation écoulée → la commande peut être clôturée.
    @Query("""
            select o from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.DELIVERED
              and o.deliveredAt < :threshold
              and o.disputeStatus <> com.minoh.lumiris_backend.entity.DisputeStatus.OPEN
            """)
    List<MarketplaceOrder> findStaleDelivered(@Param("threshold") Instant threshold);
}
