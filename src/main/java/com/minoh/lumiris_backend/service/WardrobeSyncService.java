package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.WardrobeSyncRequest;
import com.minoh.lumiris_backend.dto.out.WardrobeItemResponse;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.WardrobeItem;
import com.minoh.lumiris_backend.entity.WardrobeItemKind;
import com.minoh.lumiris_backend.entity.WardrobeItemOrigin;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WardrobeSyncService {

    private static final Set<String> SECTORS = Set.of(
            "textile", "electronics", "appliance", "furniture", "toy", "battery"
    );

    private final UserRepository userRepository;
    private final WardrobeItemRepository wardrobeItemRepository;

    @Transactional
    public List<WardrobeItemResponse> sync(String userEmail, WardrobeSyncRequest request) {
        User user = userRepository.getByEmail(userEmail);
        ensureNoDuplicateKeys(request.upserts());

        List<String> upsertKeys = request.upserts().stream()
                .map(WardrobeSyncRequest.Upsert::clientKey)
                .toList();
        Map<String, WardrobeItem> existingByKey = upsertKeys.isEmpty()
                ? Map.of()
                : wardrobeItemRepository.findByUser_IdAndOriginAndClientKeyIn(
                                user.getId(), WardrobeItemOrigin.USER, upsertKeys)
                        .stream()
                        .collect(Collectors.toMap(WardrobeItem::getClientKey, Function.identity()));

        for (WardrobeSyncRequest.Upsert input : request.upserts()) {
            validate(input);
            WardrobeItem item = existingByKey.getOrDefault(input.clientKey(), new WardrobeItem());
            item.setUser(user);
            item.setOrigin(WardrobeItemOrigin.USER);
            item.setClientKey(input.clientKey());
            item.setKind(input.kind());
            item.setAcquiredAt(input.addedAt());
            item.setPayload(new HashMap<>(input.payload()));
            wardrobeItemRepository.save(item);
        }

        List<String> deletedKeys = request.deletedKeys().stream().distinct().toList();
        if (!deletedKeys.isEmpty()) {
            wardrobeItemRepository.deleteByUserAndOriginAndClientKeys(
                    user.getId(), WardrobeItemOrigin.USER, deletedKeys);
        }

        return wardrobeItemRepository.findOwnedWithPassport(user.getId())
                .stream()
                .map(WardrobeItemResponse::from)
                .toList();
    }

    private static void ensureNoDuplicateKeys(List<WardrobeSyncRequest.Upsert> upserts) {
        Set<String> keys = new HashSet<>();
        if (upserts.stream().anyMatch(item -> !keys.add(item.clientKey()))) {
            throw new IllegalArgumentException("Une pièce ne peut apparaître qu'une fois dans la synchronisation");
        }
    }

    private static void validate(WardrobeSyncRequest.Upsert input) {
        String identifierField = switch (input.kind()) {
            case LUMIRIS_PASSPORT -> "passportId";
            case EXTERNAL_DPP -> "gtin";
            case PUBLIC_DPP -> "publicCode";
            case MANUAL -> "id";
        };
        String identifier = requiredString(input.payload(), identifierField, 255);
        String expectedKey = switch (input.kind()) {
            case LUMIRIS_PASSPORT -> "lumiris:" + identifier;
            case EXTERNAL_DPP -> "gtin:" + identifier;
            case PUBLIC_DPP -> "public:" + identifier;
            case MANUAL -> "manual:" + identifier;
        };
        if (!expectedKey.equals(input.clientKey())) {
            throw new IllegalArgumentException("Clé de pièce incohérente : " + input.clientKey());
        }

        if (input.kind() == WardrobeItemKind.MANUAL) {
            requiredString(input.payload(), "productName", 200);
            String sector = requiredString(input.payload(), "sector", 32);
            if (!SECTORS.contains(sector)) {
                throw new IllegalArgumentException("Secteur de pièce inconnu : " + sector);
            }
        }
        if (input.kind() == WardrobeItemKind.PUBLIC_DPP) {
            requiredString(input.payload(), "productName", 200);
        }
    }

    private static String requiredString(Map<String, Object> payload, String field, int maxLength) {
        Object raw = payload.get(field);
        if (!(raw instanceof String value) || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Champ de pièce invalide : " + field);
        }
        return value;
    }
}
