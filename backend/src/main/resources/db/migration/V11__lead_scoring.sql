-- ============================================================
-- V11: Lead Scoring — Rules + Audit Log
-- ============================================================
-- Lead scoring assigns a numeric score to every contact based on engagement + fit.
-- Score changes over time as events happen (emails opened, forms submitted, etc.).
-- Higher score = hotter lead.
--
-- The contacts.lead_score column already exists (from V2). This migration adds
-- the RULES that determine how scores are calculated + the AUDIT LOG of every change.
-- ============================================================

-- ============================================================
-- LEAD SCORE RULES — user-defined rules
-- ============================================================
-- Each rule says: "When TRIGGER X happens, add N points to the contact's score."
-- Optional field-based CONDITION filters when the rule applies.
--
-- TRIGGER types:
--   CONTACT_CREATED    — a new contact was added
--   CONTACT_UPDATED    — a contact's lifecycle stage changed
--   EMAIL_OPENED       — contact opened a sequence email
--   EMAIL_CLICKED      — contact clicked a link in a sequence email
--   EMAIL_REPLIED      — contact replied (Phase 3 — requires IMAP monitoring)
--   FORM_SUBMITTED     — contact submitted a form
--   ENROLLED           — contact was enrolled in a sequence
--
-- CONDITION (all optional — if null, rule fires for every trigger of this type):
--   condition_field:     the contact field to check (e.g., "title", "lifecycle_stage")
--   condition_operator:  EQUALS, CONTAINS, STARTS_WITH, GREATER_THAN, LESS_THAN
--   condition_value:     string to compare against
-- ============================================================
CREATE TABLE lead_score_rules (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                 VARCHAR(255) NOT NULL,
    description          TEXT,

    trigger_type         VARCHAR(40) NOT NULL,

    -- Optional condition filter
    condition_field      VARCHAR(100),
    condition_operator   VARCHAR(20),
    condition_value      VARCHAR(500),

    -- The scoring delta. Can be negative (e.g., -10 for "unsubscribed").
    points               INTEGER NOT NULL,

    enabled              BOOLEAN NOT NULL DEFAULT TRUE,

    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lead_score_rules_workspace ON lead_score_rules(workspace_id);
CREATE INDEX idx_lead_score_rules_trigger ON lead_score_rules(workspace_id, trigger_type, enabled);

-- ============================================================
-- LEAD SCORE LOGS — audit trail of every score change
-- ============================================================
-- Every time a rule fires (or a manual adjustment happens), we log it here.
-- Provides transparency ("why is this contact's score 85?") and debugging.
-- ============================================================
CREATE TABLE lead_score_logs (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    contact_id           UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,

    -- The rule that fired (null for manual adjustments)
    rule_id              UUID REFERENCES lead_score_rules(id) ON DELETE SET NULL,

    -- Snapshot of the rule's name at firing time (so the log survives rule deletion)
    rule_name            VARCHAR(255),

    -- Score change details
    points_delta         INTEGER NOT NULL,
    score_before         INTEGER NOT NULL,
    score_after          INTEGER NOT NULL,

    -- Context (why the rule fired)
    trigger_type         VARCHAR(40),
    reason               TEXT,

    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lead_score_logs_contact ON lead_score_logs(contact_id, created_at DESC);
CREATE INDEX idx_lead_score_logs_workspace ON lead_score_logs(workspace_id, created_at DESC);
