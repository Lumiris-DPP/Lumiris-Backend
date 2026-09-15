package com.minoh.lumiris_backend.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Vérification de signature webhook au format Svix (utilisé par Resend). Contenu signé :
 * {@code "<svix-id>.<svix-timestamp>.<body>"} ; HMAC-SHA256 avec le secret base64 (préfixe
 * {@code whsec_} retiré) ; le header {@code svix-signature} est une liste d'entrées
 * {@code v1,<base64>} séparées par des espaces — une correspondance suffit.
 */
public final class SvixSignature {

    private SvixSignature() {}

    public static boolean verify(String secret, String svixId, String svixTimestamp,
                                 String svixSignatureHeader, String body) {
        if (secret == null || secret.isBlank() || svixId == null || svixTimestamp == null
                || svixSignatureHeader == null) {
            return false;
        }
        try {
            byte[] key = Base64.getDecoder().decode(
                    secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String signed = svixId + "." + svixTimestamp + "." + body;
            String expected = Base64.getEncoder().encodeToString(
                    mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));

            for (String part : svixSignatureHeader.split("\\s+")) {
                int comma = part.indexOf(',');
                String candidate = comma >= 0 ? part.substring(comma + 1) : part;
                if (constantTimeEquals(candidate, expected)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < x.length; i++) {
            r |= x[i] ^ y[i];
        }
        return r == 0;
    }
}
