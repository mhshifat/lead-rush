-- ============================================================
-- V7: Tasks — Manual tasks for users (from sequences or created manually)
-- ============================================================
CREATE TABLE tasks (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    -- Who owns this task (usually the workspace member doing outreach)
    assigned_to_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    -- What it's about
    title               VARCHAR(500) NOT NULL,
    description         TEXT,
    task_type           VARCHAR(30) NOT NULL DEFAULT 'MANUAL',   -- MANUAL, CALL, LINKEDIN_MESSAGE, LINKEDIN_CONNECT

    -- Related entities (nullable — task might not be about a contact)
    contact_id          UUID REFERENCES contacts(id) ON DELETE CASCADE,
    sequence_id         UUID REFERENCES sequences(id) ON DELETE SET NULL,
    enrollment_id       UUID REFERENCES sequence_enrollments(id) ON DELETE SET NULL,

    -- Scheduling
    due_at              TIMESTAMP,

    -- Status
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, COMPLETED, CANCELLED
    completed_at        TIMESTAMP,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tasks_workspace ON tasks(workspace_id);
CREATE INDEX idx_tasks_workspace_status ON tasks(workspace_id, status);
CREATE INDEX idx_tasks_workspace_assignee ON tasks(workspace_id, assigned_to_user_id, status);
CREATE INDEX idx_tasks_contact ON tasks(contact_id);
