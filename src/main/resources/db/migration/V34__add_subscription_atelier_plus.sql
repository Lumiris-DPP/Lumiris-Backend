-- ATELIER+ est une OPTION (add-on) qui s'ajoute à un abonnement ATELIER de base (2e ligne
-- d'abonnement Stripe). On matérialise sa présence par un booléen synchronisé depuis Stripe,
-- pour que le tri marketplace / le badge / l'accès Analytics reposent sur l'état RÉEL de l'add-on
-- (et non sur un plan_tier == ATELIER_PLUS, qui n'existait jamais puisque non souscriptible seul).
ALTER TABLE subscriptions
    ADD COLUMN atelier_plus BOOLEAN NOT NULL DEFAULT FALSE;
