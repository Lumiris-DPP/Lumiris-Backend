package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.dto.in.AffiliateTrackRequest;
import com.minoh.lumiris_backend.dto.in.RepairAppointmentRequest;
import com.minoh.lumiris_backend.dto.in.RepairQuoteRequest;
import com.minoh.lumiris_backend.dto.in.RepairRequestCreateRequest;
import com.minoh.lumiris_backend.dto.out.RepairRequestResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairRequestService {

    private final RepairRequestRepository requestRepo;
    private final RepairerProfileRepository repairerRepo;
    private final DppFormRepository dppFormRepo;
    private final UserRepository userRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final MailService mailService;
    private final AffiliateTrackingService affiliateTrackingService;
    private final com.minoh.lumiris_backend.service.stripe.RepairRequestRefundService refundService;

    // Client VISION : crée la demande depuis un passeport de sa garde-robe (identifié par code public).
    @Transactional
    public RepairRequestResponse create(String consumerEmail, RepairRequestCreateRequest body) {
        User consumer = findUser(consumerEmail);
        RepairerProfile repairer = repairerRepo.findById(body.repairerId())
                .orElseThrow(() -> new ResourceNotFoundException("Retoucheur introuvable : " + body.repairerId()));
        DppForm dppForm = dppFormRepo.findByPublicCode(body.dppPublicCode())
                .orElseThrow(() -> new ResourceNotFoundException("Passeport introuvable : " + body.dppPublicCode()));

        RepairRequest request = new RepairRequest();
        request.setRepairerProfile(repairer);
        request.setConsumerUser(consumer);
        request.setDppForm(dppForm);
        request.setMessage(body.message());

        return toResponse(requestRepo.save(request));
    }

    @Transactional(readOnly = true)
    public List<RepairRequestResponse> findForRepairer(String repairerEmail) {
        RepairerProfile repairer = findOwnRepairerProfile(repairerEmail);
        return requestRepo.findByRepairerProfileOrderByCreatedAtDesc(repairer).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RepairRequestResponse> findForConsumer(String consumerEmail) {
        User consumer = findUser(consumerEmail);
        return requestRepo.findByConsumerUserOrderByCreatedAtDesc(consumer).stream().map(this::toResponse).toList();
    }

    // Retoucheur : soumet un devis -> PENDING -> DRAFT.
    @Transactional
    public RepairRequestResponse submitQuote(String repairerEmail, UUID requestId, RepairQuoteRequest body) {
        RepairRequest request = findOwnedByRepairer(repairerEmail, requestId);
        requireStatus(request, RepairRequestStatus.PENDING);

        request.setQuoteAmountCents(body.amountCents());
        request.setQuoteDescription(body.description());
        request.setQuoteSubmittedAt(Instant.now());
        request.setStatus(RepairRequestStatus.DRAFT);

        return toResponse(requestRepo.save(request));
    }

    // Client : accepte le devis + prend RDV -> DRAFT -> ACCEPTED. Voie SANS paiement (Stripe non
    // configuré / dev local) ; sinon le front passe par /pay et c'est le webhook qui accepte.
    // Déclenche le tracking d'affiliation si le retoucheur a un abonnement LOCAL actif.
    @Transactional
    public RepairRequestResponse acceptQuote(String consumerEmail, UUID requestId, RepairAppointmentRequest body) {
        RepairRequest request = findOwnedByConsumer(consumerEmail, requestId);
        requireStatus(request, RepairRequestStatus.DRAFT);
        return toResponse(markQuoteAccepted(request, body.appointmentAt()));
    }

    // Charge le devis payable du client (contrôles avant tout appel Stripe). Appelé par
    // RepairRequestPaymentService.
    @Transactional(readOnly = true)
    public RepairRequest requirePayableQuote(String consumerEmail, UUID requestId) {
        RepairRequest request = findOwnedByConsumer(consumerEmail, requestId);
        requireStatus(request, RepairRequestStatus.DRAFT);
        if (request.getQuoteAmountCents() == null || request.getQuoteAmountCents() <= 0) {
            throw new ConflictException("Ce devis n'a pas de montant à régler.");
        }
        if (request.getPaidAt() != null) {
            throw new ConflictException("Ce devis a déjà été réglé.");
        }
        return request;
    }

    // Rattache le PaymentIntent au devis (avant paiement).
    @Transactional
    public void attachPaymentIntent(UUID requestId, String paymentIntentId, Instant appointmentAt) {
        RepairRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));
        request.setStripePaymentIntentId(paymentIntentId);
        if (appointmentAt != null) {
            request.setAppointmentAt(appointmentAt);
        }
        requestRepo.save(request);
    }

    // Webhook payment_intent.succeeded (order_type=repair) : le paiement vaut acceptation du devis.
    // Idempotent.
    @Transactional
    public void confirmQuotePaid(String paymentIntentId) {
        requestRepo.findByStripePaymentIntentId(paymentIntentId).ifPresent(request -> {
            if (request.getPaidAt() != null) {
                return;
            }
            request.setPaidAt(Instant.now());
            markQuoteAccepted(request, request.getAppointmentAt());
        });
    }

    private RepairRequest markQuoteAccepted(RepairRequest request, Instant appointmentAt) {
        request.setAppointmentAt(appointmentAt);
        request.setStatus(RepairRequestStatus.ACCEPTED);
        RepairRequest saved = requestRepo.save(request);
        trackAffiliateIfSubscribed(saved);
        return saved;
    }

    // Client : refuse le devis -> DRAFT -> COMPLETED direct (le refus est terminal), retoucheur notifié.
    @Transactional
    public RepairRequestResponse refuseQuote(String consumerEmail, UUID requestId) {
        RepairRequest request = findOwnedByConsumer(consumerEmail, requestId);
        requireStatus(request, RepairRequestStatus.DRAFT);

        request.setQuoteRefusedAt(Instant.now());
        request.setStatus(RepairRequestStatus.COMPLETED);
        RepairRequest saved = requestRepo.save(request);

        User repairerUser = saved.getRepairerProfile().getUser();
        if (repairerUser != null) {
            mailService.sendRepairRequestRefused(
                    repairerUser.getEmail(), repairerUser.getName(), saved.getDppForm().getProductName());
        }

        return toResponse(saved);
    }

    // Retoucheur : démarre l'intervention -> ACCEPTED -> IN_PROGRESS.
    @Transactional
    public RepairRequestResponse start(String repairerEmail, UUID requestId) {
        RepairRequest request = findOwnedByRepairer(repairerEmail, requestId);
        requireStatus(request, RepairRequestStatus.ACCEPTED);
        request.setStatus(RepairRequestStatus.IN_PROGRESS);
        return toResponse(requestRepo.save(request));
    }

    // Retoucheur : clôture -> IN_PROGRESS -> COMPLETED. Le front intègre le composant
    // d'historique DPP existant (POST /api/dpp-forms/{id}/events) pour tracer la fin d'intervention.
    @Transactional
    public RepairRequestResponse complete(String repairerEmail, UUID requestId) {
        RepairRequest request = findOwnedByRepairer(repairerEmail, requestId);
        requireStatus(request, RepairRequestStatus.IN_PROGRESS);
        request.setStatus(RepairRequestStatus.COMPLETED);
        return toResponse(requestRepo.save(request));
    }

    // Client : met fin à la demande à tout moment, sauf si déjà Terminé.
    @Transactional
    public RepairRequestResponse cancel(String consumerEmail, UUID requestId) {
        RepairRequest request = findOwnedByConsumer(consumerEmail, requestId);
        if (request.getStatus() == RepairRequestStatus.COMPLETED) {
            throw new ConflictException("Cette demande est déjà terminée.");
        }
        // Devis payé mais intervention pas encore démarrée : on rembourse le client.
        if (request.getPaidAt() != null && request.getStatus() == RepairRequestStatus.ACCEPTED) {
            refundService.refundQuotePayment(request);
        }
        request.setStatus(RepairRequestStatus.COMPLETED);
        return toResponse(requestRepo.save(request));
    }

    private void trackAffiliateIfSubscribed(RepairRequest request) {
        User repairerUser = request.getRepairerProfile().getUser();
        if (repairerUser == null) {
            return;
        }
        subscriptionRepo.findByUserId(repairerUser.getId())
                .filter(UserSubscription::isActive)
                .filter(sub -> sub.getPlanTier() == PlanTier.LOCAL)
                .ifPresent(sub -> affiliateTrackingService.track(new AffiliateTrackRequest(
                        "repairer_appointment", null, null, request.getDppForm().getPublicCode(), null
                )));
    }

    private void requireStatus(RepairRequest request, RepairRequestStatus expected) {
        if (request.getStatus() != expected) {
            throw new ConflictException("Statut attendu " + expected + ", actuel " + request.getStatus());
        }
    }

    private RepairRequest findOwnedByRepairer(String repairerEmail, UUID requestId) {
        RepairerProfile repairer = findOwnRepairerProfile(repairerEmail);
        RepairRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));
        if (!request.getRepairerProfile().getId().equals(repairer.getId())) {
            throw new ResourceNotFoundException("Demande introuvable");
        }
        return request;
    }

    RepairRequest findOwnedByConsumer(String consumerEmail, UUID requestId) {
        User consumer = findUser(consumerEmail);
        RepairRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));
        if (!request.getConsumerUser().getId().equals(consumer.getId())) {
            throw new ResourceNotFoundException("Demande introuvable");
        }
        return request;
    }

    private RepairerProfile findOwnRepairerProfile(String repairerEmail) {
        User user = findUser(repairerEmail);
        return repairerRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil retoucheur introuvable"));
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private RepairRequestResponse toResponse(RepairRequest r) {
        return new RepairRequestResponse(
                r.getId(),
                r.getRepairerProfile().getId(),
                r.getRepairerProfile().getDisplayName(),
                r.getConsumerUser().getName(),
                r.getDppForm().getId(),
                r.getDppForm().getPublicCode(),
                r.getDppForm().getProductName(),
                r.getMessage(),
                r.getStatus(),
                r.getQuoteAmountCents(),
                r.getQuoteDescription(),
                r.getQuoteSubmittedAt(),
                r.getAppointmentAt(),
                r.getPaidAt(),
                r.getCreatedAt()
        );
    }
}
