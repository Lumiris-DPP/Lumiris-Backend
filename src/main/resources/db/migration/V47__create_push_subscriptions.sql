-- Abonnements Web Push (un par navigateur/appareil). endpoint est la clé d'idempotence côté
-- navigateur : PushManager.subscribe() renvoie le même abonnement existant tant qu'il n'a pas
-- expiré, donc un re-enregistrement fait un upsert plutôt qu'un doublon.
CREATE TABLE push_subscriptions (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint   VARCHAR(500) NOT NULL,
    p256dh     VARCHAR(200) NOT NULL,
    auth       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions(user_id);
