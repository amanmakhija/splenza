-- ==========================================================
-- V4: Add subscription tier to users (rate limiting + future paid features)
-- ==========================================================

ALTER TABLE users ADD COLUMN subscription_tier VARCHAR(20) NOT NULL DEFAULT 'FREE';