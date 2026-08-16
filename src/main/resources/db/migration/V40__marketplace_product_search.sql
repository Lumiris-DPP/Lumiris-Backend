-- LUMIRIS · Recherche plein texte du catalogue (nom, matière, description).
-- searchPublished ne filtrait que catégorie, matière et origine, en égalité exacte : un acheteur
-- qui cherche « veste en lin » n'avait aucun champ où le taper.
-- Le classement textuel est versé au MÊME marketplace_decision_logs que le tri existant :
-- l'invariant tient — le classement ne dépend jamais de la commission.

CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() est déclarée STABLE (son dictionnaire est rechargeable à chaud), or une colonne
-- GENERATED et un index GIN exigent une expression IMMUTABLE. On fige donc le dictionnaire via la
-- forme à deux arguments, enveloppée dans une fonction IMMUTABLE.
-- Contrepartie assumée : recharger le dictionnaire `unaccent` impose un
-- REINDEX INDEX idx_marketplace_products_search.
CREATE OR REPLACE FUNCTION lumiris_unaccent(text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

-- La configuration `french` ne retire pas les diacritiques : to_tsvector('french','vêtement') donne
-- 'vêt' quand websearch_to_tsquery('french','vetement') donne 'vetement' — aucun match. Un acheteur
-- au clavier de téléphone tape « brode », « chene », « ecru ». On désaccentue des DEUX côtés.
--
-- Poids : le nom identifie la pièce (A), la matière la qualifie (B, et c'est déjà un filtre exact —
-- texte et filtre disent donc la même chose), la description est de la prose où un mot peut
-- apparaître incidemment (C).
--
-- coalesce() est À L'INTÉRIEUR de lumiris_unaccent : la fonction est STRICT, un NULL propagerait un
-- vecteur NULL et rendrait la pièce définitivement introuvable, sans la moindre erreur.
ALTER TABLE marketplace_products
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('french', lumiris_unaccent(coalesce(name,        ''))), 'A') ||
        setweight(to_tsvector('french', lumiris_unaccent(coalesce(material,    ''))), 'B') ||
        setweight(to_tsvector('french', lumiris_unaccent(coalesce(description, ''))), 'C')
    ) STORED;

CREATE INDEX idx_marketplace_products_search ON marketplace_products USING GIN (search_vector);
