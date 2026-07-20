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
