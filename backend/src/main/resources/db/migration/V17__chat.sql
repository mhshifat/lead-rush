-- ============================================================
-- V17: Chat widget + conversations
-- ============================================================
-- Each workspace has ONE chat widget config (colors, greeting, etc.).
-- A `conversation` is one thread between a visitor and the team.
-- Visitors get a long-lived `visitor_token` (UUID stored in browser cookie) so
-- return visits thread into the same conversation until the agent closes it.
-- ============================================================

CREATE TABLE chat_widgets (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id         UUID NOT NULL UNIQUE REFERENCES workspaces(id) ON DELETE CASCADE,

    enabled              BOOLEAN NOT NULL DEFAULT TRUE,

    display_name         VARCHAR(100) NOT NULL DEFAULT 'Support',
    greeting             TEXT         NOT NULL DEFAULT 'Hi there! How can we help?',
    offline_message      TEXT         NOT NULL DEFAULT 'We''re offline — leave an email and we''ll get back to you.',
    primary_color        VARCHAR(20)  NOT NULL DEFAULT '#5E6AD2',
    position             VARCHAR(20)  NOT NULL DEFAULT 'BOTTOM_RIGHT',

    -- When true, the widget asks for an email before the first message
    require_email        BOOLEAN NOT NULL DEFAULT TRUE,

    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
CREATE TABLE chat_conversations (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    -- Long-lived token stored in visitor's browser cookie — anonymizes them until
    -- they submit an email, at which point we create/match a contact.
    visitor_token        VARCHAR(64) NOT NULL UNIQUE,

    -- Best-effort visitor identity
    visitor_name         VARCHAR(200),
    visitor_email        VARCHAR(255),

    -- Contact this conversation is bound to (nullable until we have an email)
    contact_id           UUID REFERENCES contacts(id) ON DELETE SET NULL,

    -- OPEN / CLOSED. Closed conversations don't surface new messages to new agents.
    status               VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    -- Assigned agent (user). Null = unassigned, everyone in workspace can reply.
    assigned_user_id     UUID REFERENCES users(id) ON DELETE SET NULL,

    -- Cached denormalized summary fields for the inbox list
    last_message_at      TIMESTAMP,
    last_message_preview VARCHAR(500),
    unread_by_team       INTEGER NOT NULL DEFAULT 0,
    unread_by_visitor    INTEGER NOT NULL DEFAULT 0,

    -- Page the widget lives on when the conversation was started (analytics)
    source_url           VARCHAR(2000),
    user_agent           VARCHAR(500),

    closed_at            TIMESTAMP,

    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_conversations_workspace_recent
    ON chat_conversations(workspace_id, last_message_at DESC NULLS LAST);

CREATE INDEX idx_chat_conversations_contact
    ON chat_conversations(contact_id)
    WHERE contact_id IS NOT NULL;

-- ============================================================
CREATE TABLE chat_messages (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    conversation_id      UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,

    -- VISITOR | AGENT | SYSTEM (system = auto-greeting, assignment notice, etc.)
    sender               VARCHAR(20) NOT NULL,

    -- Only set when sender = AGENT
    agent_user_id        UUID REFERENCES users(id) ON DELETE SET NULL,

    body                 TEXT NOT NULL,

    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_messages_conversation
    ON chat_messages(conversation_id, created_at ASC);
