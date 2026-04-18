-- ============================================================
-- V15: API keys for the browser extension + future integrations
-- ============================================================
-- We store a SHA-256 hash of the key, never the plaintext.
-- The plaintext is only shown once at creation time.
--
-- Each key is tied to a workspace AND a user — that user's permissions
-- define what the key can do. Revoking the user (or the key) kills it.
-- ============================================================

CREATE TABLE api_keys (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Display name chosen by the user (e.g., "My MacBook Chrome")
    name                 VARCHAR(100) NOT NULL,

    -- SHA-256 hex of the plaintext key. Plain keys are 40 chars like lr_<32 hex>.
    key_hash             VARCHAR(64) NOT NULL UNIQUE,

    -- First 4 chars of the plaintext — shown in the UI so users can identify keys
    -- ("lr_ab..." = "ab") without revealing the secret.
    key_prefix           VARCHAR(8) NOT NULL,

    last_used_at         TIMESTAMP,
    revoked_at           TIMESTAMP,

    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_workspace ON api_keys(workspace_id, created_at DESC);
CREATE INDEX idx_api_keys_lookup    ON api_keys(key_hash) WHERE revoked_at IS NULL;
