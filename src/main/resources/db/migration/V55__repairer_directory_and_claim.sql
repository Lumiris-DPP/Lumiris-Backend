-- Réseau retoucheurs : fiches importées d'un annuaire (sans compte) et réclamation de fiche.
-- Voir docs/features/reseau-retoucheurs.md.
ALTER TABLE repairer_profiles
    ADD COLUMN source        VARCHAR(20) NOT NULL DEFAULT 'SELF',
    ADD COLUMN external_ref  TEXT,
    ADD COLUMN imported_at   TIMESTAMPTZ,
    ADD COLUMN claim_token   UUID,
    ADD COLUMN claimed_at    TIMESTAMPTZ;

-- Dédup d'import PAR SOURCE (un retoucheur peut exister dans SIRENE et CMA avec des données
-- différentes ; le merge inter-sources est un problème distinct). Pas de contrainte sur `siret`
-- seul : le seed en réutilise un, et un multi-établissements peut légitimement le répéter.
CREATE UNIQUE INDEX uq_repairer_source_ref
    ON repairer_profiles (source, external_ref)
    WHERE external_ref IS NOT NULL;

CREATE UNIQUE INDEX uq_repairer_claim_token
    ON repairer_profiles (claim_token)
    WHERE claim_token IS NOT NULL;
