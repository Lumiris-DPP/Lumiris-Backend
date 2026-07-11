ALTER TABLE dpp_forms ADD COLUMN IF NOT EXISTS data_hash VARCHAR(64);
UPDATE dpp_forms SET data_hash = '' WHERE data_hash IS NULL;
ALTER TABLE dpp_forms ALTER COLUMN data_hash SET NOT NULL;
