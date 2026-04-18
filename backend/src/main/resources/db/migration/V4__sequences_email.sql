-- ============================================================
-- V4: Email Outreach — Mailboxes, Templates, Sequences, Enrollments
-- ============================================================
-- This migration creates the core email outreach infrastructure:
--   - mailboxes: connected SMTP/Gmail/Outlook accounts for sending
--   - email_templates: reusable templates with variable placeholders
--   - sequences: multi-step outreach campaigns
--   - sequence_steps: individual steps (EMAIL, DELAY, LINKEDIN, CALL)
--   - sequence_enrollments: contacts enrolled in sequences
--   - sequence_step_executions: audit log of every step execution
--   - email_send_logs: every email sent, with tracking IDs
-- ============================================================

-- ============================================================
-- MAILBOXES — Connected sending accounts
-- ============================================================
CREATE TABLE mailboxes (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id            UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                    VARCHAR(255) NOT NULL,      -- display name: "Sales Team Inbox"
    email                   VARCHAR(255) NOT NULL,      -- from address
    provider                VARCHAR(20) NOT NULL,       -- SMTP, GMAIL, OUTLOOK

    -- SMTP config (encrypted credentials stored as JSON)
    smtp_host               VARCHAR(255),
    smtp_port               INTEGER,
    smtp_username           VARCHAR(255),
    credentials_encrypted   TEXT NOT NULL,              -- AES-256 encrypted password/token

    -- Limits
    daily_limit             INTEGER DEFAULT 100,
    sends_today             INTEGER DEFAULT 0,
    sends_today_date        DATE,                       -- resets when new day

    -- Warmup config (Phase 3 feature — placeholder for now)
    warmup_enabled          BOOLEAN DEFAULT FALSE,
    warmup_current_daily    INTEGER DEFAULT 5,          -- ramps up over time

    -- Health
    status                  VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, PAUSED, ERROR
    last_error              TEXT,
    last_tested_at          TIMESTAMP,

    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(workspace_id, email)
);

CREATE INDEX idx_mailboxes_workspace ON mailboxes(workspace_id);
CREATE INDEX idx_mailboxes_workspace_status ON mailboxes(workspace_id, status);

