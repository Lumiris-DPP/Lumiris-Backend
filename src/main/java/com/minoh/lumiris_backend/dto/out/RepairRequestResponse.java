package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.RepairRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestResponse(
        UUID id,
        UUID repairerProfileId,
        String repairerDisplayName,
        String consumerName,
        UUID dppFormId,
        String dppPublicCode,
        String dppProductName,
        String message,
        RepairRequestStatus status,
        Long quoteAmountCents,
        String quoteDescription,
        Instant quoteSubmittedAt,
        Instant appointmentAt,
        Instant paidAt,
        Instant createdAt,
        // COMPLETED est le seul statut terminal, mais il recouvre quatre cas très différents : devis
        // refusé par le client (quoteRefusedAt), demande déclinée par le retoucheur avant tout devis
        // (repairerDeclinedAt), demande annulée par le client (cancelledAt), ou intervention
        // réellement terminée (aucun des trois). Le front en
        // a besoin pour ne pas afficher "Intervention terminée" à tort, et pour ne pas proposer le
        // formulaire d'événement/historique sur une demande jamais honorée.
        Instant quoteRefusedAt,
        Instant repairerDeclinedAt,
        String repairerDeclineReason,
        Instant cancelledAt
) {}
