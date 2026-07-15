-- ==========================================================
-- V3: Add waitlist
-- ==========================================================

CREATE TABLE waitlist (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    joined_at TIMESTAMP NOT NULL
);