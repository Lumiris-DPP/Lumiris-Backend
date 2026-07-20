-- LUMIRIS-22 · Vente directe in-app : Stripe Connect (Express), destination charges avec
-- commission plateforme (~5% prélevée à la source, jamais liée au score Iris ni au tri),
-- order/wardrobe, ajout auto de la pièce achetée à la Garde-Robe (facture + garantie).

-- ── Compte vendeur Stripe Connect (Express) de l'artisan ─────────────────────
CREATE TABLE seller_accounts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    stripe_account_id    VARCHAR(255) NOT NULL UNIQUE,
    charges_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    payouts_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Champs d'offre (mise en vente) sur le produit ────────────────────────────
ALTER TABLE marketplace_products
    ADD COLUMN shipping_cents INTEGER NOT NULL DEFAULT 0 CHECK (shipping_cents >= 0),
    ADD COLUMN return_policy  TEXT;

-- ── Commande d'achat direct in-app ───────────────────────────────────────────
-- commission_cents : part plateforme (application_fee) — traçée ici, jamais dans le tri.
CREATE TABLE marketplace_orders (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id                 UUID REFERENCES marketplace_products(id) ON DELETE SET NULL,
    dpp_form_id                UUID REFERENCES dpp_forms(id) ON DELETE SET NULL,
    buyer_user_id              UUID REFERENCES users(id) ON DELETE SET NULL,
    seller_user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    stripe_checkout_session_id VARCHAR(255) UNIQUE,
    stripe_payment_intent_id   VARCHAR(255),
    amount_total_cents         INTEGER NOT NULL DEFAULT 0,
    commission_cents           INTEGER NOT NULL DEFAULT 0,
    currency                   VARCHAR(3)  NOT NULL DEFAULT 'EUR',
    status                     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                               CHECK (status IN ('PENDING','PAID','FULFILLED','CANCELLED','REFUNDED')),
    invoice_number             VARCHAR(40),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_marketplace_orders_buyer  ON marketplace_orders(buyer_user_id);
CREATE INDEX idx_marketplace_orders_seller ON marketplace_orders(seller_user_id);

-- ── Garde-Robe : pièces possédées par l'acheteur (rattachées au passeport acheté) ──
CREATE TABLE wardrobe_items (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dpp_form_id          UUID REFERENCES dpp_forms(id) ON DELETE SET NULL,
    order_id             UUID REFERENCES marketplace_orders(id) ON DELETE SET NULL,
    warranty_description TEXT,
    invoice_number       VARCHAR(40),
    acquired_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_wardrobe_items_user ON wardrobe_items(user_id);
-- Un même acheteur ne stocke pas deux fois le même passeport via une commande donnée.
CREATE UNIQUE INDEX uq_wardrobe_user_order ON wardrobe_items(user_id, order_id) WHERE order_id IS NOT NULL;
