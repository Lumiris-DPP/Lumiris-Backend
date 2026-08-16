-- LUMIRIS · Déclinaisons produit (taille / couleur) à stock propre + guide des mesures réelles.
-- Une annonce = un passeport = N déclinaisons. Jusqu'ici, vendre une veste en 4 tailles imposait
-- 4 annonces, 4 photos et 4 passeports — et la taille n'était même pas affichée à l'acheteur,
-- alors qu'elle est la 1re cause de retour en habillement.
--
-- Le stock quitte marketplace_products pour vivre EXCLUSIVEMENT sur la déclinaison : c'est elle
-- qu'on vend, qu'on réserve et qu'on remet en rayon. Un mode double (stock produit quand il n'y a
-- pas de déclinaison) forkerait sept fois le chemin paiement/escrow, et chaque fork qui se trompe
-- de branche survend en silence.

CREATE TABLE marketplace_product_variants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
    size_label  VARCHAR(40),
    color_label VARCHAR(40),
    color_hex   VARCHAR(7) CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    sku         VARCHAR(64),
    stock       INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    position    INTEGER NOT NULL DEFAULT 0,
    -- Verrou optimiste : l'écran catalogue artisan renvoie un payload complet à chaque bascule de
    -- visibilité. Sans version, une vente concurrente est écrasée par le stock affiché au moment
    -- où le formulaire a été ouvert — et l'annonce survend.
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_marketplace_variants_product ON marketplace_product_variants(product_id, position);

-- Une combinaison (taille, couleur) est unique par annonce. COALESCE : la déclinaison par défaut
-- (aucun axe renseigné) tombe sous la même règle — un produit ne peut en avoir qu'une.
CREATE UNIQUE INDEX uq_marketplace_variants_combo
    ON marketplace_product_variants (product_id, COALESCE(size_label, ''), COALESCE(color_label, ''));

-- Reprise de l'existant : chaque annonce reçoit UNE déclinaison par défaut qui hérite de son stock.
-- L'invariant « une annonce a au moins une déclinaison » est posé ici et tenu ensuite par le
-- service : le checkout n'a jamais deux sémantiques de stock à arbitrer.
INSERT INTO marketplace_product_variants (product_id, stock)
SELECT id, stock FROM marketplace_products;

-- Le stock d'une annonce est désormais la somme de ses déclinaisons. Le conserver en colonne
-- créerait une 2e source de vérité, périmée dans le contexte de persistance après chaque
-- décrément atomique.
ALTER TABLE marketplace_products DROP COLUMN stock;

-- Mesures relevées par l'atelier, indexées sur la TAILLE et non sur la déclinaison : un tour de
-- poitrine dépend de la taille, jamais de la couleur — le lier à la déclinaison stockerait la même
-- valeur pour M/rouge, M/bleu, M/noir et les laisserait diverger.
-- Étiquettes libres et valeurs en millimètres entiers (même discipline que les centimes) : un
-- couple de colonnes chest_cm / length_cm serait un schéma de vêtement dans une table de
-- marketplace, alors qu'un atelier vend aussi des sacs et des chaises.
CREATE TABLE marketplace_size_measurements (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
    size_label VARCHAR(40) NOT NULL,
    label      VARCHAR(60) NOT NULL,
    value_mm   INTEGER NOT NULL CHECK (value_mm > 0),
    position   INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_marketplace_size_measurements
    ON marketplace_size_measurements(product_id, size_label, label);
CREATE INDEX idx_marketplace_size_measurements_product
    ON marketplace_size_measurements(product_id, position);

-- Déclinaison vendue : SET NULL car un atelier retire légitimement une taille de son catalogue.
-- variant_label est GELÉ à la commande — sans lui, une facture et un litige « mauvaise taille
-- livrée » perdent le seul fait sur lequel ils reposent dès la disparition de la déclinaison.
ALTER TABLE marketplace_orders
    ADD COLUMN variant_id    UUID REFERENCES marketplace_product_variants(id) ON DELETE SET NULL,
    ADD COLUMN variant_label VARCHAR(120);

CREATE INDEX idx_marketplace_orders_variant ON marketplace_orders(variant_id);
