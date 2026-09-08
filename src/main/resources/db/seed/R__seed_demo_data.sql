-- Demo seed data — opt-in via FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
-- (voir `make fresh-seed`). Migration repeatable : s'exécute après toutes les migrations
-- versionnées et cible donc toujours le schéma final. Toutes les insertions sont
-- idempotentes (ON CONFLICT / NOT EXISTS) pour tolérer une base déjà peuplée.

-- ============================================================================
-- Comptes de démo (mot de passe = <rôle>123 : admin123, artisan123, …)
-- ============================================================================
INSERT INTO users (email, password_hash, role, name, is_verified)
VALUES
    ('admin@lumiris.com',    '$2a$10$9v33uOiyJsPobz68TK7sEucgp8af0CL4hFV8TOtJKF1Qnw61.0mZa', 'ADMIN',    'Admin Lumiris',   true),
    ('artisan@lumiris.com',  '$2a$10$GH/44x9bPI74hY1sbPVVce6b06CI3UpnzEo9YQKDXfw4LhhlZvj5i', 'ARTISAN',  'Artisan Démo',    true),
    ('client@lumiris.com',   '$2a$10$elHH3sM7mOWufU2t3DC1KuOaiAGVAsCGke6ApxZTY7BPEHG7I4Ju.', 'CONSUMER', 'Client Démo',     true),
    ('repairer@lumiris.com', '$2a$10$xEtKXiDjGpPw/QIXptaTweZk8ILfTS9n/KqmkZ/90Rvn16Cd4m5Xu', 'REPAIRER', 'Réparateur Démo', true)
ON CONFLICT (email) DO NOTHING;

-- ============================================================================
-- Profil artisan : KYB complet. L'auth-guard de l'app client ne fait confiance
-- à `status` que si `declaration_signed` ET `kyb.termsAcceptedAt` sont posés
-- (sinon → redirection /onboarding), donc on seed siret + déclaration signée
-- + dossier KYB accepté + VERIFIED. DO UPDATE (et non DO NOTHING) pour que le
-- profil converge vers cet état si le seed évolue et re-tourne sur une base
-- déjà peuplée.
-- ============================================================================
INSERT INTO artisan_profiles (user_id, display_name, atelier_name, slug, city, region, tier, passport_limit,
                              status, siret, company_name, naf_code, declaration_signed, signature_timestamp, joined_at,
                              published, specialties, story, kyb_terms_accepted_at, kyb_status)
SELECT id, 'Artisan Démo', 'Atelier Démo', 'atelier-demo', 'Paris', 'Île-de-France', 'Solo', 50,
       'VERIFIED', '73282932000074', 'Atelier Démo SARL', '14.13Z', true, NOW(), NOW(),
       true, ARRAY['couture', 'maroquinerie'], 'Atelier de démonstration LUMIRIS, spécialisé en couture et petite maroquinerie.',
       NOW(), 'VALIDATED'
FROM users WHERE email = 'artisan@lumiris.com'
ON CONFLICT (user_id) DO UPDATE SET
    status = EXCLUDED.status,
    siret = EXCLUDED.siret,
    company_name = EXCLUDED.company_name,
    naf_code = EXCLUDED.naf_code,
    declaration_signed = EXCLUDED.declaration_signed,
    signature_timestamp = EXCLUDED.signature_timestamp,
    published = EXCLUDED.published,
    specialties = EXCLUDED.specialties,
    story = EXCLUDED.story,
    kyb_terms_accepted_at = EXCLUDED.kyb_terms_accepted_at,
    kyb_status = EXCLUDED.kyb_status;

-- ============================================================================
-- Profil retoucheur démo : KYB simplifié (VERIFIED direct, pas de déclaration),
-- mais l'auth-guard exige quand même `kyb.termsAcceptedAt` (sinon →
-- /onboarding), donc on le seed aussi.
-- ============================================================================
INSERT INTO repairer_profiles (user_id, display_name, company_name, status, specialties, zones, schedule,
                                address, city, region, siret, location, kyb_terms_accepted_at, kyb_status)
SELECT id, 'Réparateur Démo', 'Atelier Réparation Démo', 'VERIFIED',
       ARRAY['couture', 'cordonnerie'], ARRAY['Paris'], 'Lun-Ven 9h-18h',
       '1 place de la République', 'Paris', 'Île-de-France', '73282932000074',
       ST_SetSRID(ST_MakePoint(2.3631, 48.8674), 4326), NOW(), 'VALIDATED'
