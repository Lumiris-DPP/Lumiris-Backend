-- LUMIRIS · Étiquette d'expédition en un clic + suivi transporteur réel.
--
-- Aujourd'hui l'atelier recopie l'adresse chez le transporteur, imprime, puis revient saisir le
-- numéro de suivi à la main : trois allers-retours par colis. Et le suivi obtenu est un numéro figé,
-- jamais rafraîchi — la livraison est PRÉSUMÉE à J+7, ce qui déclenche le versement au vendeur sur
-- une supposition et fait courir la fenêtre de rétractation depuis une date inventée.
--
-- Les deux se règlent avec la même intégration : l'agrégateur qui fabrique le bordereau est aussi
-- celui qui pousse les événements du transporteur.

-- ── Adresse d'enlèvement de l'atelier ───────────────────────────────────────
-- artisan_profiles ne portait que `city` (vitrine publique). Un bordereau exige une adresse postale
-- complète côté expéditeur : sans elle, aucune étiquette n'est fabricable. Distincte de la vitrine —
-- un atelier peut exposer sa ville sans publier sa rue.
ALTER TABLE artisan_profiles
    ADD COLUMN ship_from_line1       VARCHAR(300),
    ADD COLUMN ship_from_line2       VARCHAR(300),
    ADD COLUMN ship_from_postal_code VARCHAR(20),
    ADD COLUMN ship_from_city        VARCHAR(120),
    ADD COLUMN ship_from_country     VARCHAR(2) NOT NULL DEFAULT 'FR',
    ADD COLUMN ship_from_phone       VARCHAR(40);

-- ── Poids du colis ──────────────────────────────────────────────────────────
-- Le tarif et l'éligibilité d'un transporteur se calculent au poids. 0 signifie « non renseigné » :
-- le service retombe alors sur le poids par défaut configuré, plutôt que de refuser l'étiquette.
ALTER TABLE marketplace_products
    ADD COLUMN weight_grams INTEGER NOT NULL DEFAULT 0
        CHECK (weight_grams BETWEEN 0 AND 30000);

-- ── Étiquette et suivi vivant sur la commande ───────────────────────────────
-- shipping_label_file_id : le PDF vit dans le stockage objet comme toute pièce jointe. ON DELETE
-- SET NULL — la purge d'un fichier ne doit pas emporter la commande.
-- carrier_parcel_id : identifiant du colis CHEZ l'agrégateur, seule clé de rapprochement d'un
-- webhook entrant (le numéro de suivi n'existe pas encore au moment de l'annonce du colis).
ALTER TABLE marketplace_orders
    ADD COLUMN shipping_label_file_id UUID REFERENCES files(id) ON DELETE SET NULL,
    ADD COLUMN carrier_parcel_id      VARCHAR(120),
    ADD COLUMN tracking_status        VARCHAR(24),
    ADD COLUMN tracking_status_label  VARCHAR(200),
    ADD COLUMN tracking_updated_at    TIMESTAMPTZ;

-- Rapprochement d'un webhook transporteur. Partiel : la très grande majorité des commandes n'a
-- aucun colis chez l'agrégateur (saisie manuelle, remise en main propre).
CREATE UNIQUE INDEX idx_marketplace_orders_parcel ON marketplace_orders(carrier_parcel_id)
    WHERE carrier_parcel_id IS NOT NULL;
