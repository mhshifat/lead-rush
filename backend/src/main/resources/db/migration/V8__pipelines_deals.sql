-- ============================================================
-- V8: Pipelines, Stages, Deals — CRM core
-- ============================================================
-- A workspace has many Pipelines (e.g., "Sales", "Partnerships").
-- A Pipeline has many PipelineStages (e.g., "Discovery", "Demo", "Proposal", "Won").
-- A Deal lives in one Stage at a time, moves through them as it progresses.
-- A Deal can have multiple Contacts (the people involved in the opportunity).
-- ============================================================

-- ============================================================
-- PIPELINES
-- ============================================================
CREATE TABLE pipelines (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,
    description         TEXT,

    -- The default pipeline is picked when a new deal is created without specifying one
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,

    display_order       INTEGER NOT NULL DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pipelines_workspace ON pipelines(workspace_id);

-- ============================================================
-- PIPELINE STAGES
-- ============================================================
CREATE TABLE pipeline_stages (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    pipeline_id         UUID NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,
    -- Hex color for the stage header (e.g., "#3B82F6")
    color               VARCHAR(7),

    -- Probability of winning at this stage (0-100)
    win_probability     INTEGER NOT NULL DEFAULT 0,

    -- Order within the pipeline (left-to-right in the Kanban board)
    display_order       INTEGER NOT NULL DEFAULT 0,

    -- Stage type: OPEN (deal in progress), WON (closed-won), LOST (closed-lost)
    stage_type          VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pipeline_stages_pipeline ON pipeline_stages(pipeline_id, display_order);

-- ============================================================
-- DEALS
-- ============================================================
CREATE TABLE deals (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    pipeline_id         UUID NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    pipeline_stage_id   UUID NOT NULL REFERENCES pipeline_stages(id),

    name                VARCHAR(500) NOT NULL,
    description         TEXT,

    -- Financial details
    value_amount        DECIMAL(14, 2),                 -- the deal value (nullable — not all deals have $)
    value_currency      VARCHAR(3) DEFAULT 'USD',       -- ISO 4217

    -- Ownership + lifecycle
    owner_user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    expected_close_at   DATE,
    closed_at           TIMESTAMP,                      -- set when moved to WON/LOST

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deals_workspace ON deals(workspace_id);
CREATE INDEX idx_deals_pipeline ON deals(pipeline_id);
CREATE INDEX idx_deals_pipeline_stage ON deals(pipeline_id, pipeline_stage_id);

-- ============================================================
-- DEAL_CONTACTS — Junction: deals ←→ contacts
-- ============================================================
CREATE TABLE deal_contacts (
    deal_id             UUID NOT NULL REFERENCES deals(id) ON DELETE CASCADE,
    contact_id          UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,

    -- Whether this contact is the primary contact for the deal
    is_primary          BOOLEAN NOT NULL DEFAULT FALSE,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    PRIMARY KEY (deal_id, contact_id)
);

CREATE INDEX idx_deal_contacts_deal ON deal_contacts(deal_id);
CREATE INDEX idx_deal_contacts_contact ON deal_contacts(contact_id);
