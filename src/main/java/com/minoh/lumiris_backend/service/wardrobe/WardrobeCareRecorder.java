package com.minoh.lumiris_backend.service.wardrobe;

import com.minoh.lumiris_backend.entity.NotificationType;
import com.minoh.lumiris_backend.entity.WardrobeItem;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import com.minoh.lumiris_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

// Envoie un rappel de Garde-Robe dans sa PROPRE transaction (même raison que FavoriteAlertRecorder) :
// un balayage de plusieurs centaines d'envois ne tient pas une transaction ouverte pendant des
// minutes, et l'échec de l'un n'annule pas les autres.
@Component
@RequiredArgsConstructor
public class WardrobeCareRecorder {

    private final WardrobeItemRepository wardrobeItemRepository;
    private final NotificationService notificationService;
    private final CareAdviceResolver adviceResolver;

    // Renvoie false si rien n'a été envoyé : soit une autre instance a pris la ligne, soit le
    // passeport ne porte aucun symbole d'entretien — auquel cas on n'a rien à dire, et on ne dit
    // rien plutôt que d'envoyer une relance déguisée en conseil.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendCareReminder(WardrobeItem item, CareSeason season, String seasonKey) {
        String advice = adviceResolver.adviceFor(item.getDppForm());
        if (advice == null) {
            return false;
        }
        if (wardrobeItemRepository.claimCareReminder(item.getId(), seasonKey, Instant.now()) == 0) {
            return false;
        }
        notificationService.notify(item.getUser(), NotificationType.WARDROBE_CARE,
                season.occasion(),
                itemLabel(item) + " demande " + advice
                        + ". Si elle a besoin d'un coup de main, un retoucheur proche peut s'en charger.",
                repairHref(), null);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendWarrantyAlert(WardrobeItem item) {
        if (wardrobeItemRepository.claimWarrantyAlert(item.getId(), Instant.now()) == 0) {
            return false;
        }
        long days = Math.max(0, Duration.between(Instant.now(), item.getWarrantyUntil()).toDays());
        notificationService.notify(item.getUser(), NotificationType.WARDROBE_WARRANTY_ENDING,
                "Ta garantie s'achève bientôt",
                "La garantie de " + itemLabel(item) + " expire dans " + days + " jour"
                        + (days > 1 ? "s" : "") + ". C'est le moment de signaler un défaut à l'atelier"
                        + " si la pièce en a un.",
                wardrobeHref(), null);
        return true;
    }

    private static String itemLabel(WardrobeItem item) {
        String name = item.getDppForm() != null ? item.getDppForm().getProductName() : null;
        return name != null ? "« " + name + " »" : "ta pièce";
    }

    // L'app mobile est exportée en statique avec un slash final : sans lui, la notification
    // pointerait sur une 404. `/local` est l'écran qui porte la carte des retoucheurs.
    private static String repairHref() {
        return "/local/";
    }

    private static String wardrobeHref() {
        return "/vault/";
    }
}
