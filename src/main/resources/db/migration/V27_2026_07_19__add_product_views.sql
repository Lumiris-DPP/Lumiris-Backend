-- LUMIRIS-22 · Statistiques vendeur : compteur de vues par fiche produit.
-- Incrémenté à l'ouverture de la fiche côté VISION ; agrégé dans le tableau de bord ATELIER.
ALTER TABLE marketplace_products
    ADD COLUMN views BIGINT NOT NULL DEFAULT 0;
