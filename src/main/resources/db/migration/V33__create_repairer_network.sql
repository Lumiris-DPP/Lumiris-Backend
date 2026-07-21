CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE repairer_profiles (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Nullable: CMA-imported directory listings have no Lumiris account until claimed/signed up.
    user_id             UUID            UNIQUE REFERENCES users(id),
    display_name        TEXT,
    siret               VARCHAR(14),
    company_name        TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    specialties         TEXT[],
    zones               TEXT[],
    schedule            TEXT,
    address             TEXT,
    city                TEXT,
    region              TEXT,
    location            GEOMETRY(Point, 4326),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_repairer_profiles_location ON repairer_profiles USING GIST (location);

CREATE TABLE repairer_reviews (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    repairer_profile_id UUID            NOT NULL REFERENCES repairer_profiles(id) ON DELETE CASCADE,
    rating              SMALLINT        NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment             TEXT,
    reviewer_name       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_repairer_reviews_repairer ON repairer_reviews(repairer_profile_id);

CREATE TABLE repair_requests (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    repairer_profile_id UUID            NOT NULL REFERENCES repairer_profiles(id) ON DELETE CASCADE,
    consumer_user_id    UUID            NOT NULL REFERENCES users(id),
    dpp_form_id         UUID            NOT NULL REFERENCES dpp_forms(id),
    message             TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    quote_amount_cents  BIGINT,
    quote_description   TEXT,
    quote_submitted_at  TIMESTAMPTZ,
    appointment_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_repair_requests_repairer ON repair_requests(repairer_profile_id);
CREATE INDEX idx_repair_requests_consumer ON repair_requests(consumer_user_id);

CREATE TABLE repair_messages (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    repair_request_id   UUID            NOT NULL REFERENCES repair_requests(id) ON DELETE CASCADE,
    sender_id           UUID            NOT NULL REFERENCES users(id),
    body                TEXT            NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_repair_messages_request ON repair_messages(repair_request_id);
