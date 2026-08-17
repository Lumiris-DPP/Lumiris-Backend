-- LUMIRIS · Garde-Robe active.
--
-- Une fois la pièce reçue, l'app n'avait plus rien à dire — alors que c'est la période la plus
-- longue de la relation, et celle où se joue le rachat. Les données existent déjà : les symboles
-- d'entretien du passeport, la garantie annoncée par l'atelier, le réseau de retoucheurs.
-- Il manquait deux choses : une DURÉE de garantie exploitable, et de quoi ne rappeler qu'une fois.

-- ── Garantie chiffrée ───────────────────────────────────────────────────────
-- warranty_description est une phrase libre (« 2 ans, garantie à vie sur les coutures »). On ne
-- déclenche pas une alerte en devinant une durée dans de la prose : l'atelier la déclare.
-- Le vocabulaire existe déjà côté scoring (`warranty.durationMonths`, cf. @lumiris/core), qui note
-- déjà >= 6 / 12 / 24 mois — cette colonne cesse simplement de le laisser à 0.
ALTER TABLE dpp_forms
    ADD COLUMN warranty_months INTEGER
        CHECK (warranty_months IS NULL OR warranty_months BETWEEN 0 AND 1200);

-- ── Échéances portées par la pièce possédée ─────────────────────────────────
-- warranty_until est FIGÉ à l'achat : l'atelier peut raccourcir la garantie de ses futures pièces,
-- pas celle déjà vendue. NULL = aucune durée déclarée, donc aucune alerte (jamais d'échéance
-- inventée sur un droit contractuel).
--
-- Les deux marqueurs sont des anti-doublons, pas des dates métier : le balayage tourne chaque jour
-- et sur chaque instance, sans eux le même rappel repartirait indéfiniment.
-- care_reminder_season retient la saison déjà couverte : le rappel d'entretien est saisonnier, donc
-- il doit revenir l'hiver suivant — un simple « déjà envoyé » l'éteindrait à vie.
ALTER TABLE wardrobe_items
    ADD COLUMN warranty_until        TIMESTAMPTZ,
    ADD COLUMN care_reminder_sent_at TIMESTAMPTZ,
    ADD COLUMN care_reminder_season  VARCHAR(16),
    ADD COLUMN warranty_alert_sent_at TIMESTAMPTZ;

-- Sélection du balayage : les pièces encore sous garantie dont l'échéance approche.
CREATE INDEX idx_wardrobe_items_warranty ON wardrobe_items(warranty_until)
    WHERE warranty_until IS NOT NULL AND warranty_alert_sent_at IS NULL;

-- Sélection du rappel d'entretien : une pièce par saison, la plus anciennement rappelée d'abord.
CREATE INDEX idx_wardrobe_items_care ON wardrobe_items(care_reminder_season, acquired_at);
