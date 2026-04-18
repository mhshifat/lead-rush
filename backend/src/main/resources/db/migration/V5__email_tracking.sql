-- ============================================================
-- V5: Email Tracking — Unsubscribes + Open/Click Events
-- ============================================================
-- Adds:
--   - unsubscribes: contacts that opted out (one per contact per workspace)
--   - email_link_clicks: records every click with the URL that was clicked
--   - email_deliverability_checks: cached SPF/DKIM/DMARC DNS check results
--
-- Existing columns on sequence_step_executions already track open/reply/bounce:
--   opened_at, first_clicked_at, replied_at, bounced_at
-- ============================================================

-- ============================================================
-- UNSUBSCRIBES — Contacts that opted out of sequences
-- ============================================================
-- When a contact clicks the unsubscribe link, we record it here.
-- The SequenceExecutionJob checks this table before sending each email
-- and marks the enrollment as UNSUBSCRIBED.
-- ============================================================
CREATE TABLE unsubscribes (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    contact_id          UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,

    -- Where the unsubscribe came from
    source              VARCHAR(50) NOT NULL DEFAULT 'LINK_CLICK',   -- LINK_CLICK, LIST_UNSUBSCRIBE, MANUAL, BOUNCE
    reason              TEXT,                                          -- user's optional reason

    -- Link back to the email that triggered the unsub (nullable)
    step_execution_id   UUID REFERENCES sequence_step_executions(id) ON DELETE SET NULL,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    -- One unsubscribe per contact per workspace
    UNIQUE(workspace_id, contact_id)
);

CREATE INDEX idx_unsubscribes_workspace ON unsubscribes(workspace_id);
CREATE INDEX idx_unsubscribes_contact ON unsubscribes(contact_id);

-- ============================================================
-- EMAIL LINK CLICKS — Individual click events per email
-- ============================================================
-- Tracks every link click. One step_execution can have many clicks
-- (user clicks the link multiple times or clicks different links).
-- ============================================================
CREATE TABLE email_link_clicks (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    step_execution_id   UUID NOT NULL REFERENCES sequence_step_executions(id) ON DELETE CASCADE,

    clicked_url         TEXT NOT NULL,
    user_agent          TEXT,
    ip_address          VARCHAR(45),                    -- IPv4 (15) or IPv6 (45) length

    clicked_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_link_clicks_execution ON email_link_clicks(step_execution_id);

-- ============================================================
-- EMAIL DELIVERABILITY CHECKS — Cached DNS lookup results
-- ============================================================
-- Caches SPF/DKIM/DMARC DNS record checks per domain.
-- Cache TTL: 24 hours (DNS doesn't change often but does change).
-- ============================================================
CREATE TABLE email_deliverability_checks (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    domain              VARCHAR(255) NOT NULL,

    -- Results
    spf_status          VARCHAR(20),                    -- PASS, FAIL, NOT_FOUND, ERROR
    spf_record          TEXT,                           -- actual SPF record (if found)

    dkim_status         VARCHAR(20),
    dkim_selector       VARCHAR(100),                   -- which DKIM selector was checked
    dkim_record         TEXT,

    dmarc_status        VARCHAR(20),
    dmarc_record        TEXT,

    -- Cache expiry
    checked_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(workspace_id, domain)
);

CREATE INDEX idx_deliverability_domain ON email_deliverability_checks(workspace_id, domain);
