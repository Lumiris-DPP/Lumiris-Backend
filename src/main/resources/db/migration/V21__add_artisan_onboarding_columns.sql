ALTER TABLE artisan_profiles
    ADD COLUMN siret               VARCHAR(14),
    ADD COLUMN status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN company_name        VARCHAR(255),
    ADD COLUMN naf_code            VARCHAR(10),
    ADD COLUMN declaration_signed  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN signature_timestamp TIMESTAMPTZ,
    ADD COLUMN signature_ip        VARCHAR(45),
    ADD COLUMN sirene_raw_data     JSONB,
    ADD COLUMN rejection_reason    TEXT;
