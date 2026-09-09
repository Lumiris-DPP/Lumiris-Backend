-- Métrique « taux d'acceptation » : distinguer un devis refusé d'un devis simplement terminé.
ALTER TABLE repair_requests ADD COLUMN quote_refused_at TIMESTAMPTZ;

-- Carte de couverture : chaque recherche géolocalisée sans résultat est un signal de demande
-- non satisfaite, à cibler par la prospection.
CREATE TABLE repairer_coverage_gap (
    id          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    specialty   TEXT,
    occurred_at TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coverage_gap_occurred_at ON repairer_coverage_gap (occurred_at);
