package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.PayoutExpectation;
import com.minoh.lumiris_backend.entity.RepairRequest;
import org.springframework.stereotype.Service;

// Équivalent de PayoutScheduleResolver pour une demande de réparation. Pas de délai de livraison à
// projeter ici (pas d'expédition) : payé mais pas encore terminé reste SCHEDULED sans date promise ;
// terminé et pas encore transféré est IMMINENT (le balayage/la clôture rejoue le virement sous 24h).
// Aucun mécanisme de litige n'existe encore sur les demandes de réparation — ON_HOLD n'est donc
// jamais produit ici pour l'instant, mais reste dans le type pour rester compatible le jour où il
// le sera (remboursement contesté, etc.).
@Service
public class RepairPayoutScheduleResolver {

    public record PayoutForecast(PayoutExpectation expectation, java.time.Instant expectedAt) {}

    public PayoutForecast forecast(RepairRequest request) {
        return switch (request.getStatus()) {
            case COMPLETED -> new PayoutForecast(PayoutExpectation.IMMINENT, null);
            default -> new PayoutForecast(PayoutExpectation.SCHEDULED, null);
        };
    }
}
