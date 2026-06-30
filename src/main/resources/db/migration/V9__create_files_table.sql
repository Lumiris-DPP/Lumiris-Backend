CREATE TABLE files (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_name       VARCHAR(255) NOT NULL,
    object_key        VARCHAR(1000) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    content_type      VARCHAR(255),
    size_bytes        BIGINT       NOT NULL,
    uploaded_by       UUID         NOT NULL REFERENCES users(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
