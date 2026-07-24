package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.DppAccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Jetons d'accès élargi des QR, dérivés du code public plutôt que stockés en base
 */
@Service
@RequiredArgsConstructor
public class DppAccessTokenService {

    private static final String DOMAIN = "dpp-access-v1";
    private static final int TOKEN_CHARS = 16;

    @Value("${security.jwt.secret}")
    private String secret;

    /** @return le jeton du niveau, ou {@code null} pour PUBLIC qui n'en a pas besoin. */
    public String tokenFor(String publicCode, DppAccessLevel level) {
        if (!level.isGrantable()) return null;
        return sign(DOMAIN + "|" + publicCode + "|" + level.name());
    }

    /**
     * Retrouve le niveau que porte un jeton. Un jeton absent, forgé, ou émis pour un autre
     * passeport retombe sur PUBLIC : un QR illisible doit afficher le passeport public
     */
    public DppAccessLevel resolve(String publicCode, String token) {
        if (token == null || token.isBlank()) return DppAccessLevel.PUBLIC;

        for (DppAccessLevel level : DppAccessLevel.values()) {
            if (!level.isGrantable()) continue;
            if (constantTimeEquals(tokenFor(publicCode, level), token)) return level;
        }
        return DppAccessLevel.PUBLIC;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, TOKEN_CHARS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign DPP access token", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String candidate) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
