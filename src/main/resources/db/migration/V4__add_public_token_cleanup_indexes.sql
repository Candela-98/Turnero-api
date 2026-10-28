CREATE INDEX idx_public_tokens_cleanup_expires
    ON appointment_public_tokens (expires_at)
    WHERE used_at IS NULL;

CREATE INDEX idx_public_tokens_cleanup_used
    ON appointment_public_tokens (used_at)
    WHERE used_at IS NOT NULL;