-- ==========================================================
-- V5: Creating pending signup table for email change feature
-- ==========================================================

CREATE TABLE pending_signup
(
    id UUID PRIMARY KEY,

    name VARCHAR(100) NOT NULL,

    email VARCHAR(255) NOT NULL UNIQUE,

    phone_number VARCHAR(20),

    password_hash VARCHAR(255) NOT NULL,

    otp_hash VARCHAR(64) NOT NULL,

    expires_at TIMESTAMP NOT NULL,

    attempts INTEGER NOT NULL DEFAULT 0,

    verified BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL,

    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_pending_signup_email
ON pending_signup(email);

CREATE INDEX idx_pending_signup_expiry
ON pending_signup(expires_at);