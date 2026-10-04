package com.minoh.lumiris_backend.marketplace.seller.service;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PreparationDelayResolver {

    public int effectiveDays(MarketplaceProduct product, Instant now) {
        return product.getPreparationDays() + pauseDays(product.getArtisanProfile(), now);
    }

    public Instant activePauseUntil(ArtisanProfile profile, Instant now) {
        Instant until = profile != null ? profile.getPausedUntil() : null;
        return until != null && until.isAfter(now) ? until : null;
    }

    public Instant shipDueAt(Instant from, int effectiveDays) {
        return from.plus(Duration.ofDays(Math.max(0, effectiveDays)));
    }

    private int pauseDays(ArtisanProfile profile, Instant now) {
        Instant until = activePauseUntil(profile, now);
        if (until == null) {
            return 0;
        }
        return (int) Math.ceil(Duration.between(now, until).getSeconds() / 86_400.0);
    }
}
