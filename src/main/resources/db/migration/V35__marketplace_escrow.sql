-- LUMIRIS · Escrow marketplace — bascule "destination charge" → "separate charges & transfers".
-- Le paiement est désormais encaissé sur le compte plateforme (fonds RETENUS), puis reversé au
-- vendeur par un Transfer Stripe déclenché à l'expédition (release). net_cents = montant reversé
-- au vendeur (brut de la ligne - commission plateforme). transfer_group relie charge et transferts.
ALTER TABLE marketplace_orders
    ADD COLUMN net_cents          INTEGER NOT NULL DEFAULT 0 CHECK (net_cents >= 0),
    ADD COLUMN stripe_transfer_id VARCHAR(255),
    ADD COLUMN transfer_group     VARCHAR(255),
    ADD COLUMN released_at        TIMESTAMPTZ;

CREATE INDEX idx_marketplace_orders_transfer_group ON marketplace_orders(transfer_group);
