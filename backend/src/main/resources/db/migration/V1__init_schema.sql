-- ==========================================================
-- V1: Core schema for Splitwise alternative (Phase 1)
-- ==========================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------------- USERS ----------------
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                VARCHAR(120)  NOT NULL,
    email               VARCHAR(180)  NOT NULL UNIQUE,
    password_hash       VARCHAR(255),               -- null if google-only signup
    google_id           VARCHAR(255)  UNIQUE,
    profile_picture_url TEXT,
    preferred_currency  VARCHAR(10)   NOT NULL DEFAULT 'INR',
    theme               VARCHAR(10)   NOT NULL DEFAULT 'SYSTEM', -- LIGHT / DARK / SYSTEM
    is_email_verified   BOOLEAN       NOT NULL DEFAULT FALSE,
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------- REFRESH TOKENS ----------------
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ---------------- PASSWORD RESET ----------------
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------- FRIEND REQUESTS ----------------
CREATE TABLE friend_requests (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / ACCEPTED / REJECTED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friend_request UNIQUE (sender_id, receiver_id),
    CONSTRAINT chk_not_self_request CHECK (sender_id <> receiver_id)
);

-- ---------------- FRIENDS (materialized, symmetric pair with user1_id < user2_id) ----------------
CREATE TABLE friends (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user1_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user2_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friend_pair UNIQUE (user1_id, user2_id),
    CONSTRAINT chk_ordered_pair CHECK (user1_id < user2_id)
);
CREATE INDEX idx_friends_user1 ON friends(user1_id);
CREATE INDEX idx_friends_user2 ON friends(user2_id);

-- ---------------- GROUPS ----------------
CREATE TABLE groups (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    image_url    TEXT,
    created_by   UUID NOT NULL REFERENCES users(id),
    is_archived  BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER', -- ADMIN / MEMBER
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at     TIMESTAMPTZ,
    CONSTRAINT uq_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX idx_group_members_group ON group_members(group_id);
CREATE INDEX idx_group_members_user ON group_members(user_id);

-- ---------------- CATEGORIES ----------------
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(60) NOT NULL UNIQUE,
    icon        VARCHAR(60),
    is_system   BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------------- EXPENSES ----------------
CREATE TABLE expenses (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id       UUID REFERENCES groups(id) ON DELETE CASCADE, -- null = non-group (friend) expense
    title          VARCHAR(200) NOT NULL,
    amount         NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(10) NOT NULL DEFAULT 'INR',
    category_id    UUID REFERENCES categories(id),
    notes          TEXT,
    expense_date   DATE NOT NULL,
    paid_by        UUID NOT NULL REFERENCES users(id),
    split_type     VARCHAR(20) NOT NULL DEFAULT 'EQUAL', -- EQUAL / EXACT / PERCENTAGE / SHARES
    created_by     UUID NOT NULL REFERENCES users(id),
    is_deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_expenses_group ON expenses(group_id);
CREATE INDEX idx_expenses_paid_by ON expenses(paid_by);
CREATE INDEX idx_expenses_date ON expenses(expense_date);

CREATE TABLE expense_participants (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    expense_id     UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id),
    share_amount   NUMERIC(14,2) NOT NULL,   -- amount this user owes for the expense
    percentage     NUMERIC(5,2),             -- used when split_type = PERCENTAGE
    shares         INT,                      -- used when split_type = SHARES
    CONSTRAINT uq_expense_participant UNIQUE (expense_id, user_id)
);
CREATE INDEX idx_expense_participants_expense ON expense_participants(expense_id);
CREATE INDEX idx_expense_participants_user ON expense_participants(user_id);

-- ---------------- RECEIPTS ----------------
CREATE TABLE receipts (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    expense_id   UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    s3_key       TEXT NOT NULL,
    file_url     TEXT NOT NULL,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------- SETTLEMENTS ----------------
CREATE TABLE settlements (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id     UUID REFERENCES groups(id) ON DELETE CASCADE, -- null = friend settlement
    paid_by      UUID NOT NULL REFERENCES users(id),
    paid_to      UUID NOT NULL REFERENCES users(id),
    amount       NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency     VARCHAR(10) NOT NULL DEFAULT 'INR',
    note         TEXT,
    settled_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID NOT NULL REFERENCES users(id),
    CONSTRAINT chk_settlement_not_self CHECK (paid_by <> paid_to)
);
CREATE INDEX idx_settlements_group ON settlements(group_id);
CREATE INDEX idx_settlements_paid_by ON settlements(paid_by);
CREATE INDEX idx_settlements_paid_to ON settlements(paid_to);

-- ---------------- NOTIFICATIONS ----------------
CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(40) NOT NULL, -- FRIEND_REQUEST / GROUP_ADDED / EXPENSE_ADDED / EXPENSE_EDITED / SETTLEMENT
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    reference_id UUID,                 -- id of related entity (expense, group, etc.)
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read);

-- ---------------- ACTIVITY FEED ----------------
CREATE TABLE activity_log (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id     UUID REFERENCES groups(id) ON DELETE CASCADE,
    actor_id     UUID NOT NULL REFERENCES users(id),
    action_type  VARCHAR(40) NOT NULL, -- EXPENSE_CREATED / EXPENSE_EDITED / EXPENSE_DELETED / MEMBER_JOINED / MEMBER_LEFT / SETTLEMENT_MADE
    reference_id UUID,
    metadata     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_group ON activity_log(group_id);

-- ---------------- IMPORT HISTORY ----------------
CREATE TABLE import_history (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source         VARCHAR(30) NOT NULL DEFAULT 'SPLITWISE_CSV',
    file_name      VARCHAR(255),
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / SUCCESS / FAILED / PARTIAL
    total_rows     INT DEFAULT 0,
    imported_rows  INT DEFAULT 0,
    failed_rows    INT DEFAULT 0,
    error_report   JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------- SEED CATEGORIES ----------------
INSERT INTO categories (name, icon, is_system) VALUES
    ('Food', 'restaurant', TRUE),
    ('Travel', 'flight', TRUE),
    ('Shopping', 'shopping-bag', TRUE),
    ('Rent', 'home', TRUE),
    ('Utilities', 'bolt', TRUE),
    ('Entertainment', 'movie', TRUE),
    ('Medical', 'medical-services', TRUE),
    ('Others', 'category', TRUE);
