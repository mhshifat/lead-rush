-- Domain email pattern cache.
-- When any enrichment adapter successfully finds an email for a domain, we record
-- which pattern it used (FIRST_DOT_LAST, FLAST, etc.) so future contacts at the
-- same domain can skip the external call and construct the email directly.
-- Confidence grows each time we re-confirm the same pattern at that domain.

CREATE TABLE domain_email_patterns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    domain VARCHAR(255) NOT NULL,
    pattern_type VARCHAR(40) NOT NULL,     -- e.g. FIRST_DOT_LAST, F_LAST
    confidence INT NOT NULL DEFAULT 1,     -- rises as we re-confirm the same pattern
    catch_all BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(40),                    -- which adapter discovered it (HUNTER, SMTP_VERIFY, etc.)

    last_confirmed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (workspace_id, domain)
);

CREATE INDEX idx_domain_patterns_workspace_domain
    ON domain_email_patterns (workspace_id, domain);
