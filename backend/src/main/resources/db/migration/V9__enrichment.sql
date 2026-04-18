-- ============================================================
-- V9: Data Enrichment — Providers + Cached Results
-- ============================================================
-- Enrichment pulls data about contacts from 3rd party APIs (Hunter, Clearbit, etc.)
-- to fill in missing fields: verified emails, phone numbers, job titles, company info.
--
-- Strategy: waterfall — try providers in priority order until a field is resolved.
-- Results are cached per (workspace, contact, provider) for 90 days.
-- ============================================================

-- ============================================================
-- ENRICHMENT PROVIDERS — Per-workspace provider configuration
-- ============================================================
-- Each workspace can configure which providers are enabled, in what order,
-- and with what API keys. Keys are stored encrypted (AES-256-GCM).
-- ============================================================
CREATE TABLE enrichment_providers (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    -- Which provider this row configures
    provider_key        VARCHAR(50) NOT NULL,     -- HUNTER, DROPCONTACT, CLEARBIT, etc.

    -- Encrypted API key (nullable if provider doesn't need auth)
    api_key_encrypted   TEXT,

    -- Enabled in the waterfall? If false, this provider is skipped.
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    -- Position in the waterfall — lower = tried first
    priority            INTEGER NOT NULL DEFAULT 100,

    -- Usage tracking
    calls_this_month    INTEGER NOT NULL DEFAULT 0,
    calls_month_date    DATE,                     -- resets when new month

    last_used_at        TIMESTAMP,
    last_error          TEXT,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    -- One config row per (workspace, provider)
    UNIQUE(workspace_id, provider_key)
);

CREATE INDEX idx_enrichment_providers_workspace ON enrichment_providers(workspace_id);

-- ============================================================
-- ENRICHMENT RESULTS — Cache of enrichment data per contact
-- ============================================================
-- Stores raw response data from each provider so we can:
--   1. Avoid re-calling the API if cache is fresh (< 90 days)
--   2. Audit what came from which provider
--   3. Debug why a field wasn't enriched
-- ============================================================
CREATE TABLE enrichment_results (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    contact_id          UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,

    provider_key        VARCHAR(50) NOT NULL,

    -- Result outcome
    status              VARCHAR(20) NOT NULL,     -- SUCCESS, NOT_FOUND, ERROR, RATE_LIMITED

    -- Raw JSON response from the provider (for audit / debugging / re-parsing)
    raw_response        JSONB,

    -- Extracted fields (flattened for easier queries)
    found_email         VARCHAR(255),
    found_phone         VARCHAR(50),
    found_title         VARCHAR(255),
    found_linkedin_url  VARCHAR(500),

    -- Quality signal: provider-reported confidence (0-100) if available
    confidence_score    INTEGER,

    error_message       TEXT,

    enriched_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_enrichment_results_contact ON enrichment_results(contact_id, provider_key);
CREATE INDEX idx_enrichment_results_workspace_date ON enrichment_results(workspace_id, enriched_at DESC);
