-- Replaces the groups.is_deleted boolean with a nullable deleted_at
-- timestamp. A boolean flag can only tell us THAT a group was deleted; the
-- new "Recently Deleted" / restore feature also needs to know WHEN it was
-- deleted so a 30-day retention window can be enforced. Rather than keep
-- both an is_deleted flag and a deleted_at timestamp in sync forever,
-- deleted_at alone is now the single source of truth for a group's
-- deleted state (deleted_at IS NULL means active).
--
-- Note: this only applies to the `groups` table. group_members, expenses,
-- settlements and expense_participants keep their existing is_deleted
-- boolean flags (added in V1/V10) - they don't need their own retention
-- window, they just mirror whatever their parent group's state is.

ALTER TABLE groups ADD COLUMN deleted_at TIMESTAMPTZ;

-- Backfill: any group already marked is_deleted = true gets a deleted_at.
-- We don't have the original delete time on hand, so updated_at (which is
-- touched by the delete operation itself) is the closest available
-- approximation for pre-existing rows.
UPDATE groups SET deleted_at = updated_at WHERE is_deleted = true;

ALTER TABLE groups DROP COLUMN is_deleted;