FROM users WHERE email = 'repairer@lumiris.com'
ON CONFLICT (user_id) DO UPDATE SET
    status = EXCLUDED.status,
    specialties = EXCLUDED.specialties,
    zones = EXCLUDED.zones,
    location = EXCLUDED.location,
    kyb_terms_accepted_at = EXCLUDED.kyb_terms_accepted_at,
    kyb_status = EXCLUDED.kyb_status;

-- ============================================================================
-- Échantillon annuaire CMA Île-de-France (fiches non réclamées, sans compte
-- utilisateur — un retoucheur peut s'inscrire séparément). Coordonnées
-- approximatives. Identifiées par company_name pour l'idempotence.
-- ============================================================================
INSERT INTO repairer_profiles (display_name, company_name, status, specialties, zones, schedule, address, city, region, location)
SELECT v.display_name, v.company_name, 'VERIFIED', v.specialties, v.zones, v.schedule, v.address, v.city, 'Île-de-France',
       ST_SetSRID(ST_MakePoint(v.lng, v.lat), 4326)
FROM (VALUES
    ('Atelier Cordonnerie du Marais', 'Cordonnerie du Marais SARL', ARRAY['cordonnerie', 'maroquinerie'], ARRAY['Paris 3e', 'Paris 4e'], 'Lun-Sam 9h-19h', '12 rue des Rosiers', 'Paris', 48.8571, 2.3617),
    ('Retouche Express Bastille', 'Retouche Express Bastille EURL', ARRAY['couture', 'retouche textile'], ARRAY['Paris 11e', 'Paris 12e'], 'Mar-Sam 10h-18h30', '25 rue de la Roquette', 'Paris', 48.8552, 2.3717),
    ('Cordonnier Montreuil', 'Cordonnerie Montreuilloise', ARRAY['cordonnerie'], ARRAY['Montreuil', 'Bagnolet'], 'Lun-Ven 9h-18h', '4 rue de Paris', 'Montreuil', 48.8638, 2.4432),
    ('Atelier Couture Boulogne', 'Atelier Couture Boulogne', ARRAY['couture', 'maroquinerie'], ARRAY['Boulogne-Billancourt', 'Issy-les-Moulineaux'], 'Lun-Sam 9h30-19h', '18 avenue Jean Jaurès', 'Boulogne-Billancourt', 48.8352, 2.2410),
    ('Réparation Textile Saint-Denis', 'Réparation Textile 93', ARRAY['couture', 'retouche textile', 'cordonnerie'], ARRAY['Saint-Denis', 'Aubervilliers'], 'Mar-Sam 9h-18h', '7 rue de la République', 'Saint-Denis', 48.9362, 2.3574)
) AS v(display_name, company_name, specialties, zones, schedule, address, city, lat, lng)
WHERE NOT EXISTS (
    SELECT 1 FROM repairer_profiles rp WHERE rp.company_name = v.company_name AND rp.user_id IS NULL
);

-- ============================================================================
-- Avis de démo — la fonctionnalité (POST /v1/repairers/{id}/reviews, public)
-- n'avait aucune donnée de démo, la rendant invisible en local. Identifiés par
-- (repairer, reviewer_name) pour l'idempotence.
-- ============================================================================
INSERT INTO repairer_reviews (repairer_profile_id, rating, comment, reviewer_name)
SELECT rp.id, v.rating, v.comment, v.reviewer_name
FROM repairer_profiles rp
JOIN (VALUES
    ('Atelier Réparation Démo', 5, 'Travail impeccable, mon pantalon est comme neuf.', 'Camille D.'),
    ('Atelier Réparation Démo', 4, 'Bon accueil, un peu d''attente mais le résultat est là.', 'Yanis B.'),
    ('Cordonnerie du Marais SARL', 5, 'Ressemelage parfait, prix honnête.', 'Sophie L.'),
    ('Cordonnerie du Marais SARL', 4, 'Très professionnel, je recommande.', 'Marc T.'),
    ('Retouche Express Bastille EURL', 5, 'Retouche rapide et soignée, merci !', 'Nadia K.')
) AS v(company_name, rating, comment, reviewer_name) ON v.company_name = rp.company_name
WHERE NOT EXISTS (
    SELECT 1 FROM repairer_reviews rr WHERE rr.repairer_profile_id = rp.id AND rr.reviewer_name = v.reviewer_name
);
