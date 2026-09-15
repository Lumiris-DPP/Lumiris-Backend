-- Une fiche importée n'a pas encore de compte : user_id doit devenir nullable (comme
-- repairer_profiles.user_id) pour accueillir les imports annuaire.
ALTER TABLE artisan_profiles ALTER COLUMN user_id DROP NOT NULL;

-- Mêmes colonnes d'import annuaire que repairer_profiles (V33/V51) : une fiche artisan peut
-- désormais aussi venir d'un import SIRENE, pas seulement d'une inscription directe.
ALTER TABLE artisan_profiles
    ADD COLUMN source         VARCHAR(20) NOT NULL DEFAULT 'SELF',
    ADD COLUMN external_ref   TEXT,
    ADD COLUMN imported_at    TIMESTAMPTZ,
    ADD COLUMN interest_count INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_artisan_source_ref ON artisan_profiles (source, external_ref)
    WHERE external_ref IS NOT NULL;
