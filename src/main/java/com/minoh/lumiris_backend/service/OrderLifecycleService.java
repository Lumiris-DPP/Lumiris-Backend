package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.dto.in.DisputeResolutionRequest;
import com.minoh.lumiris_backend.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.dto.in.RefundRequest;
import com.minoh.lumiris_backend.dto.in.ReturnDecisionRequest;
import com.minoh.lumiris_backend.dto.in.ReturnRequest;
import com.minoh.lumiris_backend.dto.in.ShipOrderRequest;
import com.minoh.lumiris_backend.entity.*;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.MarketplaceProductVariantRepository;
import com.minoh.lumiris_backend.repository.OrderEventRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import com.minoh.lumiris_backend.service.stripe.OrderRefundService;
import com.minoh.lumiris_backend.service.stripe.SellerPayoutService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// LUMIRIS-24 · Machine à états d'une commande marketplace : payée → expédiée → livrée → clôturée,
// avec branche retour et branche litige. Point d'entrée unique de TOUTE transition : chacune
// vérifie l'état de départ, journalise un OrderEvent (append-only) et notifie la contrepartie.
// Aucun appelant ne doit écrire `status` directement.
//
// Escrow : les fonds sont retenus par la plateforme jusqu'à la livraison, puis reversés au
// vendeur. Un remboursement postérieur reprend la part du vendeur (TransferReversal).
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    private final MarketplaceOrderRepository orderRepository;
    private final MarketplaceProductVariantRepository variantRepository;
    private final OrderEventRepository eventRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final NotificationService notificationService;
    private final OrderRefundService refundService;
    private final SellerPayoutService payoutService;
    private final PreparationDelayResolver preparationDelayResolver;
    private final MarketplaceProperties properties;

    // ── Vendeur ─────────────────────────────────────────────────────────────

    @Transactional
    public void ship(String sellerEmail, UUID orderId, ShipOrderRequest request) {
        User seller = userRepository.getByEmail(sellerEmail);
        MarketplaceOrder order = requireSellerOrder(seller, orderId);
        requireShippable(order);
        order.setCarrier(request.carrier());
        order.setTrackingNumber(request.trackingNumber());
        order.setTrackingUrl(request.trackingUrl());
        markShipped(order, seller);
    }

    // Refus commun aux deux chemins d'expédition (saisie manuelle et bordereau généré) : rien ne
    // part deux fois, et rien ne part avant d'être payé.
    public void requireShippable(MarketplaceOrder order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BillingValidationException(
                    "Seule une commande payée et non encore expédiée peut être marquée expédiée.");
        }
    }

    // Bascule effective en expédiée, une fois le transporteur et le suivi renseignés — que
    // l'atelier les ait tapés ou que l'agrégateur les ait remplis. Un seul chemin d'écriture pour
    // que la notification acheteur et l'entrée de timeline soient identiques dans les deux cas.
    @Transactional
    public void markShipped(MarketplaceOrder order, User seller) {
        requireShippable(order);
        order.setShippedAt(Instant.now());
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);

        record(order, OrderEventType.SHIPPED, OrderActorType.SELLER, seller,
                trackingSummary(order));
        notificationService.notify(order.getBuyer(), NotificationType.ORDER_SHIPPED,
                "Ta commande est en route",
                itemLabel(order) + " a été expédiée. " + trackingSummary(order),
                buyerOrderHref(order), order);
    }

    // Le vendeur constate la réception d'un retour accepté ; le remboursement reste une action
    // explicite pour qu'il puisse retenir des frais après contrôle de l'état de la pièce.
    @Transactional
    public void markReturnReceived(String sellerEmail, UUID orderId) {
        User seller = userRepository.getByEmail(sellerEmail);
        MarketplaceOrder order = requireSellerOrder(seller, orderId);
        if (order.getStatus() != OrderStatus.RETURN_APPROVED) {
            throw new BillingValidationException("Aucun retour accepté en attente sur cette commande.");
        }
        order.setReturnReceivedAt(Instant.now());
        order.setStatus(OrderStatus.RETURN_RECEIVED);
        orderRepository.save(order);

        record(order, OrderEventType.RETURN_RECEIVED, OrderActorType.SELLER, seller, null);
        notificationService.notify(order.getBuyer(), NotificationType.RETURN_RECEIVED,
                "Ton retour est arrivé",
                "L'atelier a reçu le retour de " + itemLabel(order) + ". Le remboursement suit.",
                buyerOrderHref(order), order);
    }

    @Transactional
    public void decideReturn(String sellerEmail, UUID orderId, ReturnDecisionRequest request) {
        User seller = userRepository.getByEmail(sellerEmail);
        MarketplaceOrder order = requireSellerOrder(seller, orderId);
        if (order.getStatus() != OrderStatus.RETURN_REQUESTED) {
            throw new BillingValidationException("Aucune demande de retour en attente sur cette commande.");
        }
        order.setReturnDecidedAt(Instant.now());
        order.setReturnDecisionNote(request.note());

        if (request.accepted()) {
            order.setStatus(OrderStatus.RETURN_APPROVED);
            orderRepository.save(order);
            record(order, OrderEventType.RETURN_APPROVED, OrderActorType.SELLER, seller, request.note(),
                    request.attachments());
            notificationService.notify(order.getBuyer(), NotificationType.RETURN_APPROVED,
                    "Ton retour est accepté",
                    "L'atelier accepte le retour de " + itemLabel(order)
                            + ". Renvoie la pièce, le remboursement suivra sa réception.",
                    buyerOrderHref(order), order);
            return;
        }

        order.setStatus(OrderStatus.RETURN_REFUSED);
        orderRepository.save(order);
        record(order, OrderEventType.RETURN_REFUSED, OrderActorType.SELLER, seller, request.note(),
                request.attachments());
        notificationService.notify(order.getBuyer(), NotificationType.RETURN_REFUSED,
                "Ton retour a été refusé",
                "L'atelier refuse le retour de " + itemLabel(order)
                        + (request.note() != null ? " — " + request.note() : "")
                        + ". Tu peux ouvrir un litige si tu contestes cette décision.",
                buyerOrderHref(order), order);
    }

    // Remboursement à l'initiative du vendeur (geste commercial, retour reçu, article manquant).
    @Transactional
    public void refund(String sellerEmail, UUID orderId, RefundRequest request) {
        User seller = userRepository.getByEmail(sellerEmail);
        MarketplaceOrder order = requireSellerOrder(seller, orderId);
        applyRefund(order, request.amountCents(), request.reason(), OrderActorType.SELLER, seller);
    }

    // ── Acheteur ────────────────────────────────────────────────────────────

    // Confirmation de réception : clôt l'attente et libère les fonds au vendeur sans attendre
    // l'échéance automatique. La fenêtre de retour reste ouverte (un remboursement ultérieur
    // reprend la part reversée).
    @Transactional
    public void confirmDelivery(String buyerEmail, UUID orderId) {
        User buyer = userRepository.getByEmail(buyerEmail);
        MarketplaceOrder order = requireBuyerOrder(buyer, orderId);
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BillingValidationException("Cette commande n'est pas en cours de livraison.");
        }
        transitionToDelivered(order, OrderActorType.BUYER, buyer);
    }

    @Transactional
    public void requestReturn(String buyerEmail, UUID orderId, ReturnRequest request) {
        User buyer = userRepository.getByEmail(buyerEmail);
        MarketplaceOrder order = requireBuyerOrder(buyer, orderId);
        if (!order.getStatus().allowsReturnRequest()) {
            throw new BillingValidationException(
                    "Un retour ne peut être demandé qu'entre l'expédition et la clôture de la commande.");
        }
        if (order.getReturnDeadline() != null && Instant.now().isAfter(order.getReturnDeadline())) {
            throw new BillingValidationException("La fenêtre de retour de cette commande est expirée.");
        }
        order.setReturnRequestedAt(Instant.now());
        order.setReturnReason(request.reason());
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        orderRepository.save(order);

        record(order, OrderEventType.RETURN_REQUESTED, OrderActorType.BUYER, buyer, request.reason(),
                request.attachments());
        notificationService.notify(order.getSeller(), NotificationType.RETURN_REQUESTED,
                "Demande de retour à traiter",
                "Un acheteur demande le retour de " + itemLabel(order) + " — " + request.reason(),
                sellerOrderHref(order), order);
    }

    @Transactional
    public void openDispute(String buyerEmail, UUID orderId, OrderMessageRequest request) {
        User buyer = userRepository.getByEmail(buyerEmail);
        MarketplaceOrder order = requireBuyerOrder(buyer, orderId);
        if (order.getStatus() == OrderStatus.PENDING) {
            throw new BillingValidationException("Un litige ne peut être ouvert que sur une commande payée.");
        }
        if (order.getDisputeStatus() == DisputeStatus.OPEN) {
            throw new BillingValidationException("Un litige est déjà ouvert sur cette commande.");
        }
        order.setDisputeStatus(DisputeStatus.OPEN);
        order.setDisputeOpenedAt(Instant.now());
        order.setDisputeReason(request.reason());
        order.setDisputeClosedAt(null);
        order.setDisputeResolution(null);
        orderRepository.save(order);

        record(order, OrderEventType.DISPUTE_OPENED, OrderActorType.BUYER, buyer, request.reason(),
                request.attachments());
        notificationService.notify(order.getSeller(), NotificationType.DISPUTE_OPENED,
                "Litige ouvert sur une commande",
                "Un litige a été ouvert sur " + itemLabel(order) + " — " + request.reason(),
                sellerOrderHref(order), order);
    }

    // Fil de conversation de la commande, ouvert aux DEUX parties à tout moment — pas seulement
    // en litige. Sans lui, la seule façon de poser une question à l'atelier serait d'ouvrir un
    // litige, ce qui transformerait chaque hésitation en incident. Le même fil sert de dossier
    // quand un litige finit par être ouvert.
    @Transactional
    public void postMessage(String userEmail, UUID orderId, OrderMessageRequest request) {
        User user = userRepository.getByEmail(userEmail);
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
        boolean isBuyer = order.getBuyer() != null && order.getBuyer().getId().equals(user.getId());
        boolean isSeller = order.getSeller() != null && order.getSeller().getId().equals(user.getId());
        // L'arbitre écrit dans le même fil : demander une photo ou une preuve d'expédition avant
        // de trancher évite de décider sur un dossier incomplet.
        boolean isPlatform = !isBuyer && !isSeller && user.getRole() == UserRole.ADMIN;
        if (!isBuyer && !isSeller && !isPlatform) {
            throw new RoleNotAllowedException("Cette commande ne vous concerne pas.");
        }

        OrderActorType actorType = isBuyer ? OrderActorType.BUYER
                : isSeller ? OrderActorType.SELLER : OrderActorType.PLATFORM;
        record(order, OrderEventType.MESSAGE, actorType, user, request.reason(), request.attachments());

        if (isPlatform) {
            notifyBoth(order, NotificationType.ORDER_MESSAGE, "Message de Lumiris",
                    itemLabel(order) + " — " + request.reason());
            return;
        }
        notificationService.notify(isBuyer ? order.getSeller() : order.getBuyer(),
                NotificationType.ORDER_MESSAGE,
                isBuyer ? "Message d'un acheteur" : "Message de l'atelier",
                itemLabel(order) + " — " + request.reason(),
                isBuyer ? sellerOrderHref(order) : buyerOrderHref(order), order);
    }

    // Annulation avant expédition, par l'acheteur (erreur de commande, changement d'avis) comme
    // par le vendeur (rupture, pièce abîmée). Tant que rien n'est parti, faire patienter jusqu'à
    // la livraison pour ensuite organiser un retour n'a aucun sens : on rembourse intégralement
    // et la pièce retourne au catalogue.
    @Transactional
    public void cancel(String userEmail, UUID orderId, String reason) {
        User user = userRepository.getByEmail(userEmail);
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
        boolean isBuyer = order.getBuyer() != null && order.getBuyer().getId().equals(user.getId());
        boolean isSeller = order.getSeller() != null && order.getSeller().getId().equals(user.getId());
        if (!isBuyer && !isSeller) {
            throw new RoleNotAllowedException("Cette commande ne vous concerne pas.");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BillingValidationException(
                    "Cette commande est déjà expédiée — passe par une demande de retour.");
        }

        applyRefund(order, null, reason, isBuyer ? OrderActorType.BUYER : OrderActorType.SELLER, user,
                OrderStatus.CANCELLED);
        record(order, OrderEventType.CANCELLED, isBuyer ? OrderActorType.BUYER : OrderActorType.SELLER, user, reason);
        notificationService.notify(isBuyer ? order.getSeller() : order.getBuyer(),
                NotificationType.ORDER_CANCELLED,
                isBuyer ? "Commande annulée par l'acheteur" : "L'atelier a annulé une commande",
                itemLabel(order) + (reason != null ? " — " + reason : "")
                        + (isBuyer ? "" : " Tu es intégralement remboursé."),
                isBuyer ? sellerOrderHref(order) : buyerOrderHref(order), order);
    }

    // Clôture du litige. Réservée à la plateforme (arbitre) : laisser le vendeur trancher un
    // litige qui le vise reviendrait à le laisser juge et partie.
    @Transactional
    public void resolveDispute(String adminEmail, UUID orderId, DisputeResolutionRequest request) {
        User admin = userRepository.getByEmail(adminEmail);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new RoleNotAllowedException("Seule la plateforme peut clôturer un litige.");
        }
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
        if (order.getDisputeStatus() != DisputeStatus.OPEN) {
            throw new BillingValidationException("Aucun litige ouvert sur cette commande.");
        }
        order.setDisputeClosedAt(Instant.now());
        order.setDisputeResolution(request.resolution());

        if (request.refundCents() != null && request.refundCents() > 0) {
            order.setDisputeStatus(DisputeStatus.RESOLVED);
            orderRepository.save(order);
            record(order, OrderEventType.DISPUTE_RESOLVED, OrderActorType.PLATFORM, admin, request.resolution());
            applyRefund(order, request.refundCents(), request.resolution(), OrderActorType.PLATFORM, admin);
            notifyBoth(order, NotificationType.DISPUTE_RESOLVED, "Litige tranché",
                    "Le litige sur " + itemLabel(order) + " est clos — " + request.resolution());
            return;
        }

        order.setDisputeStatus(DisputeStatus.REJECTED);
        orderRepository.save(order);
        record(order, OrderEventType.DISPUTE_REJECTED, OrderActorType.PLATFORM, admin, request.resolution());
        notifyBoth(order, NotificationType.DISPUTE_REJECTED, "Litige clos sans remboursement",
                "Le litige sur " + itemLabel(order) + " est clos — " + request.resolution());
    }

    // ── Transitions internes (webhook, échéances, résolutions) ──────────────

    // Encaissement confirmé : la commande entre dans le cycle et le vendeur a une pièce à expédier.
    @Transactional
    public void markPaid(MarketplaceOrder order) {
        int days = applyShipDueDate(order);
        record(order, OrderEventType.PAYMENT_CONFIRMED, OrderActorType.SYSTEM, null, null);
        notificationService.notify(order.getSeller(), NotificationType.ORDER_TO_SHIP,
                "Nouvelle commande à expédier",
                itemLabel(order) + " vient d'être vendue. Saisis le suivi pour la marquer expédiée.",
                sellerOrderHref(order), order);
        notificationService.notify(order.getBuyer(), NotificationType.ORDER_PAID,
                "Commande confirmée",
                "Ton paiement est confirmé. L'atelier prépare " + itemLabel(order) + "."
                        + (days >= 1 ? " Expédition annoncée sous " + days + " jour"
                        + (days > 1 ? "s" : "") + "." : ""),
                buyerOrderHref(order), order);
    }

    // La date d'expédition promise est figée ici, une fois pour toutes : le produit est mutable et
    // sa suppression détache la commande, donc relire le délai plus tard laisserait un atelier
    // repousser après coup une promesse déjà faite à un acheteur qui a payé.
    private int applyShipDueDate(MarketplaceOrder order) {
        Instant now = Instant.now();
        int days = order.getProduct() != null
                ? preparationDelayResolver.effectiveDays(order.getProduct(), now)
                : 0;
        order.setShipDueAt(preparationDelayResolver.shipDueAt(now, days));
        orderRepository.save(order);
        return days;
    }

    @Transactional
    public void markDelivered(MarketplaceOrder order, OrderActorType actorType) {
        transitionToDelivered(order, actorType, null);
    }

    // Bordereau fabriqué : l'atelier n'a plus qu'à imprimer. Journalisé à part de l'expédition —
    // il arrive qu'une étiquette soit générée puis le colis remis le lendemain, et un litige sur
    // un délai se joue sur cet écart.
    @Transactional
    public void recordLabelGenerated(MarketplaceOrder order, User seller) {
        record(order, OrderEventType.LABEL_GENERATED, OrderActorType.SELLER, seller,
                trackingSummary(order));
    }

    // Événement poussé par le TRANSPORTEUR. C'est le seul chemin par lequel une commande devient
    // livrée sur un fait constaté plutôt que sur l'échéance présumée du balayage : la fenêtre de
    // rétractation court alors depuis la bonne date, et les fonds partent sur une vraie livraison.
    //
    // Une commande gelée par un litige n'avance pas pour autant : l'événement est consigné, la
    // décision reste humaine.
    @Transactional
    public void applyTrackingUpdate(MarketplaceOrder order, TrackingStatus status, String label,
                                    String trackingNumber, String trackingUrl) {
        if (order.getTrackingStatus() == status) {
            return;
        }
        order.setTrackingStatus(status);
        order.setTrackingStatusLabel(label);
        order.setTrackingUpdatedAt(Instant.now());
        // Le numéro n'est parfois attribué qu'à la prise en charge : on le complète sans jamais
        // écraser un suivi déjà connu de l'acheteur.
        if (order.getTrackingNumber() == null && trackingNumber != null) {
            order.setTrackingNumber(trackingNumber);
        }
        if (order.getTrackingUrl() == null && trackingUrl != null) {
            order.setTrackingUrl(trackingUrl);
        }
        orderRepository.save(order);

        record(order, OrderEventType.TRACKING_UPDATE, OrderActorType.SYSTEM, null, label);

        if (status.isDelivered() && order.getStatus() == OrderStatus.SHIPPED
                && order.getDisputeStatus() != DisputeStatus.OPEN) {
            transitionToDelivered(order, OrderActorType.SYSTEM, null);
            return;
        }
        notifyCarrierMilestone(order, status, label);
    }

    // Un colis change d'état une dizaine de fois entre l'atelier et la boîte aux lettres. Deux
    // seulement méritent d'interrompre l'acheteur : il doit être là pour recevoir, ou quelque
    // chose a mal tourné. Le reste vit dans la timeline, qu'il consulte quand il le veut.
    private void notifyCarrierMilestone(MarketplaceOrder order, TrackingStatus status, String label) {
        if (status == TrackingStatus.OUT_FOR_DELIVERY) {
            notificationService.notify(order.getBuyer(), NotificationType.ORDER_SHIPPED,
                    "Ton colis arrive aujourd'hui",
                    itemLabel(order) + " est en cours de livraison.",
                    buyerOrderHref(order), order);
            return;
        }
        if (status == TrackingStatus.EXCEPTION || status == TrackingStatus.RETURNED) {
            notifyBoth(order, NotificationType.ORDER_SHIPPED,
                    "Incident de livraison",
                    "Le transporteur signale un problème sur " + itemLabel(order)
                            + (label != null ? " — " + label : "") + ".");
        }
    }

    // Relance du vendeur sur une commande payée jamais expédiée. Notification seule : ni l'état ni
    // l'argent ne bougent, on rappelle simplement qu'un acheteur attend.
    @Transactional
    public void remindSellerToShip(MarketplaceOrder order) {
        order.setShipReminderSentAt(Instant.now());
        orderRepository.save(order);
        notificationService.notify(order.getSeller(), NotificationType.ORDER_TO_SHIP,
                "Une commande attend toujours son colis",
                itemLabel(order) + " est payée depuis plusieurs jours et n'est pas encore expédiée."
                        + " Saisis le suivi, ou annule la commande si tu ne peux pas l'honorer.",
                sellerOrderHref(order), order);
    }

    // Reprise d'un versement qui n'a jamais abouti (compte vendeur non activé au moment de la
    // livraison, incident Stripe). Sans elle, l'argent resterait chez la plateforme sans signal.
    @Transactional
    public void retryRelease(MarketplaceOrder order) {
        releaseFunds(order);
    }

    // Panier abandonné avant paiement : le stock réservé doit revenir au catalogue, sinon une
    // pièce unique reste invendable après un simple checkout laissé en plan.
    @Transactional
    public void cancelAbandoned(MarketplaceOrder order) {
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        restock(order);
        record(order, OrderEventType.CANCELLED, OrderActorType.SYSTEM, null, "Paiement non finalisé");
    }

    // Le stock ne vit que sur la déclinaison. Si l'atelier a retiré la taille entre-temps, il n'y a
    // rien à remettre en rayon : on le trace plutôt que d'inventer un stock produit qui n'existe plus.
    private void restock(MarketplaceOrder order) {
        if (order.getVariant() == null) {
            log.warn("Remise en stock impossible pour la commande {} : déclinaison supprimée", order.getId());
            return;
        }
        variantRepository.incrementStock(order.getVariant().getId(), Math.max(1, order.getQuantity()));
    }

    // Livraison : ouvre la fenêtre de retour et libère les fonds retenus au vendeur.
    private void transitionToDelivered(MarketplaceOrder order, OrderActorType actorType, User actor) {
        Instant now = Instant.now();
        order.setDeliveredAt(now);
        order.setReturnDeadline(now.plus(properties.returnWindow()));
        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        record(order, OrderEventType.DELIVERED, actorType, actor, null);
        notificationService.notify(order.getBuyer(), NotificationType.ORDER_DELIVERED,
                "Commande livrée",
                itemLabel(order) + " est marquée livrée. Tu peux demander un retour sous "
                        + properties.getReturnWindowDays() + " jours.",
                buyerOrderHref(order), order);
        releaseFunds(order);
    }

    // Clôture : la fenêtre de retour est écoulée, la commande devient définitive.
    @Transactional
    public void complete(MarketplaceOrder order) {
        order.setCompletedAt(Instant.now());
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        record(order, OrderEventType.COMPLETED, OrderActorType.SYSTEM, null, null);
        notificationService.notify(order.getSeller(), NotificationType.ORDER_COMPLETED,
                "Commande clôturée",
                "La vente de " + itemLabel(order) + " est définitive : la fenêtre de retour est écoulée.",
                sellerOrderHref(order), order);
        releaseFunds(order);
    }

    // Libération best-effort : un échec Stripe laisse les fonds retenus (retry par le job de
    // clôture ou reprise manuelle) sans invalider la transition métier qui vient d'aboutir.
    private void releaseFunds(MarketplaceOrder order) {
        if (order.getStripeTransferId() != null) {
            return;
        }
        try {
            if (payoutService.releaseFunds(order)) {
                record(order, OrderEventType.FUNDS_RELEASED, OrderActorType.SYSTEM, null, null);
                notificationService.notify(order.getSeller(), NotificationType.FUNDS_RELEASED,
                        "Fonds versés",
                        "Le paiement de " + itemLabel(order) + " a été viré sur ton compte.",
                        sellerOrderHref(order), order);
            }
        } catch (RuntimeException e) {
            log.warn("Libération des fonds impossible pour la commande {}: {}", order.getId(), e.getMessage());
        }
    }

    private void applyRefund(MarketplaceOrder order, Integer requestedCents, String reason,
                             OrderActorType actorType, User actor) {
        applyRefund(order, requestedCents, reason, actorType, actor, OrderStatus.REFUNDED);
    }

    // `finalStatus` distingue un remboursement d'une annulation : l'argent suit le même chemin,
    // mais « annulée avant expédition » et « remboursée après retour » ne racontent pas la même
    // histoire dans l'historique de l'acheteur ni dans les statistiques du vendeur.
    private void applyRefund(MarketplaceOrder order, Integer requestedCents, String reason,
                             OrderActorType actorType, User actor, OrderStatus finalStatus) {
        int refundable = refundService.refundableCents(order);
        int amount = requestedCents == null || requestedCents <= 0 ? refundable : requestedCents;
        if (refundable <= 0) {
            throw new BillingValidationException("Cette commande est déjà intégralement remboursée.");
        }
        OrderRefundService.RefundOutcome outcome = refundService.refund(order, amount, reason);

        order.setStripeRefundId(outcome.refundId());
        order.setStripeTransferReversalId(outcome.transferReversalId());
        order.setRefundedCents(order.getRefundedCents() + outcome.amountCents());
        order.setRefundedAt(Instant.now());
        order.setRefundReason(reason);
        order.setStatus(finalStatus);
        orderRepository.save(order);

        // La pièce retourne au catalogue : elle n'a jamais changé de propriétaire durablement.
        restock(order);

        // Remboursement INTÉGRAL (ou annulation) : la pièce quitte la Garde-Robe de l'acheteur —
        // il ne la possède plus, garder son passeport et sa facture serait faux. Un remboursement
        // partiel (geste commercial, retard) laisse au contraire la pièce chez lui.
        if (refundService.refundableCents(order) <= 0) {
            wardrobeItemRepository.deleteByOrder_Id(order.getId());
        }

        record(order, OrderEventType.REFUNDED, actorType, actor,
                formatCents(outcome.amountCents()) + (reason != null ? " — " + reason : ""));
        notificationService.notify(order.getBuyer(), NotificationType.ORDER_REFUNDED,
                "Remboursement en route",
                formatCents(outcome.amountCents()) + " te seront recrédités sur ton moyen de paiement pour "
                        + itemLabel(order) + ".",
                buyerOrderHref(order), order);
    }

    private void notifyBoth(MarketplaceOrder order, NotificationType type, String title, String body) {
        notificationService.notify(order.getBuyer(), type, title, body, buyerOrderHref(order), order);
        notificationService.notify(order.getSeller(), type, title, body, sellerOrderHref(order), order);
    }

    private void record(MarketplaceOrder order, OrderEventType type, OrderActorType actorType,
                        User actor, String message) {
        record(order, type, actorType, actor, message, List.of());
    }

    // Les identifiants proviennent d'un téléversement préalable (POST /api/files) : on ne retient
    // que ceux qui existent réellement, un identifiant fantaisiste ne doit pas faire échouer une
    // transition métier par ailleurs valide.
    private void record(MarketplaceOrder order, OrderEventType type, OrderActorType actorType,
                        User actor, String message, List<UUID> fileIds) {
        List<StoredFile> attachments = fileIds.isEmpty() ? List.of() : storedFileRepository.findAllById(fileIds);
        eventRepository.save(new OrderEvent(order, type, actorType, actor, message, attachments));
    }

    public MarketplaceOrder requireSellerOrder(User seller, UUID orderId) {
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
        if (order.getSeller() == null || !order.getSeller().getId().equals(seller.getId())) {
            throw new RoleNotAllowedException("Cette commande n'appartient pas à votre atelier.");
        }
        return order;
    }

    private MarketplaceOrder requireBuyerOrder(User buyer, UUID orderId) {
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) {
            throw new ResourceNotFoundException("Commande introuvable");
        }
        return order;
    }

    // La déclinaison fait partie de l'identité de la pièce vendue : c'est elle que l'atelier prend
    // sur l'étagère, et c'est sur elle qu'un litige « mauvaise taille » se joue.
    private String itemLabel(MarketplaceOrder order) {
        if (order.getProduct() == null) {
            return "ta commande";
        }
        String label = "« " + order.getProduct().getName() + " »";
        return order.getVariantLabel() != null ? label + " (" + order.getVariantLabel() + ")" : label;
    }

    private String trackingSummary(MarketplaceOrder order) {
        if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
            return order.getCarrier() != null ? "Transporteur : " + order.getCarrier() + "." : "";
        }
        return (order.getCarrier() != null ? order.getCarrier() + " · " : "")
                + "Suivi " + order.getTrackingNumber();
    }

    private String buyerOrderHref(MarketplaceOrder order) {
        return "/commande/suivi/?id=" + order.getId();
    }

    private String sellerOrderHref(MarketplaceOrder order) {
        return "/commandes?order=" + order.getId();
    }

    private String formatCents(int cents) {
        return String.format("%.2f €", cents / 100.0).replace('.', ',');
    }
}
