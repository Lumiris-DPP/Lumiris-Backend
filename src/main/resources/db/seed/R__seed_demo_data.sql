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
-- à `status` que si `declaration_signed` est true (sinon → redirection
-- /onboarding SIRET), donc on seed siret + déclaration signée + VERIFIED.
-- DO UPDATE (et non DO NOTHING) pour que le profil converge vers cet état si
-- le seed évolue et re-tourne sur une base déjà peuplée.
-- ============================================================================
INSERT INTO artisan_profiles (user_id, display_name, atelier_name, slug, city, region, tier, passport_limit,
                              status, siret, company_name, naf_code, declaration_signed, signature_timestamp, joined_at)
SELECT id, 'Artisan Démo', 'Atelier Démo', 'atelier-demo', 'Paris', 'Île-de-France', 'Solo', 50,
       'VERIFIED', '73282932000074', 'Atelier Démo SARL', '14.13Z', true, NOW(), NOW()
FROM users WHERE email = 'artisan@lumiris.com'
ON CONFLICT (user_id) DO UPDATE SET
    status = EXCLUDED.status,
    siret = EXCLUDED.siret,
    company_name = EXCLUDED.company_name,
    naf_code = EXCLUDED.naf_code,
    declaration_signed = EXCLUDED.declaration_signed,
    signature_timestamp = EXCLUDED.signature_timestamp;

-- ============================================================================
-- Subscription active pour l'artisan (requise par QuotaService pour créer des
-- DPP et par l'app client pour afficher les passeports).
-- `status` = statut Stripe brut en minuscules ; plan/cycle en majuscules.
-- ============================================================================
INSERT INTO subscriptions (user_id, plan_tier, billing_cycle, status, current_period_end)
SELECT id, 'ATELIER_SOLO', 'MONTHLY', 'active', NOW() + INTERVAL '30 days'
FROM users WHERE email = 'artisan@lumiris.com'
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================================
-- DPP de démo pour l'artisan. UUID fixes pour l'idempotence des tables filles.
-- `data_hash` factice (l'endpoint /verify signalera un mismatch — sans impact
-- sur les pages publiques et les apps).
-- ============================================================================
INSERT INTO dpp_forms (id, user_id, status, product_name, product_description, product_category,
                       origin_country, available_sizes, colors, care_notes, manufactured_at,
                       batch_number, gtin, sku, reach_compliant, recycled_pct,
                       warranty_description, is_repairable, end_of_life_instructions,
                       public_code, data_hash)
SELECT d.*
FROM (SELECT id FROM users WHERE email = 'artisan@lumiris.com') u
CROSS JOIN LATERAL (
    VALUES
        ('d0000000-0000-4000-8000-000000000001'::uuid, u.id, 'VALID'::dpp_status,
         'Manteau laine Grand Froid', 'Manteau en laine française tissée à l''atelier, doublure en laine recyclée.',
         'outerwear', 'France', ARRAY['S','M','L'], ARRAY['Camel','Marine'],
         'Nettoyage à sec uniquement.', '2026-03-10', 'LOT-2026-007', '3760000000011', 'MLG-001',
         true, 40::smallint, 'Garantie 5 ans, retouches offertes.', true,
         'Reprise en atelier pour recyclage de la laine.', 'SEED0001', repeat('a1', 32)),
        ('d0000000-0000-4000-8000-000000000002'::uuid, u.id, 'VALID'::dpp_status,
         'Chemise coton bio Rivage', 'Chemise en coton biologique certifié, coupe droite.',
         'top', 'Portugal', ARRAY['S','M','L','XL'], ARRAY['Blanc','Bleu ciel'],
         NULL, '2026-04-22', 'LOT-2026-019', '3760000000028', 'CCR-002',
         true, 15::smallint, 'Garantie 2 ans.', true,
         NULL, 'SEED0002', repeat('b2', 32)),
        ('d0000000-0000-4000-8000-000000000003'::uuid, u.id, 'VALID'::dpp_status,
         'Tote bag Écume', 'Tote bag en chutes de tissu.',
         'accessory', NULL, NULL, ARRAY['Écru'],
         NULL, NULL, NULL, '3760000000035', 'TBE-003',
         false, NULL, NULL, false,
         NULL, 'SEED0003', repeat('c3', 32))
) AS d(id, user_id, status, product_name, product_description, product_category,
       origin_country, available_sizes, colors, care_notes, manufactured_at,
       batch_number, gtin, sku, reach_compliant, recycled_pct,
       warranty_description, is_repairable, end_of_life_instructions, public_code, data_hash)
ON CONFLICT DO NOTHING;

-- Composition
INSERT INTO dpp_materials (dpp_form_id, fiber, percentage, origin_country)
SELECT * FROM (VALUES
    ('d0000000-0000-4000-8000-000000000001'::uuid, 'wool',     90::smallint, 'France'),
    ('d0000000-0000-4000-8000-000000000001'::uuid, 'cashmere', 10::smallint, 'France'),
    ('d0000000-0000-4000-8000-000000000002'::uuid, 'cotton',  100::smallint, 'Portugal'),
    ('d0000000-0000-4000-8000-000000000003'::uuid, 'cotton',  100::smallint, NULL)
) AS m(dpp_form_id, fiber, percentage, origin_country)
WHERE NOT EXISTS (SELECT 1 FROM dpp_materials WHERE dpp_form_id = m.dpp_form_id);

-- Entretien (codes GINETEX utilisés par les apps front)
INSERT INTO dpp_care_instructions (dpp_form_id, care_code)
VALUES
    ('d0000000-0000-4000-8000-000000000001', 'dry-clean'),
    ('d0000000-0000-4000-8000-000000000001', 'no-tumble'),
    ('d0000000-0000-4000-8000-000000000001', 'iron-low'),
    ('d0000000-0000-4000-8000-000000000002', 'wash-40'),
    ('d0000000-0000-4000-8000-000000000002', 'tumble-dry'),
    ('d0000000-0000-4000-8000-000000000002', 'iron-med')
ON CONFLICT (dpp_form_id, care_code) DO NOTHING;

-- Scores Iris (cohérents avec IrisScoreCalculator : A ≥ 80, B ≥ 65, C ≥ 50, D ≥ 35)
INSERT INTO dpp_iris_scores (dpp_form_id, transparency, craftsmanship, impact, repairability, total, grade)
VALUES
    ('d0000000-0000-4000-8000-000000000001', 85.0, 90.0,  80.0, 75.0, 84.0, 'A'),
    ('d0000000-0000-4000-8000-000000000002', 37.5, 40.0, 100.0, 70.0, 57.0, 'C'),
    ('d0000000-0000-4000-8000-000000000003', 20.0, 30.0,  40.0,  0.0, 25.5, 'E')
ON CONFLICT (dpp_form_id) DO NOTHING;

-- Historique du cycle de vie (affiché sur la page publique du DPP)
INSERT INTO dpp_events (dpp_form_id, occurred_at, description, actor_type)
SELECT * FROM (VALUES
    ('d0000000-0000-4000-8000-000000000001'::uuid, NOW() - INTERVAL '90 days',
     'Fabrication terminée à l''Atelier Démo.', 'MANUFACTURER'),
    ('d0000000-0000-4000-8000-000000000001'::uuid, NOW() - INTERVAL '60 days',
     'Mise en vente en boutique.', 'RETAILER')
) AS e(dpp_form_id, occurred_at, description, actor_type)
WHERE NOT EXISTS (SELECT 1 FROM dpp_events WHERE dpp_form_id = e.dpp_form_id);
