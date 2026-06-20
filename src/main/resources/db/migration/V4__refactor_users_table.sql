-- Rename CLIENT role to CONSUMER to match frontend UserRole type
UPDATE users SET role = 'CONSUMER' WHERE role = 'CLIENT';

-- Add new profile fields
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS name         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS avatar_url   TEXT,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

-- Remove billing columns (moved to subscriptions table later)
ALTER TABLE users
    DROP COLUMN IF EXISTS plan_tier,
    DROP COLUMN IF EXISTS subscription_status;
