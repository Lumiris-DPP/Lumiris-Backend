ALTER TABLE artisan_profiles
    ADD COLUMN method       TEXT,
    ADD COLUMN journey      TEXT,
    ADD COLUMN specialties  TEXT[],
    ADD COLUMN links        JSONB,
    ADD COLUMN published    BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE artisan_profile_photos (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    artisan_profile_id  UUID        NOT NULL REFERENCES artisan_profiles(id) ON DELETE CASCADE,
    file_id             UUID        NOT NULL REFERENCES files(id),
    position            INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_artisan_profile_photos_profile ON artisan_profile_photos(artisan_profile_id);
