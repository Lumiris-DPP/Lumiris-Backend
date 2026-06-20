-- Seed artisan profile for the demo artisan account
INSERT INTO artisan_profiles (user_id, display_name, atelier_name, slug, city, region, tier, passport_limit, joined_at)
SELECT
    id,
    'Artisan Démo',
    'Atelier Démo',
    'atelier-demo',
    'Paris',
    'Île-de-France',
    'Solo',
    50,
    NOW()
FROM users
WHERE email = 'artisan@lumiris.com';

-- Seed demo names into users table
UPDATE users SET name = 'Admin Lumiris'   WHERE email = 'admin@lumiris.com';
UPDATE users SET name = 'Artisan Démo'    WHERE email = 'artisan@lumiris.com';
UPDATE users SET name = 'Client Démo'     WHERE email = 'client@lumiris.com';
UPDATE users SET name = 'Réparateur Démo' WHERE email = 'repairer@lumiris.com';
