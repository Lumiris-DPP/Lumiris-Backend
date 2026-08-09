-- LUMIRIS-24 · Cycle de vie complet d'une commande marketplace : expédition, retours, litiges,
-- remboursements, notifications. Le rail logistique vit dans `status` ; le litige, orthogonal,
-- dans `dispute_status` (une commande expédiée peut être en litige sans quitter son état).

-- ── Rail logistique élargi ───────────────────────────────────────────────────
-- FULFILLED (ancien libellé fourre-tout « livrée/remise ») devient DELIVERED, qui a désormais un
-- sens précis : la fenêtre de retour court à partir de là.
ALTER TABLE marketplace_orders DROP CONSTRAINT IF EXISTS marketplace_orders_status_check;
UPDATE marketplace_orders SET status = 'DELIVERED' WHERE status = 'FULFILLED';
ALTER TABLE marketplace_orders
    ADD CONSTRAINT marketplace_orders_status_check CHECK (status IN (
        'PENDING','PAID','SHIPPED','DELIVERED','COMPLETED',
        'RETURN_REQUESTED','RETURN_APPROVED','RETURN_REFUSED','RETURN_RECEIVED',
        'REFUNDED','CANCELLED'));

ALTER TABLE marketplace_orders
    -- Quantité de la ligne : le remboursement et la remise en stock doivent la connaître.
    ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 1),

    -- Adresse de livraison saisie au checkout — sans elle le vendeur ne peut pas expédier.
    ADD COLUMN ship_to_name        VARCHAR(200),
    ADD COLUMN ship_to_line1       VARCHAR(300),
    ADD COLUMN ship_to_line2       VARCHAR(300),
    ADD COLUMN ship_to_postal_code VARCHAR(20),
    ADD COLUMN ship_to_city        VARCHAR(120),
    ADD COLUMN ship_to_country     VARCHAR(2) DEFAULT 'FR',
    ADD COLUMN ship_to_phone       VARCHAR(40),

    ADD COLUMN carrier         VARCHAR(80),
    ADD COLUMN tracking_number VARCHAR(120),
    ADD COLUMN tracking_url    VARCHAR(500),
    ADD COLUMN shipped_at      TIMESTAMPTZ,
    ADD COLUMN delivered_at    TIMESTAMPTZ,
    ADD COLUMN completed_at    TIMESTAMPTZ,

    ADD COLUMN return_deadline      TIMESTAMPTZ,
    ADD COLUMN return_requested_at  TIMESTAMPTZ,
    ADD COLUMN return_reason        TEXT,
    ADD COLUMN return_decided_at    TIMESTAMPTZ,
    ADD COLUMN return_decision_note TEXT,
    ADD COLUMN return_received_at   TIMESTAMPTZ,

    ADD COLUMN stripe_refund_id            VARCHAR(255),
    ADD COLUMN refunded_cents              INTEGER NOT NULL DEFAULT 0 CHECK (refunded_cents >= 0),
    ADD COLUMN refunded_at                 TIMESTAMPTZ,
    ADD COLUMN refund_reason               TEXT,
    ADD COLUMN stripe_transfer_reversal_id VARCHAR(255),

    ADD COLUMN dispute_status     VARCHAR(16) NOT NULL DEFAULT 'NONE'
                                  CHECK (dispute_status IN ('NONE','OPEN','RESOLVED','REJECTED')),
    ADD COLUMN dispute_opened_at  TIMESTAMPTZ,
    ADD COLUMN dispute_reason     TEXT,
    ADD COLUMN dispute_resolution TEXT,
    ADD COLUMN dispute_closed_at  TIMESTAMPTZ;

-- Le tableau de bord vendeur ouvre systématiquement sur « à expédier » et « litiges » : ces deux
-- filtres portent tout le trafic de l'écran.
CREATE INDEX idx_marketplace_orders_seller_status ON marketplace_orders(seller_user_id, status);
CREATE INDEX idx_marketplace_orders_dispute ON marketplace_orders(dispute_status)
    WHERE dispute_status = 'OPEN';
-- Balayage du job de clôture : ne retient que les commandes encore susceptibles d'évoluer seules.
CREATE INDEX idx_marketplace_orders_pending_close ON marketplace_orders(status, shipped_at, delivered_at)
    WHERE status IN ('SHIPPED','DELIVERED');

-- ── Journal des transitions : timeline acheteur + piste d'audit des litiges ───
CREATE TABLE marketplace_order_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- RESTRICT, pas CASCADE : une commande dotée d'un journal d'audit ne se supprime pas. Le cycle
    -- de vie l'annule ou la clôture, il ne l'efface jamais — et un CASCADE se heurterait de toute
    -- façon au trigger append-only ci-dessous.
    order_id      UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE RESTRICT,
    type          VARCHAR(32) NOT NULL,
    actor_type    VARCHAR(16) NOT NULL CHECK (actor_type IN ('BUYER','SELLER','PLATFORM','SYSTEM')),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    message       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_order_events_order ON marketplace_order_events(order_id, created_at);

-- Append-only : un événement d'audit ne se corrige ni ne s'efface (même garantie que
-- marketplace_decision_logs).
--
-- Une seule mutation reste permise : détacher l'acteur (actor_user_id → NULL), ce que déclenche
-- la suppression d'un compte. Sans cette exception, le droit à l'effacement rendrait tout compte
-- ayant expédié ou commandé une pièce indéfiniment indestructible. Le fait consigné (quoi, quand,
-- par quel rôle) survit ; seul le lien nominatif disparaît.
CREATE OR REPLACE FUNCTION reject_order_event_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE'
        AND NEW.actor_user_id IS NULL
        AND NEW.id = OLD.id
        AND NEW.order_id = OLD.order_id
        AND NEW.type = OLD.type
        AND NEW.actor_type = OLD.actor_type
        AND NEW.message IS NOT DISTINCT FROM OLD.message
        AND NEW.created_at = OLD.created_at
    THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'marketplace_order_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_events_immutable
    BEFORE UPDATE OR DELETE ON marketplace_order_events
    FOR EACH ROW EXECUTE FUNCTION reject_order_event_mutation();

-- Preuves jointes à un évènement : photo d'un article abîmé, étiquette de retour, capture d'un
-- suivi transporteur. Un litige tranché sur du texte seul n'est pas arbitrable.
CREATE TABLE marketplace_order_event_files (
    event_id UUID NOT NULL REFERENCES marketplace_order_events(id) ON DELETE RESTRICT,
    file_id  UUID NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
    PRIMARY KEY (event_id, file_id)
);

-- `files.uploaded_by` était NOT NULL sans règle de suppression : tout compte ayant téléversé un
-- fichier — désormais tout acheteur joignant une photo à un litige — devenait indestructible, ce
-- qui bloque le droit à l'effacement. Le fichier survit à son auteur (un document de passeport ou
-- une preuve de litige doit rester consultable) ; seul le lien nominatif tombe.
ALTER TABLE files ALTER COLUMN uploaded_by DROP NOT NULL;
ALTER TABLE files DROP CONSTRAINT files_uploaded_by_fkey;
ALTER TABLE files ADD CONSTRAINT files_uploaded_by_fkey
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL;

-- ── Notifications in-app (acheteur ET vendeur) ───────────────────────────────
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(32) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    href       VARCHAR(500),
    order_id   UUID REFERENCES marketplace_orders(id) ON DELETE CASCADE,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE read_at IS NULL;
