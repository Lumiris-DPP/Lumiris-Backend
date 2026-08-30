-- V48 posait le throttle directement sur dpp_forms, mais fn_dpp_forms_immutable() (V29) protège
-- les passeports publiés en réinitialisant silencieusement toute colonne hors de sa liste
-- blanche explicite (status, blockchain_tx_hash, blockchain_anchor_status, updated_at) — l'écriture
-- du throttle était donc annulée avant de persister, sans erreur. Déplace l'état hors de la ligne
-- protégée, dans sa propre table.
ALTER TABLE dpp_forms DROP COLUMN last_scan_notified_at;

CREATE TABLE dpp_scan_notification_throttle (
    dpp_form_id      UUID        PRIMARY KEY REFERENCES dpp_forms(id) ON DELETE CASCADE,
    last_notified_at TIMESTAMPTZ NOT NULL
);
