CREATE INDEX idx_user_sessions_cleanup_expires_at
    ON user_sessions (expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_user_sessions_cleanup_revoked_at
    ON user_sessions (revoked_at)
    WHERE revoked_at IS NOT NULL;
