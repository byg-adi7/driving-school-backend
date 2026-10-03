-- ============================================================
-- Invites: an admin or instructor creates an account without a password;
-- the new user gets a link and chooses their own. INVITED accounts can't
-- sign in until they do. Existing accounts are ACTIVE (the default).
-- ============================================================
ALTER TABLE users ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN invite_expires_at TIMESTAMP;

-- One live invite per user: (re)sending replaces it. Only a SHA-256 hash of the
-- token is stored - the token itself exists only in the link.
CREATE TABLE account_invites (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash          VARCHAR(64) NOT NULL UNIQUE,
    expires_at          TIMESTAMP NOT NULL,
    used_at             TIMESTAMP,
    invited_by_user_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_account_invites_user_id ON account_invites(user_id);
