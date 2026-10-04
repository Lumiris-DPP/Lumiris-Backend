package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

// Calcule le délai de préparation selon la pause atelier.
@Service
public class PreparationDelayResolver {

    // Calcule le délai annoncé en tenant compte de la pause.
    public int effectiveDays(MarketplaceProduct product, Instant now) {
        return product.getPreparationDays() + pauseDays(product.getArtisanProfile(), now);
    }

    // Retrouve la fin d'une pause atelier encore active.
    public Instant activePauseUntil(ArtisanProfile profile, Instant now) {
        Instant until = profile != null ? profile.getPausedUntil() : null;
        return until != null && until.isAfter(now) ? until : null;
    }

    // Ajoute le délai de préparation à la date du paiement.
    public Instant shipDueAt(Instant from, int effectiveDays) {
        return from.plus(Duration.ofDays(Math.max(0, effectiveDays)));
    }

    // Convertit la pause restante en jours annoncés.
    private int pauseDays(ArtisanProfile profile, Instant now) {
        Instant until = activePauseUntil(profile, now);
        if (until == null) {
            return 0;
        }
        return (int) Math.ceil(Duration.between(now, until).getSeconds() / 86_400.0);
    }
}
