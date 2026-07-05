ALTER TABLE dpp_forms ADD COLUMN public_code VARCHAR(8);

UPDATE dpp_forms
SET public_code = UPPER(SUBSTRING(MD5(RANDOM()::TEXT || id::TEXT), 1, 8))
WHERE public_code IS NULL;

ALTER TABLE dpp_forms ALTER COLUMN public_code SET NOT NULL;

CREATE UNIQUE INDEX idx_dpp_forms_public_code ON dpp_forms(public_code);
