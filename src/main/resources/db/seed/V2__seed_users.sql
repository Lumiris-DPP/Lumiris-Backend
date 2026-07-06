INSERT INTO users (id, email, password_hash, role, plan_tier, subscription_status, is_verified)
VALUES
    (gen_random_uuid(), 'admin@lumiris.com',    '$2a$10$9v33uOiyJsPobz68TK7sEucgp8af0CL4hFV8TOtJKF1Qnw61.0mZa', 'ADMIN',    'PRO',   'ACTIVE', true),
    (gen_random_uuid(), 'artisan@lumiris.com',  '$2a$10$GH/44x9bPI74hY1sbPVVce6b06CI3UpnzEo9YQKDXfw4LhhlZvj5i', 'ARTISAN',  'BASIC', 'ACTIVE', true),
    (gen_random_uuid(), 'client@lumiris.com',   '$2a$10$elHH3sM7mOWufU2t3DC1KuOaiAGVAsCGke6ApxZTY7BPEHG7I4Ju.', 'CLIENT',   null,    'ACTIVE', true),
    (gen_random_uuid(), 'repairer@lumiris.com', '$2a$10$xEtKXiDjGpPw/QIXptaTweZk8ILfTS9n/KqmkZ/90Rvn16Cd4m5Xu', 'REPAIRER', null,    'ACTIVE', true);
