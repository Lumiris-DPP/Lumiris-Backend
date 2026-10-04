package com.minoh.lumiris_backend.marketplace.seller.service;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.SellerAccount;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerStatusResponse;
import com.minoh.lumiris_backend.marketplace.seller.repository.SellerAccountRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.integration.stripe.StripeCalls;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Configure et actualise le compte de paiement vendeur.
@Service
@RequiredArgsConstructor
public class SellerConnectService {

    private static final Logger log = LoggerFactory.getLogger(SellerConnectService.class);

    private final StripeProperties properties;
    private final SellerAccountRepository sellerAccountRepository;
    private final UserRepository userRepository;

    // Prépare le lien Stripe d'activation du compte vendeur.
    @Transactional
    public String startOnboarding(String userEmail) {
        properties.requireSecretKey();
        User user = requireSeller(userEmail);
        return StripeCalls.billed("Onboarding vendeur impossible", () -> {
            String acctId = ensureAccountId(user);
            String base = properties.portalReturnUrl();
            return com.stripe.model.AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(acctId)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .setRefreshUrl(base + (base.contains("?") ? "&" : "?") + "connect=refresh")
                    .setReturnUrl(base + (base.contains("?") ? "&" : "?") + "connect=return")
                    .build()).getUrl();
        });
    }

    // Retrouve ou crée le compte Stripe du vendeur.
    private String ensureAccountId(User user) throws StripeException {
        SellerAccount existing = sellerAccountRepository.findByUser_Id(user.getId()).orElse(null);
        if (existing != null) {
            return existing.getStripeAccountId();
        }
        Account created = Account.create(AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setCountry("FR")
                .setEmail(user.getEmail())
                .setCapabilities(AccountCreateParams.Capabilities.builder()
                        .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                .setRequested(true).build())
                        .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                                .setRequested(true).build())
                        .build())
                .putMetadata("user_id", user.getId().toString())
                .build());
        persistAccount(user, created.getId(), created);
        return created.getId();
    }

    // Présente l'état enregistré du compte de paiement vendeur.
    @Transactional
    public SellerStatusResponse getStatus(String userEmail) {
        User user = requireSeller(userEmail);
        SellerAccount account = sellerAccountRepository.findByUser_Id(user.getId()).orElse(null);
        if (account == null || !properties.hasSecretKey()) {
            return account == null ? SellerStatusResponse.none() : toStatus(account);
        }
        try {
            Account stripeAccount = Account.retrieve(account.getStripeAccountId());
            applyStripeState(account, stripeAccount);
            sellerAccountRepository.save(account);
        } catch (com.stripe.exception.StripeException e) {
            log.debug("Seller account refresh failed for {}: {}", account.getStripeAccountId(), e.getMessage());
        }
        return toStatus(account);
    }

    // Actualise le compte vendeur depuis les informations Stripe.
    @Transactional
    public void syncFromStripe(String stripeAccountId) {
        sellerAccountRepository.findByStripeAccountId(stripeAccountId).ifPresent(account -> {
            try {
                applyStripeState(account, Account.retrieve(stripeAccountId));
                sellerAccountRepository.save(account);
                log.info("Seller account {} synced (charges={}, payouts={})",
                        stripeAccountId, account.isChargesEnabled(), account.isPayoutsEnabled());
            } catch (com.stripe.exception.StripeException e) {
                log.warn("Could not sync seller account {}: {}", stripeAccountId, e.getMessage());
            }
        });
    }

    // Enregistre le compte vendeur nouvellement créé chez Stripe.
    private void persistAccount(User user, String accountId, Account stripeAccount) {
        SellerAccount account = new SellerAccount();
        account.setUser(user);
        account.setStripeAccountId(accountId);
        applyStripeState(account, stripeAccount);
        sellerAccountRepository.save(account);
    }

    // Reporte les autorisations et l'activation du compte Stripe.
    private void applyStripeState(SellerAccount account, Account stripeAccount) {
        account.setChargesEnabled(Boolean.TRUE.equals(stripeAccount.getChargesEnabled()));
        account.setPayoutsEnabled(Boolean.TRUE.equals(stripeAccount.getPayoutsEnabled()));
        account.setOnboardingCompleted(Boolean.TRUE.equals(stripeAccount.getDetailsSubmitted()));
    }

    // Présente les informations enregistrées du compte vendeur.
    private SellerStatusResponse toStatus(SellerAccount a) {
        return new SellerStatusResponse(true, a.isOnboardingCompleted(), a.isChargesEnabled(), a.isPayoutsEnabled());
    }

    // Vérifie que le rôle de l'utilisateur permet un compte vendeur.
    private User requireSeller(String email) {
        User user = userRepository.getByEmail(email);
        if (user.getRole() != UserRole.ARTISAN && user.getRole() != UserRole.REPAIRER) {
            throw new RoleNotAllowedException("Seuls les artisans et retoucheurs peuvent être payés via la plateforme.");
        }
        return user;
    }
}
