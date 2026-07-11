-- Stripe customer handle on the user (created lazily when billing starts).
ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255) UNIQUE;
