-- RGPD droit à l'effacement en deux temps : suppression douce immédiate (compte désactivé,
-- sessions révoquées, données conservées et récupérables) puis anonymisation définitive par
-- AccountPurgeScheduler après la fenêtre de rétention (30 j par défaut).
ALTER TABLE users
    ADD COLUMN deleted_at    TIMESTAMPTZ,
    ADD COLUMN anonymized_at TIMESTAMPTZ;

-- Le balayage de purge ne regarde que les comptes en attente d'anonymisation.
CREATE INDEX idx_users_pending_anonymization
    ON users (deleted_at)
    WHERE deleted_at IS NOT NULL AND anonymized_at IS NULL;
