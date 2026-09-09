-- Jeton de réclamation : lié à l'adresse invitée et daté, pour qu'un lien transféré ou périmé
-- ne serve pas à un tiers.
ALTER TABLE repairer_profiles
    ADD COLUMN claim_token_email      TEXT,
    ADD COLUMN claim_token_expires_at TIMESTAMPTZ;
