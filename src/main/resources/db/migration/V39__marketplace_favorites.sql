-- LUMIRIS · Favoris acheteur + état des alertes (rupture imminente, baisse de prix).
-- Sur des pièces uniques à prix artisanal, la décision n'est presque jamais prise au premier
-- passage — et il n'existait aucun moyen de revenir à une pièce vue la veille, ni d'être prévenu
-- qu'elle part.
--
-- Le favori n'est pas une préférence d'affichage (comme le panier, local) mais un ABONNEMENT : le
-- serveur doit pouvoir répondre « qui suit cette pièce » au moment où le stock ou le prix bouge, et
-- la livraison passe par notifications.user_id + email. D'où une table — et pas de favori anonyme,
-- qui ne délivrerait aucune alerte.

CREATE TABLE marketplace_favorites (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,

    -- Prix de RÉFÉRENCE propre à ce favori : celui que l'acheteur a vu la dernière fois qu'on lui a
    -- parlé de la pièce. Par favori et non par produit — deux acheteurs ayant ajouté la pièce à des
    -- prix différents n'ont pas la même baisse. Une HAUSSE ne déplace jamais ce repère, sinon un
    -- vendeur fabriquerait une « baisse » en montant puis redescendant son prix.
    last_price_cents      INTEGER NOT NULL CHECK (last_price_cents >= 0),

    -- Détecteur de front pour « il n'en reste qu'un » : posé à l'envoi, remis à NULL dès que le
    -- stock repasse au-dessus du seuil (remboursement, panier abandonné). Sans lui, un stock qui
    -- oscille renotifierait le même acheteur à chaque balayage.
    low_stock_notified_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_marketplace_favorites_user_product UNIQUE (user_id, product_id)
);

-- Liste « Mes favoris » : un seul index couvre le filtre et l'ordre d'affichage.
CREATE INDEX idx_marketplace_favorites_user ON marketplace_favorites(user_id, created_at DESC);
-- Balayage des alertes : on part du produit dont le stock ou le prix a bougé vers ses suiveurs.
CREATE INDEX idx_marketplace_favorites_product ON marketplace_favorites(product_id);
