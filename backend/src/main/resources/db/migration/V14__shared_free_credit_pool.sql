-- Shared free daily credit pool across grouped AI features. Previously
-- ai_feature_daily_usage was keyed by (user_id, feature_key), giving every
-- feature its own independent free daily allowance. It's now keyed by
-- (user_id, credit_group) instead - which features share a group is a
-- hardcoded mapping in code (see FeatureCreditGroups), not configured here,
-- since it's a product/code-level classification rather than an
-- operational tuning knob. RECEIPT_SCAN and VOICE_EXPENSE both map to the
-- "AI_ASSIST" group for now; a future feature that shouldn't share this
-- pool would map to its own group name instead (see FeatureCreditGroups'
-- javadoc for the "ungrouped = solo group of one" default).

ALTER TABLE ai_feature_daily_usage ADD COLUMN credit_group VARCHAR(30);

-- Both existing feature keys collapse into the same group for now.
UPDATE ai_feature_daily_usage SET credit_group = 'AI_ASSIST';

-- A user may already have separate rows for RECEIPT_SCAN and VOICE_EXPENSE
-- today, which would now collide on the same (user_id, credit_group) - merge
-- them into a single row (summing today's usage, keeping the latest reset
-- time) rather than leaving duplicates or silently dropping one, so nobody
-- gets extra free credits today just because they'd used multiple features
-- before this migration ran.
CREATE TEMP TABLE ai_feature_daily_usage_merged AS
SELECT
    user_id,
    credit_group,
    SUM(free_used_today) AS free_used_today,
    MAX(free_reset_at)   AS free_reset_at
FROM ai_feature_daily_usage
GROUP BY user_id, credit_group;

DELETE FROM ai_feature_daily_usage;

INSERT INTO ai_feature_daily_usage (id, user_id, credit_group, free_used_today, free_reset_at)
SELECT uuid_generate_v4(), user_id, credit_group, free_used_today, free_reset_at
FROM ai_feature_daily_usage_merged;

DROP TABLE ai_feature_daily_usage_merged;

ALTER TABLE ai_feature_daily_usage ALTER COLUMN credit_group SET NOT NULL;
ALTER TABLE ai_feature_daily_usage DROP COLUMN feature_key;

ALTER TABLE ai_feature_daily_usage
    DROP CONSTRAINT IF EXISTS uq_ai_feature_daily_usage_user_feature;
ALTER TABLE ai_feature_daily_usage
    ADD CONSTRAINT uq_ai_feature_daily_usage_user_group UNIQUE (user_id, credit_group);