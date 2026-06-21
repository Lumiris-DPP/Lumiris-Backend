CREATE TABLE dpp_forms (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    product_name       VARCHAR(255),
    product_type       VARCHAR(50),
    internal_reference VARCHAR(60),
    retail_price       NUMERIC(10, 2),
    currency           VARCHAR(3)   NOT NULL DEFAULT 'EUR',
    status             VARCHAR(30)  NOT NULL DEFAULT 'Draft',
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP
);
