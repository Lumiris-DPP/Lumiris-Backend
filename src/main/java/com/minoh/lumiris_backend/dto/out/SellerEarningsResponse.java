package com.minoh.lumiris_backend.dto.out;

// LUMIRIS · Trésorerie vendeur (escrow). Montants nets (part vendeur), en centimes.
// heldCents = encaissé mais retenu par la plateforme (en attente de livraison) ;
// releasedCents = déjà reversé au vendeur (Transfer créé).
public record SellerEarningsResponse(
        long heldCents,
        long releasedCents,
        String currency
) {}
