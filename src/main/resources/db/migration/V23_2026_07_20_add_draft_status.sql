-- New DPP lifecycle state: a draft saved by the artisan before final publication.
-- Kept alone in this migration: Postgres forbids using a new enum value in the
-- same transaction that adds it (the trigger relaxation lives in V24).
ALTER TYPE dpp_status ADD VALUE IF NOT EXISTS 'DRAFT';
