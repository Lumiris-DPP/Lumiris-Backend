package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.domain.BillingCycle;
import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.dto.in.ConfirmSubscriptionRequest;
import com.minoh.lumiris_backend.dto.in.CreateSetupIntentRequest;
import com.minoh.lumiris_backend.dto.out.CatalogResponse;
import com.minoh.lumiris_backend.dto.out.PlanResponse;
import com.minoh.lumiris_backend.dto.out.PortalResponse;
import com.minoh.lumiris_backend.dto.out.SetupIntentResponse;
import com.minoh.lumiris_backend.dto.out.SubscriptionStateResponse;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.service.stripe.StripeCatalogService;
import com.minoh.lumiris_backend.service.stripe.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final StripeCatalogService catalogService;
    private final StripeProperties properties;

    @GetMapping
    ResponseEntity<SubscriptionStateResponse> current(@CurrentUserEmail String email) {
        return ResponseEntity.ok(subscriptionService.getState(email));
    }

    @GetMapping("/plans")
    ResponseEntity<CatalogResponse> plans() {
        List<PlanResponse> plans = catalogService.catalog().stream().map(PlanResponse::from).toList();
        return ResponseEntity.ok(new CatalogResponse(properties.publishableKey(), plans));
    }

    // Step 1 — returns an Elements client secret for the chosen plan.
    @PostMapping("/setup-intent")
    ResponseEntity<SetupIntentResponse> setupIntent(
            @Valid @RequestBody CreateSetupIntentRequest request,
            @CurrentUserEmail String email
    ) {
        PlanTier tier = PlanTier.fromKey(request.tier())
                .orElseThrow(() -> new BillingValidationException("Plan inconnu: " + request.tier()));
        BillingCycle cycle = BillingCycle.fromKey(request.cycle());
        SubscriptionService.SetupIntentResult result =
                subscriptionService.createSetupIntent(email, tier, cycle);
        return ResponseEntity.ok(SetupIntentResponse.from(result));
    }

    // Step 2 — create the subscription from the confirmed payment method.
    @PostMapping("/confirm")
    ResponseEntity<SubscriptionStateResponse> confirm(
            @Valid @RequestBody ConfirmSubscriptionRequest request,
            @CurrentUserEmail String email
    ) {
        subscriptionService.confirmSubscription(email, request.setupIntentId());
        return ResponseEntity.ok(subscriptionService.getState(email));
    }

    // Change the plan of an existing subscription in-app (no new checkout).
    @PostMapping("/change")
    ResponseEntity<SubscriptionStateResponse> change(
            @Valid @RequestBody CreateSetupIntentRequest request,
            @CurrentUserEmail String email
    ) {
        PlanTier tier = PlanTier.fromKey(request.tier())
                .orElseThrow(() -> new BillingValidationException("Plan inconnu: " + request.tier()));
        BillingCycle cycle = BillingCycle.fromKey(request.cycle());
        subscriptionService.changePlan(email, tier, cycle);
        return ResponseEntity.ok(subscriptionService.getState(email));
    }

    @PostMapping("/portal")
    ResponseEntity<PortalResponse> portal(@CurrentUserEmail String email) {
        String url = subscriptionService.createPortalSession(email);
        return ResponseEntity.ok(new PortalResponse(url));
    }
}
