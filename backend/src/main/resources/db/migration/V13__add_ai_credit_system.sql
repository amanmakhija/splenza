-- Shared AI-credit wallet (multi-feature) + Google Play Billing purchase
-- audit trail. See AiCreditService for the atomic consume/refund logic that
-- reads/writes these tables.

-- One row per (user, AI feature, "today") - each feature's own free daily
-- allowance, completely independent of every other feature's allowance.
CREATE TABLE ai_feature_daily_usage (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id),
    feature_key     VARCHAR(30) NOT NULL,
    free_used_today INT NOT NULL DEFAULT 0,
    free_reset_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ai_feature_daily_usage_user_feature UNIQUE (user_id, feature_key)
);
CREATE INDEX idx_ai_feature_daily_usage_user_id ON ai_feature_daily_usage(user_id);

-- One row per user - the shared purchased-credit pool drawn from by every AI
-- feature once its own free daily allowance is exhausted. Credits never
-- expire and are not per-feature.
CREATE TABLE ai_credit_wallets (
    user_id           UUID PRIMARY KEY REFERENCES users(id),
    purchased_balance INT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Google Play purchase verification audit trail. google_play_purchase_token
-- is unique so a retried client call can never credit the same purchase
-- twice - a purchase token can only ever be consumed once anyway.
CREATE TABLE ai_credit_purchases (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                     UUID NOT NULL REFERENCES users(id),
    package_id                  VARCHAR(50) NOT NULL,
    credits                     INT NOT NULL,
    price_in_paise              INT NOT NULL,
    currency                    VARCHAR(10) NOT NULL DEFAULT 'INR',
    google_play_product_id      VARCHAR(100) NOT NULL,
    google_play_purchase_token  VARCHAR(500) NOT NULL,
    status                      VARCHAR(20) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_credit_purchases_token UNIQUE (google_play_purchase_token)
);
CREATE INDEX idx_ai_credit_purchases_user_id ON ai_credit_purchases(user_id);

-- Audit/debugging trail of every AI feature call that successfully consumed
-- a credit. Not used for balance math (that's the two tables above) - purely
-- for support/debugging.
CREATE TABLE ai_credit_usage_log (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID NOT NULL REFERENCES users(id),
    feature_key   VARCHAR(30) NOT NULL,
    credit_source VARCHAR(10) NOT NULL,
    metadata      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_credit_usage_log_user_id ON ai_credit_usage_log(user_id);
CREATE INDEX idx_ai_credit_usage_log_created_at ON ai_credit_usage_log(created_at);