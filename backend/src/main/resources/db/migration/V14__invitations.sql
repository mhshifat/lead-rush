-- ============================================================
-- V14: Workspace invitations
-- ============================================================
-- An OWNER / ADMIN invites someone by email. The invitee gets a one-time
-- signup link with an opaque token. Accepting the link:
--   - creates a User (if email is new) OR attaches to the existing User
--   - creates a WorkspaceMembership with the invited role
--   - marks the invitation ACCEPTED
--
-- Invitations expire after 7 days. Revoked and expired invitations stay in
-- the table for audit purposes.
-- ============================================================

CREATE TABLE workspace_invitations (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    -- Who sent it
    invited_by_user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Target
    email                VARCHAR(255) NOT NULL,
    role                 VARCHAR(20)  NOT NULL,     -- OWNER / ADMIN / MANAGER / MEMBER / VIEWER

    -- Opaque token sent in the email link (UUID string)
    token                VARCHAR(64)  NOT NULL UNIQUE,

    -- PENDING, ACCEPTED, REVOKED, EXPIRED
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',

    expires_at           TIMESTAMP NOT NULL,
    accepted_at          TIMESTAMP,
    accepted_by_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,

    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

-- A workspace can have at most one PENDING invite per email at a time.
CREATE UNIQUE INDEX idx_invitations_workspace_email_pending
    ON workspace_invitations(workspace_id, lower(email))
    WHERE status = 'PENDING';

CREATE INDEX idx_invitations_workspace ON workspace_invitations(workspace_id, created_at DESC);
