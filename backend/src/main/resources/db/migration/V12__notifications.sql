-- ============================================================
-- V12: In-App Notifications
-- ============================================================
-- Notifications are delivered in real time via WebSocket AND persisted in this
-- table so users see them when they come back later. Always workspace-scoped
-- because membership is per-workspace.
-- ============================================================

CREATE TABLE notifications (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    -- Recipient user (always set — every notification has a target)
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Semantic type — lets the UI pick an icon, route, etc.
    --   TASK_ASSIGNED, ENROLLMENT_COMPLETED, ENROLLMENT_BOUNCED,
    --   FORM_SUBMITTED, SCORE_THRESHOLD, SEQUENCE_STEP_SKIPPED,
    --   DEAL_ASSIGNED, CONTACT_REPLIED, GENERIC
    type                 VARCHAR(40) NOT NULL,

    title                VARCHAR(255) NOT NULL,
    body                 TEXT,

    -- Optional deep-link path the UI can navigate to (e.g., /contacts/abc-123)
    link_path            VARCHAR(500),

    -- Free-form metadata — contact IDs, deal IDs, etc.
    metadata             JSONB DEFAULT '{}'::jsonb,

    read_at              TIMESTAMP,

    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_unread
    ON notifications(workspace_id, user_id, read_at)
    WHERE read_at IS NULL;

CREATE INDEX idx_notifications_user_recent
    ON notifications(workspace_id, user_id, created_at DESC);
