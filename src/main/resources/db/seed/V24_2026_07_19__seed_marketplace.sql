-- LUMIRIS-9 · Données de démo marketplace (opt-in : db/seed).
-- Deux artisans pour illustrer la priorité ATELIER+ à score égal :
--   • artisan@lumiris.com  → add-on ATELIER+ actif (badge + priorité au tri)
--   • artisan2@lumiris.com → sans ATELIER+ (même score que l'artisan+ sur une pièce)
-- Chaque produit référence un DPP scoré : le tri "score" s'appuie sur dpp_iris_scores.total.

-- ── Abonnement ATELIER+ actif pour l'artisan démo ───────────────────────────
INSERT INTO subscriptions (user_id, plan_tier, billing_cycle, status, current_period_end)
SELECT id, 'ATELIER_PLUS', 'MONTHLY', 'active', NOW() + INTERVAL '1 year'
FROM users WHERE email = 'artisan@lumiris.com';

-- ── Second artisan (sans ATELIER+) ──────────────────────────────────────────
-- Réutilise le hash bcrypt du compte artisan démo (mot de passe de démo partagé).
INSERT INTO users (id, email, password_hash, role, name, is_verified)
VALUES (
    gen_random_uuid(),
    'artisan2@lumiris.com',
    '$2a$10$GH/44x9bPI74hY1sbPVVce6b06CI3UpnzEo9YQKDXfw4LhhlZvj5i',
    'ARTISAN',
    'Atelier Bord de Loire',
    true
);

INSERT INTO artisan_profiles (user_id, display_name, atelier_name, slug, city, region, tier, passport_limit, joined_at)
SELECT id, 'Atelier Bord de Loire', 'Atelier Bord de Loire', 'atelier-bord-de-loire', 'Tours', 'Centre-Val de Loire', 'Solo', 50, NOW()
FROM users WHERE email = 'artisan2@lumiris.com';

-- ── DPP + score Iris — artisan démo (ATELIER+) ──────────────────────────────
-- public_code (unique) et data_hash sont NOT NULL sur dpp_forms (cf. V14/V15).
INSERT INTO dpp_forms (id, user_id, status, product_name, product_category, origin_country, public_code, data_hash)
SELECT v.id, u.id, 'VALID', v.name, v.category, v.origin, v.code, ''
FROM users u,
     (VALUES
        ('11111111-1111-1111-1111-111111110001'::uuid, 'Pull maille écru',   'sweater', 'France',   'MP000001'),
        ('11111111-1111-1111-1111-111111110002'::uuid, 'Chemise lin lavé',   'shirt',   'Portugal', 'MP000002'),
        ('11111111-1111-1111-1111-111111110003'::uuid, 'Bottines cuir tanné','shoe',    'Italie',   'MP000003')
     ) AS v(id, name, category, origin, code)
WHERE u.email = 'artisan@lumiris.com';

-- ── DPP + score Iris — second artisan (sans ATELIER+) ───────────────────────
INSERT INTO dpp_forms (id, user_id, status, product_name, product_category, origin_country, public_code, data_hash)
SELECT v.id, u.id, 'VALID', v.name, v.category, v.origin, v.code, ''
FROM users u,
     (VALUES
        ('22222222-2222-2222-2222-222222220001'::uuid, 'Veste laine bouillie',  'jacket',    'France', 'MP000004'),
        ('22222222-2222-2222-2222-222222220002'::uuid, 'Pantalon coton bio',    'trouser',   'France', 'MP000005'),
        ('22222222-2222-2222-2222-222222220003'::uuid, 'Écharpe cachemire',     'accessory', 'Népal',  'MP000006')
     ) AS v(id, name, category, origin, code)
WHERE u.email = 'artisan2@lumiris.com';

INSERT INTO dpp_iris_scores (dpp_form_id, transparency, craftsmanship, repairability, impact, total, grade) VALUES
    ('11111111-1111-1111-1111-111111110001', 34, 22, 8, 20, 84, 'A'),
    ('11111111-1111-1111-1111-111111110002', 30, 18, 6, 18, 72, 'B'),
    ('11111111-1111-1111-1111-111111110003', 26, 18, 5, 17, 66, 'B'),
    ('22222222-2222-2222-2222-222222220001', 34, 22, 8, 20, 84, 'A'),
    ('22222222-2222-2222-2222-222222220002', 22, 15, 6, 15, 58, 'C'),
    ('22222222-2222-2222-2222-222222220003', 20, 13, 4, 14, 51, 'C');

-- ── Produits publiés au catalogue ───────────────────────────────────────────
INSERT INTO marketplace_products
    (artisan_profile_id, dpp_form_id, name, description, category, material, origin_country,
     price_cents, currency, stock, external_order_url, photo_url, status)
SELECT ap.id, v.dpp_id, v.name, v.description, v.category, v.material, v.origin,
       v.price, 'EUR', v.stock, v.url, v.photo, 'PUBLISHED'
FROM artisan_profiles ap
JOIN users u ON u.id = ap.user_id,
     (VALUES
        ('11111111-1111-1111-1111-111111110001'::uuid, 'Pull maille écru', 'Pull en laine mérinos filée et tricotée en France.', 'sweater', 'wool',   'France',   18900, 6, 'https://atelier-demo.example/pull-ecru',     'https://picsum.photos/seed/lumiris-pull/600/800'),
        ('11111111-1111-1111-1111-111111110002'::uuid, 'Chemise lin lavé', 'Chemise en lin lavé, coupe droite intemporelle.',   'shirt',   'linen',  'Portugal', 12900, 9, 'https://atelier-demo.example/chemise-lin',   'https://picsum.photos/seed/lumiris-chemise/600/800'),
        ('11111111-1111-1111-1111-111111110003'::uuid, 'Bottines cuir tanné', 'Bottines en cuir à tannage végétal, cousu main.', 'shoe',   'leather','Italie',   24900, 4, 'https://atelier-demo.example/bottines-cuir', 'https://picsum.photos/seed/lumiris-bottines/600/800')
     ) AS v(dpp_id, name, description, category, material, origin, price, stock, url, photo)
WHERE u.email = 'artisan@lumiris.com';

INSERT INTO marketplace_products
    (artisan_profile_id, dpp_form_id, name, description, category, material, origin_country,
     price_cents, currency, stock, external_order_url, photo_url, status)
SELECT ap.id, v.dpp_id, v.name, v.description, v.category, v.material, v.origin,
       v.price, 'EUR', v.stock, v.url, v.photo, 'PUBLISHED'
FROM artisan_profiles ap
JOIN users u ON u.id = ap.user_id,
     (VALUES
        ('22222222-2222-2222-2222-222222220001'::uuid, 'Veste laine bouillie', 'Veste en laine bouillie doublée, chaude et durable.', 'jacket',    'wool',     'France', 29900, 3, 'https://bord-de-loire.example/veste-laine',    'https://picsum.photos/seed/lumiris-veste/600/800'),
        ('22222222-2222-2222-2222-222222220002'::uuid, 'Pantalon coton bio',   'Pantalon en coton biologique certifié GOTS.',        'trouser',   'cotton',   'France', 11900, 7, 'https://bord-de-loire.example/pantalon-coton',  'https://picsum.photos/seed/lumiris-pantalon/600/800'),
        ('22222222-2222-2222-2222-222222220003'::uuid, 'Écharpe cachemire',    'Écharpe en cachemire tissée à la main.',             'accessory', 'cashmere', 'Népal',  15900, 5, 'https://bord-de-loire.example/echarpe-cachemire','https://picsum.photos/seed/lumiris-echarpe/600/800')
     ) AS v(dpp_id, name, description, category, material, origin, price, stock, url, photo)
WHERE u.email = 'artisan2@lumiris.com';
