-- Synchronisation des objets ajoutés par l'utilisateur (manuel ou DPP).
-- Les pièces issues d'un achat restent pilotées par le cycle de vie de la commande.
ALTER TABLE wardrobe_items
    ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'PURCHASE',
    ADD COLUMN client_key VARCHAR(255),
    ADD COLUMN kind VARCHAR(32),
    ADD COLUMN payload JSONB;

ALTER TABLE wardrobe_items
    ADD CONSTRAINT ck_wardrobe_origin CHECK (origin IN ('PURCHASE', 'USER')),
    ADD CONSTRAINT ck_wardrobe_user_item_shape CHECK (
        (origin = 'PURCHASE' AND client_key IS NULL AND kind IS NULL)
        OR
        (origin = 'USER' AND client_key IS NOT NULL AND kind IS NOT NULL AND payload IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_wardrobe_user_client_key
    ON wardrobe_items(user_id, client_key)
    WHERE origin = 'USER';

