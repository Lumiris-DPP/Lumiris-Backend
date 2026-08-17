package com.minoh.lumiris_backend.service.shipping;

// Tout ce qu'un transporteur exige pour fabriquer un bordereau, exprimé sans vocabulaire de
// prestataire : l'adaptateur traduit. `reference` est le numéro que l'atelier lira sur l'étiquette
// et que le support retrouvera côté agrégateur.
public record ParcelRequest(
        Address from,
        Address to,
        String recipientEmail,
        int weightGrams,
        String reference
) {
    public record Address(
            String fullName,
            String line1,
            String line2,
            String postalCode,
            String city,
            String country,
            String phone
    ) {}
}
