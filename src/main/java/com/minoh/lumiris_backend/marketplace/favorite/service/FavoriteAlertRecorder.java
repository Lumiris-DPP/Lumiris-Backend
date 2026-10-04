package com.minoh.lumiris_backend.marketplace.favorite.service;

import com.minoh.lumiris_backend.entity.MarketplaceFavorite;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.NotificationType;
import com.minoh.lumiris_backend.marketplace.favorite.repository.MarketplaceFavoriteRepository;
import com.minoh.lumiris_backend.service.NotificationService;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Enregistre les alertes de prix et de stock des favoris.
@Component
@RequiredArgsConstructor
public class FavoriteAlertRecorder {

    private final MarketplaceFavoriteRepository favoriteRepository;
    private final NotificationService notificationService;

    // Réactive les alertes après la remontée du stock.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetLowStockFlags(long threshold) {
        favoriteRepository.clearLowStockFlags(threshold);
    }

    // Enregistre l'alerte de stock faible pour un favori.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendLowStock(MarketplaceFavorite favorite) {
        if (favoriteRepository.claimLowStock(favorite.getId(), Instant.now()) == 0) {
            return false;
        }
        MarketplaceProduct product = favorite.getProduct();
        notificationService.notify(favorite.getUser(), NotificationType.FAVORITE_LOW_STOCK,
                "Il n'en reste qu'un",
                "« " + product.getName() + " », que tu as mise en favori, n'est plus disponible "
                        + "qu'en un exemplaire.",
                productHref(product), null);
        return true;
    }

    // Enregistre l'alerte de baisse de prix pour un favori.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendPriceDrop(MarketplaceFavorite favorite) {
        int previousPrice = favorite.getLastPriceCents();
        int newPrice = favorite.getProduct().getPriceCents();
        if (favoriteRepository.claimPriceDrop(favorite.getId(), previousPrice, newPrice) == 0) {
            return false;
        }
        MarketplaceProduct product = favorite.getProduct();
        notificationService.notify(favorite.getUser(), NotificationType.FAVORITE_PRICE_DROP,
                "Le prix a baissé",
                "« " + product.getName() + " », que tu as mise en favori, est passée de "
                        + formatCents(previousPrice) + " à " + formatCents(newPrice) + ".",
                productHref(product), null);
        return true;
    }

    // Construit le lien public de la pièce favorite.
    private static String productHref(MarketplaceProduct product) {
        return "/boutique/produit/?id=" + product.getId();
    }

    // Présente un montant en centimes sous forme lisible.
    private static String formatCents(int cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0).replace('.', ',') + " €";
    }
}
