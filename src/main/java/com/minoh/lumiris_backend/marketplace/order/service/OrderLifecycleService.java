package com.minoh.lumiris_backend.marketplace.order.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.NotificationType;
import com.minoh.lumiris_backend.entity.OrderActorType;
import com.minoh.lumiris_backend.entity.OrderEvent;
import com.minoh.lumiris_backend.entity.OrderEventType;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.TrackingStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductVariantRepository;
import com.minoh.lumiris_backend.marketplace.order.dto.in.DisputeResolutionRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.RefundRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ReturnDecisionRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ReturnRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ShipOrderRequest;
import com.minoh.lumiris_backend.marketplace.order.exception.InvalidOrderTransitionException;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.marketplace.order.repository.OrderEventRepository;
import com.minoh.lumiris_backend.marketplace.seller.service.PreparationDelayResolver;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import com.minoh.lumiris_backend.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
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

    public void requireShippable(MarketplaceOrder order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new InvalidOrderTransitionException(
                    "Seule une commande payée et non encore expédiée peut être marquée expédiée.");
        }
    }

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

    @Transactional
    public void markReturnReceived(String sellerEmail, UUID orderId) {
        User seller = userRepository.getByEmail(sellerEmail);
        MarketplaceOrder order = requireSellerOrder(seller, orderId);
        if (order.getStatus() != OrderStatus.RETURN_APPROVED) {
            throw new InvalidOrderTransitionException("Aucun retour accepté en attente sur cette commande.");
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
            throw new InvalidOrderTransitionException("Aucune demande de retour en attente sur cette commande.");
        }
        order.setReturnDecidedAt(Instant.now());
        order.setReturnDecisionNote(request.note());
        order.setStatus(request.accepted() ? OrderStatus.RETURN_APPROVED : OrderStatus.RETURN_REFUSED);
        orderRepository.save(order);
        record(order, request.accepted() ? OrderEventType.RETURN_APPROVED : OrderEventType.RETURN_REFUSED,
                OrderActorType.SELLER, seller, request.note(), request.attachments());

        if (request.accepted()) {
            notificationService.notify(order.getBuyer(), NotificationType.RETURN_APPROVED,
                    "Ton retour est accepté",
                    "L'atelier accepte le retour de " + itemLabel(order)
                            + ". Renvoie la pièce, le remboursement suivra sa réception.",
                    buyerOrderHref(order), order);
            return;
        }

        notificationService.notify(order.getBuyer(), NotificationType.RETURN_REFUSED,
                "Ton retour a été refusé",
                "L'atelier refuse le retour de " + itemLabel(order)
                        + (request.note() != null ? " — " + request.note() : "")
                        + ". Tu peux ouvrir un litige si tu contestes cette décision.",
                buyerOrderHref(order), order);
    }

    @Transactional
    public void refund(String sellerEmail, UUID orderId, RefundRequest request) {
        User seller = userRepository.getByEmail(sellerEmail);
        MarketplaceOrder order = requireSellerOrder(seller, orderId);
        if (isRefundReplay(order, request)) {
            return;
        }
        String operationKey = request.operationId() == null
                ? order.getRefundedCents() + ":" + request.amountCents()
                : request.operationId().toString();
        applyRefund(order, request.amountCents(), request.reason(), OrderActorType.SELLER, seller,
                OrderStatus.REFUNDED, operationKey);
        if (request.operationId() != null) {
            jdbc.update("insert into marketplace_order_refund_operations "
                    + "(order_id, operation_id, requested_cents, reason) values (?, ?, ?, ?)",
                    orderId, request.operationId(), request.amountCents(), request.reason());
        }
    }

    @Transactional
    public void confirmDelivery(String buyerEmail, UUID orderId) {
        User buyer = userRepository.getByEmail(buyerEmail);
        MarketplaceOrder order = requireBuyerOrder(buyer, orderId);
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new InvalidOrderTransitionException("Cette commande n'est pas en cours de livraison.");
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

    @Transactional
    public void postMessage(String userEmail, UUID orderId, OrderMessageRequest request) {
        User user = userRepository.getByEmail(userEmail);
        MarketplaceOrder order = lockOrder(orderId);
        boolean isBuyer = order.getBuyer() != null && order.getBuyer().getId().equals(user.getId());
        boolean isSeller = order.getSeller() != null && order.getSeller().getId().equals(user.getId());
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

    @Transactional
    public void cancel(String userEmail, UUID orderId, String reason) {
        User user = userRepository.getByEmail(userEmail);
        MarketplaceOrder order = lockOrder(orderId);
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

    @Transactional
    public void resolveDispute(String adminEmail, UUID orderId, DisputeResolutionRequest request) {
        User admin = userRepository.getByEmail(adminEmail);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new RoleNotAllowedException("Seule la plateforme peut clôturer un litige.");
        }
        MarketplaceOrder order = lockOrder(orderId);
        if (order.getDisputeStatus() != DisputeStatus.OPEN) {
            throw new BillingValidationException("Aucun litige ouvert sur cette commande.");
        }
        order.setDisputeClosedAt(Instant.now());
        order.setDisputeResolution(request.resolution());

        if (request.refundCents() != null && request.refundCents() > 0) {
            order.setDisputeStatus(DisputeStatus.RESOLVED);
            orderRepository.save(order);
            record(order, OrderEventType.DISPUTE_RESOLVED, OrderActorType.PLATFORM, admin, request.resolution());
            applyRefund(order, request.refundCents(), request.resolution(), OrderActorType.PLATFORM, admin,
                    OrderStatus.REFUNDED);
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
        order = lockOrder(order.getId());
        if (order.getStatus() != OrderStatus.SHIPPED || order.getDisputeStatus() == DisputeStatus.OPEN) {
            return;
        }
        transitionToDelivered(order, actorType, null);
    }

    @Transactional
    public void recordLabelGenerated(MarketplaceOrder order, User seller) {
        record(order, OrderEventType.LABEL_GENERATED, OrderActorType.SELLER, seller,
                trackingSummary(order));
    }

    @Transactional
    public void applyTrackingUpdate(MarketplaceOrder order, TrackingStatus status, String label,
                                    String trackingNumber, String trackingUrl) {
        order = lockOrder(order.getId());
        if (order.getTrackingStatus() == status) {
            return;
        }
        order.setTrackingStatus(status);
        order.setTrackingStatusLabel(label);
        order.setTrackingUpdatedAt(Instant.now());
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

    @Transactional
    public void remindSellerToShip(MarketplaceOrder order) {
        order = lockOrder(order.getId());
        if (order.getStatus() != OrderStatus.PAID || order.getShipReminderSentAt() != null) {
            return;
        }
        order.setShipReminderSentAt(Instant.now());
        orderRepository.save(order);
        notificationService.notify(order.getSeller(), NotificationType.ORDER_TO_SHIP,
                "Une commande attend toujours son colis",
                itemLabel(order) + " est payée depuis plusieurs jours et n'est pas encore expédiée."
                        + " Saisis le suivi, ou annule la commande si tu ne peux pas l'honorer.",
                sellerOrderHref(order), order);
    }

    @Transactional
    public void retryRelease(MarketplaceOrder order) {
        order = lockOrder(order.getId());
        releaseFunds(order);
    }

    @Transactional
    public void cancelAbandoned(MarketplaceOrder order) {
        order = lockOrder(order.getId());
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        restock(order);
        record(order, OrderEventType.CANCELLED, OrderActorType.SYSTEM, null, "Paiement non finalisé");
    }

    private void restock(MarketplaceOrder order) {
        if (order.getVariant() == null) {
            log.warn("Remise en stock impossible pour la commande {} : déclinaison supprimée", order.getId());
            return;
        }
        variantRepository.incrementStock(order.getVariant().getId(), Math.max(1, order.getQuantity()));
    }

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

    @Transactional
    public void complete(MarketplaceOrder order) {
        order = lockOrder(order.getId());
        if (!Set.of(OrderStatus.DELIVERED, OrderStatus.RETURN_REFUSED, OrderStatus.RETURN_RECEIVED)
                .contains(order.getStatus()) || order.getDisputeStatus() == DisputeStatus.OPEN) {
            return;
        }
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

    private void releaseFunds(MarketplaceOrder order) {
        if (order.getStripeTransferId() != null) {
            return;
        }
        String transferId;
        try {
            transferId = payoutService.createTransfer(order).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Libération des fonds impossible pour la commande {}: {}", order.getId(), e.getMessage());
            return;
        }
        if (transferId == null) {
            return;
        }
        order.setStripeTransferId(transferId);
        order.setReleasedAt(Instant.now());
        orderRepository.save(order);
        record(order, OrderEventType.FUNDS_RELEASED, OrderActorType.SYSTEM, null, null);
        notificationService.notify(order.getSeller(), NotificationType.FUNDS_RELEASED,
                "Fonds versés",
                "Le paiement de " + itemLabel(order) + " a été viré sur ton compte.",
                sellerOrderHref(order), order);
    }

    private void applyRefund(MarketplaceOrder order, Integer requestedCents, String reason,
                             OrderActorType actorType, User actor, OrderStatus finalStatus) {
        applyRefund(order, requestedCents, reason, actorType, actor, finalStatus,
                order.getRefundedCents() + ":" + requestedCents);
    }

    private void applyRefund(MarketplaceOrder order, Integer requestedCents, String reason,
                             OrderActorType actorType, User actor, OrderStatus finalStatus, String operationKey) {
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BillingValidationException("Cette commande ne peut pas être remboursée.");
        }
        int previouslyRefunded = order.getRefundedCents();
        int refundable = refundService.refundableCents(order);
        int amount = requestedCents == null || requestedCents <= 0 ? refundable : requestedCents;
        if (refundable <= 0) {
            throw new BillingValidationException("Cette commande est déjà intégralement remboursée.");
        }
        OrderRefundService.RefundOutcome outcome = refundService.refund(order, amount, reason, operationKey);

        order.setStripeRefundId(outcome.refundId());
        order.setStripeTransferReversalId(outcome.transferReversalId());
        order.setRefundedCents(order.getRefundedCents() + outcome.amountCents());
        order.setRefundedAt(Instant.now());
        order.setRefundReason(reason);
        order.setStatus(finalStatus);
        orderRepository.save(order);
        if (previouslyRefunded == 0) {
            restock(order);
        }
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

    private void record(MarketplaceOrder order, OrderEventType type, OrderActorType actorType,
                        User actor, String message, List<UUID> fileIds) {
        List<StoredFile> attachments = fileIds.isEmpty() ? List.of() : storedFileRepository.findAllById(fileIds);
        eventRepository.save(new OrderEvent(order, type, actorType, actor, message, attachments));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MarketplaceOrder requireSellerOrder(User seller, UUID orderId) {
        MarketplaceOrder order = lockOrder(orderId);
        if (order.getSeller() == null || !order.getSeller().getId().equals(seller.getId())) {
            throw new RoleNotAllowedException("Cette commande n'appartient pas à votre atelier.");
        }
        return order;
    }

    private MarketplaceOrder requireBuyerOrder(User buyer, UUID orderId) {
        MarketplaceOrder order = lockOrder(orderId);
        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyer.getId())) {
            throw new ResourceNotFoundException("Commande introuvable");
        }
        return order;
    }

    private boolean isRefundReplay(MarketplaceOrder order, RefundRequest request) {
        if (request.operationId() == null) {
            return false;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select requested_cents, reason from marketplace_order_refund_operations "
                        + "where order_id = ? and operation_id = ?", order.getId(), request.operationId());
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> previous = rows.getFirst();
        if (!Objects.equals(previous.get("requested_cents"), request.amountCents())
                || !Objects.equals(previous.get("reason"), request.reason())) {
            throw new BillingValidationException("Cet identifiant de remboursement désigne une autre opération.");
        }
        return true;
    }

    private MarketplaceOrder lockOrder(UUID id) {
        MarketplaceOrder order = entityManager.find(MarketplaceOrder.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) {
            throw new ResourceNotFoundException("Commande introuvable");
        }
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

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
