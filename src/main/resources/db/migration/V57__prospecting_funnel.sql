-- Suivi du tunnel de prospection : jeton porté par l'e-mail (pour le lien de clic tracké et le
-- rapprochement à la réclamation), et cadence des relances.
ALTER TABLE repairer_prospect_outreach
    ADD COLUMN token             UUID,
    ADD COLUMN contact_count     INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN last_contacted_at TIMESTAMPTZ;

CREATE INDEX idx_prospect_outreach_token ON repairer_prospect_outreach (token) WHERE token IS NOT NULL;

-- Le balayage de relance ne regarde que les prospects encore ouverts.
CREATE INDEX idx_prospect_outreach_followup
    ON repairer_prospect_outreach (last_contacted_at)
    WHERE claimed_at IS NULL AND unsubscribed_at IS NULL;
