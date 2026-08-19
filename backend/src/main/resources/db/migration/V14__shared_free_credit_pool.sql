ALTER TABLE ai_feature_daily_usage
    ADD COLUMN credit_group VARCHAR(30);

-- Both existing feature keys collapse into the same group for now.
UPDATE ai_feature_daily_usage
SET credit_group = 'AI_ASSIST';

-- Merge existing rows before deleting them.
CREATE TEMP TABLE ai_feature_daily_usage_merged AS
SELECT
    user_id,
    credit_group,
    SUM(free_used_today) AS free_used_today,
    MAX(free_reset_at) AS free_reset_at
FROM ai_feature_daily_usage
GROUP BY user_id, credit_group;

-- Remove the old feature-specific rows.
DELETE FROM ai_feature_daily_usage;

-- Remove the old feature_key column BEFORE inserting rows,
-- because it is NOT NULL and is no longer part of the new schema.
ALTER TABLE ai_feature_daily_usage
    DROP COLUMN feature_key;

ALTER TABLE ai_feature_daily_usage
    ALTER COLUMN credit_group SET NOT NULL;

ALTER TABLE ai_feature_daily_usage
    DROP CONSTRAINT IF EXISTS uq_ai_feature_daily_usage_user_feature;

-- Insert the merged shared-credit rows.
INSERT INTO ai_feature_daily_usage (
    id,
    user_id,
    credit_group,
    free_used_today,
    free_reset_at
)
SELECT
    uuid_generate_v4(),
    user_id,
    credit_group,
    free_used_today,
    free_reset_at
FROM ai_feature_daily_usage_merged;

DROP TABLE ai_feature_daily_usage_merged;

ALTER TABLE ai_feature_daily_usage
    ADD CONSTRAINT uq_ai_feature_daily_usage_user_group
    UNIQUE (user_id, credit_group);