package com.minoh.lumiris_backend.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Component
public class DppHashUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateDppHash(Map<String, Object> dppData) {
        try {
            String canonicalJson = objectMapper.writeValueAsString(new TreeMap<>(dppData));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize DPP data for hashing", e);
        }
    }
}