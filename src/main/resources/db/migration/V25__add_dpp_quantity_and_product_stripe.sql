-- LUMIRIS-9 / Vente directe in-app : nombre d'exemplaires produits par DPP + lien Stripe (produit/prix)
-- sur le produit marketplace, pour la conversion DPP → article vendable in-app.

-- Combien d'exemplaires l'artisan compte produire pour ce passeport (sert de stock initial à la conversion).
ALTER TABLE dpp_forms ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 1);

-- Un produit marketplace référence AU PLUS un produit/prix Stripe (mode=payment). Créé une seule fois
-- (dédup idempotente côté service) : on ne crée jamais deux produits Stripe pour la même annonce.
ALTER TABLE marketplace_products
    ADD COLUMN stripe_product_id VARCHAR(255),
    ADD COLUMN stripe_price_id   VARCHAR(255);

-- Modèle « 1 produit marketplace = 1 DPP » : une seule annonce par passeport (empêche les conversions en double).
CREATE UNIQUE INDEX uq_marketplace_products_dpp
    ON marketplace_products (dpp_form_id) WHERE dpp_form_id IS NOT NULL;
