-- ==========================================================
-- V7: Creating device tokens table to send notifications
-- ==========================================================

CREATE TABLE device_tokens
(
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    token VARCHAR(512) NOT NULL UNIQUE,

    platform VARCHAR(20) NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL,

    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_device_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE
);

CREATE INDEX idx_device_tokens_user
ON device_tokens(user_id);

CREATE INDEX idx_device_tokens_active
ON device_tokens(active);