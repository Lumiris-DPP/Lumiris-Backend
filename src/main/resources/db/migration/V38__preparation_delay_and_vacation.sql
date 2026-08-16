-- LUMIRIS · Délai de préparation annoncé + mode congés.
-- Rien n'indiquait qu'une pièce était fabriquée à la commande : l'acheteur supposait un envoi sous
-- 48 h, ne voyait rien partir, s'inquiétait, écrivait — voire ouvrait un litige pendant que
-- l'atelier travaillait normalement. Et un artisan en congés continuait de vendre sans pouvoir
-- expédier, donc se faisait relancer pour une raison étrangère à sa qualité.
--
-- Les deux se ramènent à UNE donnée : la date à laquelle l'atelier s'est engagé à expédier.
--   délai effectif = preparation_days + jours restants de pause de l'atelier
--   ship_due_at    = date d'encaissement + délai effectif
-- L'affichage acheteur, la relance et l'échéancier de versement s'y branchent tous, sans qu'aucun
-- d'eux n'ait à connaître la pause.

ALTER TABLE marketplace_products
    ADD COLUMN preparation_days INTEGER NOT NULL DEFAULT 0
        CHECK (preparation_days BETWEEN 0 AND 90);

-- Congés : les pièces restent achetables, le délai annoncé est simplement allongé jusqu'à la date
-- de retour. Aucune bascule de statut produit — ARCHIVED est déjà l'état de retrait manuel de
-- l'artisan, une bascule en masse effacerait ses propres choix DRAFT/ARCHIVED.
-- Pas de CHECK (paused_until > now()) : un CHECK Postgres doit être immuable.
ALTER TABLE artisan_profiles ADD COLUMN paused_until TIMESTAMPTZ;

-- ship_due_at : promesse FIGÉE à l'encaissement. Le produit est mutable et product_id est
-- ON DELETE SET NULL : relire le délai en direct laisserait un atelier repousser après coup une
-- promesse déjà faite, et ne survivrait pas à la suppression de l'annonce.
-- ship_reminder_sent_at : la relance J+3 se re-déclenchait à CHAQUE passage quotidien du job,
-- indéfiniment, faute de marqueur. Une relance, une seule.
ALTER TABLE marketplace_orders
    ADD COLUMN ship_due_at           TIMESTAMPTZ,
    ADD COLUMN ship_reminder_sent_at TIMESTAMPTZ;

-- Commandes antérieures : la promesse implicite était « dès que possible » depuis l'achat.
UPDATE marketplace_orders SET ship_due_at = created_at
    WHERE ship_due_at IS NULL AND status <> 'PENDING';

CREATE INDEX idx_marketplace_orders_ship_due ON marketplace_orders(ship_due_at)
    WHERE status = 'PAID' AND ship_reminder_sent_at IS NULL;
