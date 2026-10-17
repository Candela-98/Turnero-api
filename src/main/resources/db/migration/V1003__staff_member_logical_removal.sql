ALTER TABLE staff_members ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE staff_members DROP CONSTRAINT staff_members_user_id_key;
CREATE UNIQUE INDEX uq_staff_members_current_user ON staff_members(user_id) WHERE deleted_at IS NULL;
ALTER TABLE staff_members ADD CONSTRAINT chk_removed_staff_inactive CHECK (deleted_at IS NULL OR status = 'INACTIVE');
