ALTER TABLE repair_requests
    ADD COLUMN net_cents         INTEGER,
    ADD COLUMN stripe_transfer_id TEXT,
    ADD COLUMN released_at       TIMESTAMPTZ;
