-- Adds soft-delete support to the three tables that need it for the group
-- cascade-delete feature. groups and expenses already have is_deleted from
-- V1; these three did not.
--
-- group_members: so memberships are hidden when a group is soft-deleted
-- settlements: so settlements are hidden when a group is soft-deleted
-- expense_participants: so participants are hidden when a group is soft-deleted
--
-- NOTE: activity_log intentionally does NOT get is_deleted here. Activity logs
-- are retained permanently as a user-level audit trail across all groups, so
-- they are never soft-deleted as part of a group deletion cascade.

ALTER TABLE group_members       ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE settlements         ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE expense_participants ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;