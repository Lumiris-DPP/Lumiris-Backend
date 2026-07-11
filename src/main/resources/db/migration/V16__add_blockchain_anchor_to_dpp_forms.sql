ALTER TABLE dpp_forms
    ADD COLUMN IF NOT EXISTS blockchain_tx_hash    VARCHAR(66),
    ADD COLUMN IF NOT EXISTS blockchain_anchor_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
