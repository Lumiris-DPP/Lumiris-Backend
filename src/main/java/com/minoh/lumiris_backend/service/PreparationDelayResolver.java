package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// Seul endroit où le délai annoncé sur l'annonce rencontre les congés de l'atelier. Un artisan
// absent ne peut pas commencer avant son retour : les deux s'additionnent.
@Service
public class PreparationDelayResolver {

    public int effectiveDays(MarketplaceProduct product, Instant now) {
        return product.getPreparationDays() + pauseDays(product.getArtisanProfile(), now);
    }

    // Date de retour de l'atelier, ou null si la pause est absente ou déjà passée : le front n'a
    // ainsi jamais d'horloge à comparer.
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
