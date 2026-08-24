-- ==========================================================
-- V15: Recurring payments + smart recurrence detection
-- ==========================================================
-- Two related features:
--   1. recurring_payments      - user-configured rules that auto-generate (or
--                                remind about) an expense on a schedule.
--   2. recurring_suggestions   - output of the nightly, deterministic (no LLM)
--                                pattern-detection job; only surfaced at >=0.90
--                                confidence.
-- See RecurringPaymentService / RecurrenceDetectionService / RecurrenceAnalyzer.

-- ---------------- RECURRING PAYMENTS (rules) ----------------
CREATE TABLE recurring_payments (
    id                        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                   UUID NOT NULL REFERENCES users(id),          -- owner of the rule
    title                     VARCHAR(200) NOT NULL,
    amount                    NUMERIC(14,2) NOT NULL,
    currency                  VARCHAR(10) NOT NULL DEFAULT 'INR',
    category_id               UUID REFERENCES categories(id),
    group_id                  UUID REFERENCES groups(id),
    paid_by                   UUID NOT NULL REFERENCES users(id),
    split_type                VARCHAR(20) NOT NULL,                        -- EQUAL / EXACT / PERCENTAGE / SHARES
    participants              JSONB NOT NULL,                              -- [{userId, shareAmount, percentage, shares}]
    frequency                 VARCHAR(20) NOT NULL,                        -- WEEKLY / BIWEEKLY / MONTHLY / YEARLY
    interval_anchor           SMALLINT NOT NULL,                           -- day-of-week (0-6) for W/BW, day-of-month (1-31) for M/Y
    start_date                DATE NOT NULL,
    end_date                  DATE,
    status                    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',       -- ACTIVE / PAUSED / ENDED
    next_occurrence_date      DATE NOT NULL,                               -- recomputed after every run/edit
    last_generated_expense_id UUID,                                        -- traceability; no FK to avoid a mutual-dependency cycle
    source                    VARCHAR(30) NOT NULL DEFAULT 'MANUAL',       -- MANUAL / SUGGESTION_ACCEPTED
    auto_create               BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_days_before      SMALLINT NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Per-user listing.
CREATE INDEX idx_recurring_payments_user_status ON recurring_payments(user_id, status);
-- Daily execution cron scan for due rules.
CREATE INDEX idx_recurring_payments_due ON recurring_payments(next_occurrence_date, status);

-- ---------------- EXPENSES <- RECURRING LINK ----------------
-- Tags an expense with the rule that generated it, so generated expenses are
-- traceable and the detector can (a) skip clusters already automated and
-- (b) avoid the circular "detect a pattern in expenses we ourselves generated".
ALTER TABLE expenses
    ADD COLUMN recurring_payment_id UUID REFERENCES recurring_payments(id);
CREATE INDEX idx_expenses_recurring_payment_id ON expenses(recurring_payment_id);

-- ---------------- RECURRING SUGGESTIONS ----------------
CREATE TABLE recurring_suggestions (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id              UUID NOT NULL REFERENCES users(id),
    cluster_key          VARCHAR(120) NOT NULL,                            -- stable hash of the matched cluster (idempotent upsert)
    suggested_title      VARCHAR(200) NOT NULL,
    suggested_amount     NUMERIC(14,2) NOT NULL,                           -- median of the cluster
    currency             VARCHAR(10) NOT NULL DEFAULT 'INR',
    category_id          UUID REFERENCES categories(id),
    group_id             UUID REFERENCES groups(id),
    suggested_frequency  VARCHAR(20) NOT NULL,
    confidence_score     NUMERIC(4,3) NOT NULL,                            -- 0.000 - 1.000
    matched_expense_ids  JSONB NOT NULL,                                   -- array of expense UUIDs, most recent first
    first_seen_date      DATE NOT NULL,
    last_seen_date       DATE NOT NULL,
    predicted_next_date  DATE NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',           -- PENDING / ACCEPTED / DISMISSED
    dismissed_at         TIMESTAMPTZ,                                      -- drives the 60-day cooldown
    dismiss_count        SMALLINT NOT NULL DEFAULT 0,                      -- caps lifetime re-surfaces at 2
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recurring_suggestions_user_cluster UNIQUE (user_id, cluster_key)
);
CREATE INDEX idx_recurring_suggestions_user_status ON recurring_suggestions(user_id, status);
