package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.MarketplaceFavorite;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.NotificationType;
import com.minoh.lumiris_backend.repository.MarketplaceFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

// Envoie une alerte de favori dans sa PROPRE transaction : un balayage de plusieurs centaines
// d'envois ne doit pas tenir une transaction ouverte pendant des minutes, et l'échec de l'un ne
// doit pas annuler les autres.
//
// La revendication conditionnelle est la garde anti-doublon : le job tourne sur chaque instance, et
// un double tir signifierait un e-mail en double. On ne notifie que si l'update a affecté une ligne.
@Component
@RequiredArgsConstructor
public class FavoriteAlertRecorder {

    private final MarketplaceFavoriteRepository favoriteRepository;
    private final NotificationService notificationService;

    // Détecteur de front : un favori dont la pièce est repassée au-dessus du seuil redevient
    // alertable. Sans cette remise à zéro, l'acheteur ne serait prévenu qu'une fois dans sa vie.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetLowStockFlags(long threshold) {
        favoriteRepository.clearLowStockFlags(threshold);
    }

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

    // L'app mobile est exportée en statique avec un slash final : sans lui, la notification pointe
    // sur une 404.
    private static String productHref(MarketplaceProduct product) {
        return "/boutique/produit/?id=" + product.getId();
    }

    private static String formatCents(int cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0).replace('.', ',') + " €";
    }
}
