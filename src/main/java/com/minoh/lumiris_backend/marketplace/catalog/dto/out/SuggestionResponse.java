package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

import com.minoh.lumiris_backend.marketplace.decision.dto.out.DecisionLogResponse;
import java.util.List;

// Jusqu'à 3 alternatives artisanales pour un DPP scanné, triées par score puis statut
// ATELIER+, accompagnées du log de décision auditable. La liste peut être plus courte
// (voire vide) si trop peu de pièces atteignent le score scanné : le seuil n'est jamais abaissé.
public record SuggestionResponse(
        List<Suggestion> suggestions,
        DecisionLogResponse decisionLog
) {
    /** Associe une alternative à sa raison de recommandation. */
    public record Suggestion(
            MarketplaceItemResponse item,
            int rank,
            String reason
    ) {}
}
