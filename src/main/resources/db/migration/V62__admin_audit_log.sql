-- Journal d'audit des actions admin (import annuaire, invitation, validation/rejet de fiche…).
CREATE TABLE admin_audit_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_email TEXT        NOT NULL,
    action      VARCHAR(64) NOT NULL,   -- repairer.import | repairer.invite | repairer.verify | repairer.reject
    target_type VARCHAR(32) NOT NULL,   -- repairer | …
    target_id   TEXT,
    detail      TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_log_target ON admin_audit_log (target_type, occurred_at DESC);
