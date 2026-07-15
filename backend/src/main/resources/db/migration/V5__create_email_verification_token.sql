-- ==========================================================
-- V5: Add email verification token
-- ==========================================================

CREATE TABLE email_verification_token
(
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    otp_hash VARCHAR(255) NOT NULL,

    expires_at TIMESTAMP NOT NULL,

    used BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_email_verification_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_user
ON email_verification_token(user_id);

CREATE INDEX idx_email_verification_hash
ON email_verification_token(otp_hash);