-- Normalize any legacy 'USER' rows (created before enum enforcement)
UPDATE users SET role = 'CONSUMER' WHERE role NOT IN ('ARTISAN', 'CONSUMER', 'ADMIN', 'REPAIRER');

-- Remove the DEFAULT 'USER' — role must now be provided explicitly
ALTER TABLE users ALTER COLUMN role DROP DEFAULT;

-- Enforce only valid UserRole values at DB level
ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('ARTISAN', 'CONSUMER', 'ADMIN', 'REPAIRER'));
