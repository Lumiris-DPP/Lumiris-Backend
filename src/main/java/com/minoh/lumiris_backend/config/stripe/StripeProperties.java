package com.minoh.lumiris_backend.config.stripe;

import com.minoh.lumiris_backend.exception.StripeNotConfiguredException;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
        String secretKey,
        String publishableKey,
        String webhookSecret,
        boolean bootstrapCatalog,
        String portalReturnUrl,
        Products products
) {
    public record Products(
            String solo,
            String studio,
            String maison,
            String atelierPlus,
            String local
    ) {}

    public boolean hasSecretKey() {
        return secretKey != null && !secretKey.isBlank();
    }

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public void requireSecretKey() {
        if (!hasSecretKey()) {
            throw new StripeNotConfiguredException("Stripe n'est pas configuré (clé secrète absente).");
        }
    }
}