-- ============================================================
-- EMAIL TEMPLATES — Reusable templates with variables
-- ============================================================
-- Variables: {{firstName}}, {{lastName}}, {{fullName}}, {{companyName}}, {{title}}, {{email}}
-- Replaced at send time with the enrolled contact's data.
-- ============================================================
CREATE TABLE email_templates (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,
    subject             VARCHAR(500) NOT NULL,
    body_html           TEXT,                           -- rich HTML version
    body_text           TEXT,                           -- plain text fallback

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_templates_workspace ON email_templates(workspace_id);

-- ============================================================
-- SEQUENCES — Multi-step outreach campaigns
-- ============================================================
CREATE TABLE sequences (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,
    description         TEXT,

    -- Status: DRAFT (editing), ACTIVE (accepting new enrollments), PAUSED (stopped)
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- Which mailbox to send from (nullable - user picks at enrollment time)
    default_mailbox_id  UUID REFERENCES mailboxes(id) ON DELETE SET NULL,

    -- Aggregate stats (updated when steps execute)
    total_enrolled      INTEGER DEFAULT 0,
    total_completed     INTEGER DEFAULT 0,
    total_replied       INTEGER DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sequences_workspace ON sequences(workspace_id);
CREATE INDEX idx_sequences_workspace_status ON sequences(workspace_id, status);

-- ============================================================
-- SEQUENCE STEPS — Individual steps within a sequence
-- ============================================================
-- Example sequence:
--   Step 1: EMAIL "Intro email"          (step_order: 1, delay_days: 0)
--   Step 2: DELAY                        (step_order: 2, delay_days: 3)
--   Step 3: EMAIL "Follow-up"            (step_order: 3, delay_days: 0)
--   Step 4: DELAY                        (step_order: 4, delay_days: 5)
--   Step 5: EMAIL "Final pitch"          (step_order: 5, delay_days: 0)
-- ============================================================
CREATE TABLE sequence_steps (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id            UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    sequence_id             UUID NOT NULL REFERENCES sequences(id) ON DELETE CASCADE,

    step_order              INTEGER NOT NULL,                -- 1, 2, 3, ...
    step_type               VARCHAR(20) NOT NULL,            -- EMAIL, DELAY, LINKEDIN_MESSAGE, LINKEDIN_CONNECT, CALL, TASK

    -- Delay before this step executes (in days) — after the previous step completes
    delay_days              INTEGER DEFAULT 0,

    -- For EMAIL steps
    email_template_id       UUID REFERENCES email_templates(id) ON DELETE SET NULL,
    subject_override        VARCHAR(500),                    -- override template subject for this step
    body_html_override      TEXT,                            -- override template body

    -- For TASK/CALL steps
    task_description        TEXT,

    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(sequence_id, step_order)
);

CREATE INDEX idx_sequence_steps_sequence ON sequence_steps(sequence_id, step_order);

-- ============================================================
-- SEQUENCE ENROLLMENTS — Contacts enrolled in sequences
-- ============================================================
CREATE TABLE sequence_enrollments (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id            UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    sequence_id             UUID NOT NULL REFERENCES sequences(id) ON DELETE CASCADE,
    contact_id              UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,

    -- Which mailbox to send from for this enrollment
    mailbox_id              UUID REFERENCES mailboxes(id) ON DELETE SET NULL,

    -- Progress tracking
    current_step_index      INTEGER DEFAULT 0,               -- 0-indexed: which step is next
    next_execution_at       TIMESTAMP,                       -- when the next step is due

    -- Status
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                            -- ACTIVE, PAUSED, COMPLETED, REPLIED, BOUNCED, UNSUBSCRIBED, FAILED

    enrolled_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    -- A contact can only be enrolled in a sequence ONCE (at a time)
    UNIQUE(sequence_id, contact_id)
);

CREATE INDEX idx_enrollments_workspace ON sequence_enrollments(workspace_id);
CREATE INDEX idx_enrollments_sequence ON sequence_enrollments(sequence_id);
CREATE INDEX idx_enrollments_contact ON sequence_enrollments(contact_id);
CREATE INDEX idx_enrollments_status ON sequence_enrollments(status);

-- Critical index: the scheduler finds due enrollments by this query:
--   WHERE status = 'ACTIVE' AND next_execution_at <= NOW()
CREATE INDEX idx_enrollments_due ON sequence_enrollments(status, next_execution_at)
    WHERE status = 'ACTIVE';

-- ============================================================
-- SEQUENCE STEP EXECUTIONS — Audit log per step execution
-- ============================================================
-- Every time a step runs (or fails) for an enrollment, we log it here.
-- This provides a full history and supports analytics (open/click rates per step).
-- ============================================================
CREATE TABLE sequence_step_executions (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id            UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    enrollment_id           UUID NOT NULL REFERENCES sequence_enrollments(id) ON DELETE CASCADE,
    sequence_step_id        UUID NOT NULL REFERENCES sequence_steps(id) ON DELETE CASCADE,

    status                  VARCHAR(20) NOT NULL,       -- PENDING, SENT, FAILED, SKIPPED
    error_message           TEXT,

    -- Tracking (for email steps)
    message_id              VARCHAR(500),                -- SMTP Message-ID header
    sent_at                 TIMESTAMP,
    opened_at               TIMESTAMP,
    first_clicked_at        TIMESTAMP,
    replied_at              TIMESTAMP,
    bounced_at              TIMESTAMP,

    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_step_executions_enrollment ON sequence_step_executions(enrollment_id);
CREATE INDEX idx_step_executions_message_id ON sequence_step_executions(message_id);

-- ============================================================
-- EMAIL SEND LOGS — Low-level record of every email sent
-- ============================================================
-- Separate from step executions because we send emails outside sequences too
-- (e.g., manual "send email" from contact page, notifications).
-- ============================================================
CREATE TABLE email_send_logs (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id            UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    mailbox_id              UUID NOT NULL REFERENCES mailboxes(id),
    contact_id              UUID REFERENCES contacts(id) ON DELETE SET NULL,

    -- Link back to sequence execution (null for non-sequence sends)
    step_execution_id       UUID REFERENCES sequence_step_executions(id) ON DELETE SET NULL,

    to_email                VARCHAR(255) NOT NULL,
    from_email              VARCHAR(255) NOT NULL,
    subject                 VARCHAR(500) NOT NULL,
    body_html               TEXT,

    message_id              VARCHAR(500),                -- SMTP Message-ID
    status                  VARCHAR(20) NOT NULL,       -- QUEUED, SENT, FAILED, BOUNCED
    error_message           TEXT,
    sent_at                 TIMESTAMP,

    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_send_logs_workspace ON email_send_logs(workspace_id, sent_at DESC);
CREATE INDEX idx_email_send_logs_contact ON email_send_logs(contact_id);
CREATE INDEX idx_email_send_logs_message_id ON email_send_logs(message_id);
