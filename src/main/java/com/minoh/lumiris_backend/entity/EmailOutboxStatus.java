package com.minoh.lumiris_backend.entity;

// PENDING couvre aussi bien "jamais tenté" que "en attente du prochain retry" (next_attempt_at
// dans le futur) — pas besoin d'un état FAILED distinct, l'historique tient dans attempts/last_error.
// DEAD est la DLQ : plus repris automatiquement, à traiter manuellement côté admin.
public enum EmailOutboxStatus {
    PENDING,
    SENT,
    DEAD
}
