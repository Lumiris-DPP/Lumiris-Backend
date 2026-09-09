-- Prospection retoucheurs : trace des e-mails d'invitation à réclamer une fiche + liste de
-- suppression (opt-out, bounces). Voir docs/features/reseau-retoucheurs.md, phase 3.
CREATE TABLE repairer_prospect_outreach (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    repairer_profile_id UUID        NOT NULL REFERENCES repairer_profiles(id) ON DELETE CASCADE,
    email               TEXT        NOT NULL,
    sent_at             TIMESTAMPTZ,
    opened_at           TIMESTAMPTZ,
    clicked_at          TIMESTAMPTZ,
    claimed_at          TIMESTAMPTZ,
    unsubscribed_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prospect_outreach_profile ON repairer_prospect_outreach (repairer_profile_id);

CREATE TABLE email_suppression (
    email       TEXT        PRIMARY KEY,
    reason      VARCHAR(20) NOT NULL,   -- UNSUBSCRIBE | BOUNCE | COMPLAINT | MANUAL
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
