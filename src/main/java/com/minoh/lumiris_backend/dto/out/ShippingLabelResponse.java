package com.minoh.lumiris_backend.dto.out;

// Bordereau fabriqué : l'URL présignée du PDF à imprimer, et le suivi que l'atelier n'a pas eu à
// saisir. La commande est déjà passée expédiée quand cette réponse arrive.
public record ShippingLabelResponse(
        String labelUrl,
        String carrier,
        String trackingNumber,
        String trackingUrl
) {
    // État de l'intégration, lu par l'UI vendeur AVANT de proposer l'impression : sans lui,
    // l'atelier découvrirait qu'il lui manque une adresse au moment d'imprimer.
    public record Availability(boolean enabled, String provider, boolean senderAddressReady) {

        public static Availability unavailable() {
            return new Availability(false, null, false);
        }
    }
}
