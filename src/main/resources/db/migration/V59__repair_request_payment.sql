-- Paiement du devis de retouche : encaissé sur le compte plateforme quand le client accepte,
-- remboursable tant que l'intervention n'a pas démarré. Voir docs/features/reseau-retoucheurs.md.
ALTER TABLE repair_requests
    ADD COLUMN stripe_payment_intent_id VARCHAR(255),
    ADD COLUMN paid_at                  TIMESTAMPTZ;

CREATE INDEX idx_repair_requests_payment_intent
    ON repair_requests (stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;
