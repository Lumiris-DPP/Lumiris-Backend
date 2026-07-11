package com.minoh.lumiris_backend.config.stripe;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Without a secret key the SDK stays uninitialised and billing endpoints fail fast.
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
@RequiredArgsConstructor
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    private final StripeProperties properties;

    @PostConstruct
    void init() {
        if (properties.hasSecretKey()) {
            Stripe.apiKey = properties.secretKey();
            Stripe.setAppInfo("Lumiris-ATELIER", "1.0.0", "https://lumiris.local");
            log.info("Stripe SDK initialised (mode={})",
                    properties.secretKey().startsWith("sk_live") ? "LIVE" : "test");
        } else {
            log.warn("Stripe secret key absent — billing endpoints disabled until STRIPE_SECRET_KEY is set.");
        }
    }
}
