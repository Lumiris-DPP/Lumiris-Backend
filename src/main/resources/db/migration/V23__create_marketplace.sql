-- LUMIRIS-9 · Marketplace : catalogue artisan, moteur de suggestions, log de décision auditable
-- et tracking d'affiliation. Le tri des suggestions/recherche ne dépend JAMAIS de la commission :
-- les logs de décision (append-only) tracent l'ordre retenu pour un audit indépendant.

-- Statut d'un produit du catalogue artisan : VARCHAR + CHECK (même convention que
-- artisan_profiles.status et subscriptions.plan_tier). On évite un type ENUM PG nommé,
-- dont le cast Hibernate diffère selon le style de requête (littéral JPQL vs paramètre).

-- Les tables d'audit (log de décision, clics d'affiliation) sont append-only :
-- toute UPDATE/DELETE/TRUNCATE est refusée pour garantir l'intégrité de la piste d'audit.
CREATE OR REPLACE FUNCTION fn_marketplace_append_only()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Marketplace audit records are append-only and cannot be modified or deleted';
END;
$$;

-- ── Catalogue produit côté artisan ──────────────────────────────────────────
-- Un produit référence optionnellement le DPP de la pièce : son score Iris (via
-- dpp_iris_scores) est la SEULE grandeur comparable utilisée par le tri "score".
CREATE TABLE marketplace_products (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artisan_profile_id UUID NOT NULL REFERENCES artisan_profiles(id) ON DELETE CASCADE,
    dpp_form_id        UUID REFERENCES dpp_forms(id) ON DELETE SET NULL,
    name               TEXT NOT NULL,
    description        TEXT,
    category           TEXT,
    material           TEXT,
    origin_country     TEXT,
    price_cents        INTEGER NOT NULL DEFAULT 0 CHECK (price_cents >= 0),
    currency           VARCHAR(3) NOT NULL DEFAULT 'EUR',
    stock              INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    external_order_url TEXT,
    photo_url          TEXT,
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_marketplace_products_artisan  ON marketplace_products(artisan_profile_id);
CREATE INDEX idx_marketplace_products_dpp      ON marketplace_products(dpp_form_id);
CREATE INDEX idx_marketplace_products_status   ON marketplace_products(status);
CREATE INDEX idx_marketplace_products_category ON marketplace_products(category);

-- ── Log de décision auditable (tri suggestions / recherche) ─────────────────
-- request : entrée du tri (catégorie/score/filtres). result : ordre retenu avec
-- le rang, le score Iris et le statut ATELIER+ de chaque produit + la raison.
-- commission_considered est TOUJOURS false : le tri est indépendant de la commission.
CREATE TABLE marketplace_decision_logs (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context               TEXT NOT NULL,          -- SUGGEST | SEARCH
    sort_key              TEXT NOT NULL,          -- clé de tri retenue (ex. IRIS_THEN_ATELIER_PLUS)
    request               JSONB NOT NULL,
    result                JSONB NOT NULL,
    commission_considered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_marketplace_decision_logs_context ON marketplace_decision_logs(context);
CREATE INDEX idx_marketplace_decision_logs_created ON marketplace_decision_logs(created_at);

CREATE TRIGGER trg_marketplace_decision_logs_append_only
    BEFORE UPDATE OR DELETE ON marketplace_decision_logs
    FOR EACH ROW EXECUTE FUNCTION fn_marketplace_append_only();

-- TRUNCATE ne déclenche pas les triggers FOR EACH ROW : on le bloque explicitement (statement-level).
CREATE TRIGGER trg_marketplace_decision_logs_no_truncate
    BEFORE TRUNCATE ON marketplace_decision_logs
    FOR EACH STATEMENT EXECUTE FUNCTION fn_marketplace_append_only();

-- ── Tracking des clics d'affiliation externe ────────────────────────────────
CREATE TABLE affiliate_clicks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID,
    source          TEXT NOT NULL,               -- shop | scan-suggest | passport ...
    target_url      TEXT,
    dpp_public_code TEXT,
    referrer        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_affiliate_clicks_product ON affiliate_clicks(product_id);
CREATE INDEX idx_affiliate_clicks_created ON affiliate_clicks(created_at);

CREATE TRIGGER trg_affiliate_clicks_append_only
    BEFORE UPDATE OR DELETE ON affiliate_clicks
    FOR EACH ROW EXECUTE FUNCTION fn_marketplace_append_only();

CREATE TRIGGER trg_affiliate_clicks_no_truncate
    BEFORE TRUNCATE ON affiliate_clicks
    FOR EACH STATEMENT EXECUTE FUNCTION fn_marketplace_append_only();
