package com.minoh.lumiris_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Intégration transporteur (étiquette + suivi). Sans clés, `enabled()` est faux et TOUTE la
// fonctionnalité s'efface : l'atelier retrouve la saisie manuelle du suivi, qui reste le chemin
// nominal pour une remise en main propre ou un transporteur hors agrégateur.
@ConfigurationProperties(prefix = "app.shipping")
public record ShippingProperties(
        Sendcloud sendcloud,
        // Poids retenu quand l'annonce n'en déclare aucun. Un bordereau sans poids est refusé par
        // tous les transporteurs : mieux vaut une valeur par défaut affichée qu'un échec sec.
        int defaultWeightGrams,
        // Fenêtre de rétractation et versement courent depuis la livraison CONSTATÉE. Le webhook
        // reste facultatif : le balayage J+7 continue de servir de filet quand rien n'arrive.
        String webhookSecret
) {
    private static final String SENDCLOUD_DEFAULT_BASE_URL = "https://panel.sendcloud.sc/api/v2";

    // Identifiants du panel Sendcloud (Settings → Integrations → API). `baseUrl` est surchargeable
    // pour pointer un bac à sable.
    public record Sendcloud(String publicKey, String secretKey, String baseUrl) {

        public boolean hasCredentials() {
            return isFilled(publicKey) && isFilled(secretKey);
        }
    }

    // Le bloc `sendcloud` entier peut manquer de la configuration : l'adaptateur est construit au
    // démarrage même sans clés (il s'annonce simplement non configuré), il ne doit donc jamais
    // supposer sa présence.
    public String sendcloudBaseUrl() {
        String configured = sendcloud == null ? null : sendcloud.baseUrl();
        return isFilled(configured) ? configured : SENDCLOUD_DEFAULT_BASE_URL;
    }

    public boolean enabled() {
        return sendcloud != null && sendcloud.hasCredentials();
    }

    public int weightGramsOr(int declared) {
        return declared > 0 ? declared : Math.max(1, defaultWeightGrams);
    }

    public boolean hasWebhookSecret() {
        return isFilled(webhookSecret);
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
