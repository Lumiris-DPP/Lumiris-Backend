-- Signal d'intérêt anonyme sur une fiche réparateur non réclamée (bandeau "pas encore dans le
-- réseau" côté mobile). Aucune donnée personnelle : un compteur, rien de plus — RGPD-neutre.
ALTER TABLE repairer_profiles
    ADD COLUMN interest_count INTEGER NOT NULL DEFAULT 0;
