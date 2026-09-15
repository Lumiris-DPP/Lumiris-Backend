package com.minoh.lumiris_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.NotificationRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * RGPD — droit à la portabilité (art. 20). Assemble, à la demande, l'ensemble des données
 * personnelles rattachées au compte dans une archive ZIP de fichiers JSON. Synchrone : le volume
 * par utilisateur est petit et l'appel reste très en deçà du délai légal de 48 h.
 *
 * Les données agrégées d'audience (passport_analytics_events) sont volontairement exclues : elles
 * ne portent aucune identité et ne sont pas des données personnelles de cet utilisateur.
 */
@Service
@RequiredArgsConstructor
public class AccountDataExportService {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final UserRepository userRepository;
    private final DppFormRepository dppFormRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final NotificationRepository notificationRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public byte[] export(String email) {
        User user = userRepository.getByEmail(email);

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", user.getId().toString());
        account.put("email", user.getEmail());
        account.put("name", user.getName());
        account.put("role", user.getRole().name());
        account.put("avatarUrl", user.getAvatarUrl());
        account.put("verified", user.isVerified());
        account.put("createdAt", str(user.getCreatedAt()));
        account.put("lastSeenAt", str(user.getLastSeenAt()));

        List<Map<String, Object>> passports = dppFormRepository.findByUserId(user.getId()).stream()
                .map(d -> ordered(
                        "id", d.getId().toString(),
                        "publicCode", d.getPublicCode(),
                        "productName", d.getProductName(),
                        "status", d.getStatus().name(),
                        "createdAt", str(d.getCreatedAt()),
                        "updatedAt", str(d.getUpdatedAt())))
                .toList();

        List<Map<String, Object>> repairRequests =
                repairRequestRepository.findByConsumerUserOrderByCreatedAtDesc(user).stream()
                        .map(r -> ordered(
                                "id", r.getId().toString(),
                                "status", r.getStatus().name(),
                                "message", r.getMessage(),
                                "quoteAmountCents", r.getQuoteAmountCents(),
                                "appointmentAt", str(r.getAppointmentAt()),
                                "createdAt", str(r.getCreatedAt())))
                        .toList();

        List<Map<String, Object>> notifications =
                notificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged()).stream()
                        .map(n -> ordered(
                                "type", n.getType().name(),
                                "title", n.getTitle(),
                                "body", n.getBody(),
                                "readAt", str(n.getReadAt()),
                                "createdAt", str(n.getCreatedAt())))
                        .toList();

        Map<String, Object> subscription = subscriptionRepository.findByUserId(user.getId())
                .map(s -> ordered(
                        "planTier", s.getPlanTier() == null ? null : s.getPlanTier().name(),
                        "status", s.getStatus(),
                        "atelierPlus", s.isAtelierPlus(),
                        "cancelAtPeriodEnd", s.isCancelAtPeriodEnd(),
                        "currentPeriodEnd", str(s.getCurrentPeriodEnd())))
                .orElse(null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeJson(zip, "account.json", account);
            writeJson(zip, "passports.json", passports);
            writeJson(zip, "repair_requests.json", repairRequests);
            writeJson(zip, "notifications.json", notifications);
            writeJson(zip, "subscription.json", subscription);
            writeText(zip, "README.txt", """
                    Export de vos données personnelles Lumiris (RGPD art. 20).
                    Généré le %s.

                    account.json          — votre compte
                    passports.json        — les passeports produits que vous avez créés
                    repair_requests.json  — vos demandes de réparation
                    notifications.json    — vos notifications in-app
                    subscription.json     — votre abonnement (le cas échéant)

                    Les commandes et factures sont conservées séparément pour obligation
                    comptable et légale ; contactez privacy@lumiris.fr pour y accéder.
                    """.formatted(Instant.now()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build the account export archive", e);
        }
        return out.toByteArray();
    }

    private static void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(JSON.writeValueAsBytes(value));
        zip.closeEntry();
    }

    private static void writeText(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String str(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    // Small ordered-map helper: keys/values interleaved, insertion order preserved in the JSON.
    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}
