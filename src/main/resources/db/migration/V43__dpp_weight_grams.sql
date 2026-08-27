-- Poids du vêtement, facultatif. Sert aux sous-scores carbone et eau de l'axe Impact ;

ALTER TABLE dpp_forms
    ADD COLUMN weight_grams INTEGER
        CHECK (weight_grams IS NULL OR weight_grams BETWEEN 1 AND 50000);
