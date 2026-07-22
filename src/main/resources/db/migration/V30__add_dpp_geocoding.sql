ALTER TABLE dpp_materials
    ADD COLUMN latitude  DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;

ALTER TABLE dpp_events
    ADD COLUMN location_city    TEXT,
    ADD COLUMN location_country TEXT,
    ADD COLUMN latitude         DOUBLE PRECISION,
    ADD COLUMN longitude        DOUBLE PRECISION;

CREATE TABLE geocode_cache (
    id               BIGSERIAL   PRIMARY KEY,
    query_normalized TEXT        NOT NULL UNIQUE,
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    provider         TEXT        NOT NULL,
    resolved_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);