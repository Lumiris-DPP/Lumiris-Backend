CREATE TABLE artisan_profiles (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    atelier_name    VARCHAR(255),
    display_name    VARCHAR(255),
    slug            VARCHAR(255),
    city            VARCHAR(100),
    region          VARCHAR(100),
    tier            VARCHAR(50)  NOT NULL DEFAULT 'Solo',
    plus            BOOLEAN      NOT NULL DEFAULT FALSE,
    passport_limit  INTEGER      NOT NULL DEFAULT 50,
    story           TEXT,
    photo_url       TEXT,
    website_url     VARCHAR(255),
    epv_labeled     BOOLEAN      NOT NULL DEFAULT FALSE,
    ofg_labeled     BOOLEAN      NOT NULL DEFAULT FALSE,
    gots_labeled    BOOLEAN      NOT NULL DEFAULT FALSE,
    oeko_tex_labeled BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Partial unique index: slug uniqueness only enforced when set
CREATE UNIQUE INDEX artisan_profiles_slug_unique ON artisan_profiles(slug) WHERE slug IS NOT NULL;
