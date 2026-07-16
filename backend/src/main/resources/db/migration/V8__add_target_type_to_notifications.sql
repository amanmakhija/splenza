-- ==========================================================
-- V8: adding target type column in notifications table
-- ==========================================================

ALTER TABLE notifications
ADD COLUMN target_type VARCHAR(30);

UPDATE notifications
SET target_type =
CASE type
    WHEN 'EXPENSE_ADDED' THEN 'EXPENSE'
    WHEN 'EXPENSE_EDITED' THEN 'EXPENSE'
    WHEN 'GROUP_ADDED' THEN 'GROUP'
    WHEN 'FRIEND_REQUEST' THEN 'FRIEND'
    WHEN 'SETTLEMENT' THEN 'SETTLEMENT'
END;

ALTER TABLE notifications
ALTER COLUMN target_type
SET NOT NULL;