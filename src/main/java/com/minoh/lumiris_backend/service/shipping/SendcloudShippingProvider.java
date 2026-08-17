package com.minoh.lumiris_backend.service.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.config.ShippingProperties;
import com.minoh.lumiris_backend.entity.TrackingStatus;
import com.minoh.lumiris_backend.exception.ShippingProviderException;
import com.minoh.lumiris_backend.exception.WebhookSignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

// Adaptateur Sendcloud (API v2, authentification Basic public:secret).
//
// Sendcloud n'imprime une étiquette que si le colis est annoncé avec une MÉTHODE d'envoi. Plutôt
// que d'imposer une méthode unique en configuration — qui ne survivrait ni à un colis lourd ni à
// une livraison hors de France — on demande à Sendcloud les méthodes réellement applicables à
// cette destination et à ce poids, et on retient la moins chère. C'est exactement l'intérêt d'un
// agrégateur : son tarif négocié devient l'argument d'adhésion de l'atelier.
@Component
public class SendcloudShippingProvider implements ShippingProvider {

    private static final Logger log = LoggerFactory.getLogger(SendcloudShippingProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SIGNATURE_HEADER_ALGORITHM = "HmacSHA256";

    // Sendcloud exprime les poids en kilogrammes, en chaîne à trois décimales.
    private static final int GRAMS_PER_KILO = 1000;

    private final ShippingProperties properties;
    private final RestClient restClient;

    public SendcloudShippingProvider(ShippingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.sendcloudBaseUrl())
                .defaultHeader("Authorization", basicAuth(properties))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String name() {
        return "sendcloud";
    }

    @Override
    public boolean configured() {
        return properties.enabled();
    }

    @Override
    public ShippingLabel createLabel(ParcelRequest request) {
        int shippingMethodId = cheapestMethodFor(request);
        JsonNode parcel = announceParcel(request, shippingMethodId);

        String labelUrl = firstLabelUrl(parcel);
        if (labelUrl == null) {
            throw new ShippingProviderException(
                    "Sendcloud a annoncé le colis sans produire d'étiquette imprimable.");
        }
        return new ShippingLabel(
                text(parcel, "id"),
                carrierName(parcel),
                text(parcel, "tracking_number"),
                text(parcel, "tracking_url"),
                downloadLabel(labelUrl));
    }

    // Sendcloud signe le corps brut en HMAC-SHA256 (en-tête `Sendcloud-Signature`). Sans secret
    // configuré, on REFUSE : un endpoint public qui ferait avancer des commandes et libérerait des
    // fonds sur une charge utile non signée serait une porte ouverte.
    @Override
    public Optional<CarrierEvent> readWebhook(String payload, String signature) {
        requireValidSignature(payload, signature);
        try {
            JsonNode root = MAPPER.readTree(payload);
            if (!"parcel_status_changed".equals(root.path("action").asText())) {
                return Optional.empty();
            }
            JsonNode parcel = root.path("parcel");
            String parcelId = text(parcel, "id");
            if (parcelId == null) {
                return Optional.empty();
            }
            String statusLabel = text(parcel.path("status"), "message");
            return Optional.of(new CarrierEvent(
                    parcelId,
                    text(parcel, "tracking_number"),
                    text(parcel, "tracking_url"),
                    toTrackingStatus(statusLabel),
                    statusLabel,
                    eventInstant(root)));
        } catch (WebhookSignatureException e) {
            throw e;
        } catch (Exception e) {
            throw new ShippingProviderException("Charge utile Sendcloud illisible.", e);
        }
    }

    // ── Appels HTTP ─────────────────────────────────────────────────────────

    private int cheapestMethodFor(ParcelRequest request) {
        JsonNode methods = call("Interrogation des méthodes d'envoi impossible", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/shipping_methods")
                        .queryParam("to_country", request.to().country())
                        .queryParam("from_postal_code", request.from().postalCode())
                        .build())
                .retrieve()
                .body(JsonNode.class));

        double weightKilos = request.weightGrams() / (double) GRAMS_PER_KILO;
        return streamOf(methods.path("shipping_methods"))
                .filter(method -> acceptsWeight(method, weightKilos))
                .min((left, right) -> Double.compare(price(left), price(right)))
                .map(method -> method.path("id").asInt())
                .orElseThrow(() -> new ShippingProviderException(
                        "Aucun transporteur ne dessert " + request.to().country()
                                + " pour un colis de " + request.weightGrams() + " g."));
    }

