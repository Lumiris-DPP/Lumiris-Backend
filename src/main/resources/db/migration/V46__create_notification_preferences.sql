-- Préférences de désabonnement par catégorie (email + push). Absence de ligne pour un
-- (user_id, category) donné == abonné aux deux canaux : seules les catégories explicitement
-- désactivées ont une entrée ici, pas besoin de backfill pour tous les users à la création.
CREATE TABLE notification_preferences (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category      VARCHAR(32) NOT NULL,
    email_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    push_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (user_id, category)
);
