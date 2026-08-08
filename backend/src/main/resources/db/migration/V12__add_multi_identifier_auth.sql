-- Adds support for email, phone (OTP), and OAuth as independent, verifiable
-- login methods. `users.email`/`users.phoneNumber` are kept as denormalized
-- "primary contact" columns (lower migration risk - a huge amount of
-- existing code reads user.getEmail() directly) but are no longer the
-- source of truth for "is this identifier verified/usable to log in" -
-- user_identifiers is.

-- users.email was NOT NULL - phone-only signups won't have one.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
-- Existing unique constraints on email/phone_number are left as-is:
-- Postgres unique constraints permit multiple NULLs, so this doesn't block
-- more than one phone-only or email-only user from coexisting.

CREATE TABLE user_identifiers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(10) NOT NULL,
    value VARCHAR(255) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_identifiers_user_id ON user_identifiers(user_id);
CREATE INDEX idx_user_identifiers_type_value ON user_identifiers(type, value);

-- "Only one verified owner per (type, value)" - a partial unique index,
-- since two different users ARE allowed to have the same value pending
-- unverified at once (someone fat-fingering a number that isn't theirs).
CREATE UNIQUE INDEX uq_user_identifiers_verified_type_value
    ON user_identifiers(type, value)
    WHERE verified = TRUE;

CREATE TABLE user_oauth_links (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_oauth_links_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_user_oauth_links_user_id ON user_oauth_links(user_id);

CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY,
    identifier_type VARCHAR(10) NOT NULL,
    identifier_value VARCHAR(255) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_challenges_lookup
    ON otp_challenges(identifier_type, identifier_value, purpose, consumed_at);

-- Backfill: every existing user's email was already effectively verified
-- (the current signup flow requires email-OTP verification before a
-- `users` row even exists), so it's safe to carry forward as verified.
INSERT INTO user_identifiers (id, user_id, type, value, verified, is_primary, verified_at, created_at)
SELECT gen_random_uuid(), id, 'EMAIL', lower(email), TRUE, TRUE, created_at, created_at
FROM users
WHERE email IS NOT NULL;

-- Existing phone numbers were plain free-text with no verification at all
-- (anyone could type any number in) - do NOT grandfather them in as
-- trusted. Migrate as unverified so users are prompted to verify (or
-- replace) their number next time they open the app.
INSERT INTO user_identifiers (id, user_id, type, value, verified, is_primary, created_at)
SELECT gen_random_uuid(), id, 'PHONE', phone_number, FALSE, FALSE, created_at
FROM users
WHERE phone_number IS NOT NULL;