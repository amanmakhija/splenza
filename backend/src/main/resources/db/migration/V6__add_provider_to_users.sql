-- ==========================================================
-- V6: Added provider for storing login method
-- ==========================================================

ALTER TABLE users
ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';