    private JsonNode announceParcel(ParcelRequest request, int shippingMethodId) {
        Map<String, Object> parcel = new LinkedHashMap<>();
        parcel.put("name", request.to().fullName());
        parcel.put("address", request.to().line1());
        parcel.put("address_2", nullToEmpty(request.to().line2()));
        parcel.put("city", request.to().city());
        parcel.put("postal_code", request.to().postalCode());
        parcel.put("country", request.to().country());
        parcel.put("telephone", nullToEmpty(request.to().phone()));
        parcel.put("email", nullToEmpty(request.recipientEmail()));
        parcel.put("order_number", request.reference());
        parcel.put("weight", String.format(Locale.ROOT, "%.3f",
                request.weightGrams() / (double) GRAMS_PER_KILO));
        parcel.put("request_label", true);
        parcel.put("shipment", Map.of("id", shippingMethodId));

        JsonNode response = call("Création du bordereau impossible", () -> restClient.post()
                .uri("/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("parcel", parcel))
                .retrieve()
                .body(JsonNode.class));
        return response.path("parcel");
    }

    // L'URL d'étiquette renvoyée est absolue et hors baseUrl, mais reste protégée par les mêmes
    // identifiants : on refait donc l'appel avec l'en-tête d'authentification.
    private byte[] downloadLabel(String labelUrl) {
        byte[] pdf = call("Téléchargement de l'étiquette impossible", () -> restClient.get()
                .uri(labelUrl)
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .body(byte[].class));
        if (pdf == null || pdf.length == 0) {
            throw new ShippingProviderException("Sendcloud a renvoyé une étiquette vide.");
        }
        return pdf;
    }

    private <T> T call(String failureMessage, Supplier<T> exchange) {
        try {
            T body = exchange.get();
            if (body == null) {
                throw new ShippingProviderException(failureMessage + " (réponse vide).");
            }
            return body;
        } catch (ShippingProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Sendcloud : {} — {}", failureMessage, e.getMessage());
            throw new ShippingProviderException(failureMessage + ".", e);
        }
    }

    // ── Lecture des réponses ────────────────────────────────────────────────

    private static boolean acceptsWeight(JsonNode method, double weightKilos) {
        double min = method.path("min_weight").asDouble(0);
        double max = method.path("max_weight").asDouble(Double.MAX_VALUE);
        return weightKilos >= min && weightKilos <= max;
    }

    // Une méthode sans pays facturé (contrat sur devis) ne doit pas passer pour gratuite et rafler
    // le tri : elle est reléguée en fin de classement.
    private static double price(JsonNode method) {
        return streamOf(method.path("countries"))
                .map(country -> country.path("price"))
                .filter(JsonNode::isNumber)
                .mapToDouble(JsonNode::asDouble)
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private static String carrierName(JsonNode parcel) {
        String code = text(parcel.path("carrier"), "code");
        return code == null ? "Sendcloud" : code.toUpperCase(Locale.ROOT);
    }

    private static String firstLabelUrl(JsonNode parcel) {
        return streamOf(parcel.path("label").path("normal_printer"))
                .map(JsonNode::asText)
                .filter(url -> !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    // Le vocabulaire de statut de Sendcloud est textuel et versionné par transporteur ; ses
    // identifiants numériques ne le sont pas moins. On lit donc la phrase, et surtout on n'accorde
    // DELIVERED qu'à une livraison franche : « delivery attempt failed » contient « deliver ».
    private static TrackingStatus toTrackingStatus(String message) {
        if (message == null || message.isBlank()) {
            return TrackingStatus.IN_TRANSIT;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("cancelled") || normalized.contains("error")
                || normalized.contains("failed") || normalized.contains("refused")) {
            return TrackingStatus.EXCEPTION;
        }
        if (normalized.contains("return")) {
            return TrackingStatus.RETURNED;
        }
        if (normalized.contains("delivered")) {
            return TrackingStatus.DELIVERED;
        }
        if (normalized.contains("out for delivery") || normalized.contains("ready to be collected")
                || normalized.contains("service point")) {
            return TrackingStatus.OUT_FOR_DELIVERY;
        }
        if (normalized.contains("announced") || normalized.contains("ready to send")
                || normalized.contains("no label")) {
            return TrackingStatus.ANNOUNCED;
        }
        return TrackingStatus.IN_TRANSIT;
    }

    private static Instant eventInstant(JsonNode root) {
        long epochMillis = root.path("timestamp").asLong(0);
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis) : Instant.now();
    }

    // ── Signature ───────────────────────────────────────────────────────────

    private void requireValidSignature(String payload, String signature) {
        if (!properties.hasWebhookSecret()) {
            throw new WebhookSignatureException(
                    "Aucun secret de webhook transporteur configuré : charge utile rejetée.");
        }
        if (signature == null || signature.isBlank()) {
            throw new WebhookSignatureException("Signature de webhook transporteur absente.");
        }
        if (!MessageDigest.isEqual(
                expectedSignature(payload).getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookSignatureException("Signature de webhook transporteur invalide.");
        }
    }

    private String expectedSignature(String payload) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_HEADER_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.webhookSecret().getBytes(StandardCharsets.UTF_8), SIGNATURE_HEADER_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new ShippingProviderException("Vérification de signature impossible.", e);
        }
    }

    private static String basicAuth(ShippingProperties properties) {
        ShippingProperties.Sendcloud sendcloud = properties.sendcloud();
        String credentials = (sendcloud == null ? "" : sendcloud.publicKey() + ":" + sendcloud.secretKey());
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    // ── Utilitaires ─────────────────────────────────────────────────────────

    private static Stream<JsonNode> streamOf(JsonNode array) {
        return array.isArray()
                ? StreamSupport.stream(array.spliterator(), false)
                : Stream.empty();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
