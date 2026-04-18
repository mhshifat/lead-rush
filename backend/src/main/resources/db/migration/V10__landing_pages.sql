-- ============================================================
-- V10: Landing Pages, Forms, Form Submissions
-- ============================================================
-- A LandingPage is a block-based page (like Leadpages/Unbounce).
-- A Form is a list of fields that, when submitted, creates/updates a Contact.
-- FormSubmissions are the actual submitted data.
-- PageVisits are anonymous view analytics (for basic conversion tracking).
-- ============================================================

-- ============================================================
-- FORMS — Field collection definitions
-- ============================================================
CREATE TABLE forms (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,
    description         TEXT,

    -- Field definitions as JSON: [{ "key": "firstName", "label": "First Name", "type": "text", "required": true }, ...]
    -- Field types: text, email, tel, textarea, select, checkbox
    fields              JSONB NOT NULL DEFAULT '[]',

    -- Where to redirect after successful submission (optional)
    success_redirect_url VARCHAR(500),

    -- Inline success message shown after submit (used if no redirect_url)
    success_message     TEXT DEFAULT 'Thank you! We''ll be in touch.',

    -- If true, auto-enroll submitter in a sequence
    auto_enroll_sequence_id UUID REFERENCES sequences(id) ON DELETE SET NULL,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_forms_workspace ON forms(workspace_id);

-- ============================================================
-- LANDING PAGES — Block-based pages
-- ============================================================
CREATE TABLE landing_pages (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,

    -- URL-friendly slug — unique per workspace, used in /p/{slug} URLs
    slug                VARCHAR(255) NOT NULL,

    -- SEO
    meta_title          VARCHAR(255),
    meta_description    VARCHAR(500),

    -- Block JSON schema — array of blocks:
    -- [{ "id": "uuid", "type": "hero", "props": { "title": "...", "subtitle": "..." } }, ...]
    blocks              JSONB NOT NULL DEFAULT '[]',

    -- Publishing
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',     -- DRAFT, PUBLISHED
    published_at        TIMESTAMP,

    -- Aggregate analytics (updated by visit logging)
    view_count          INTEGER NOT NULL DEFAULT 0,
    conversion_count    INTEGER NOT NULL DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(workspace_id, slug)
);

CREATE INDEX idx_landing_pages_workspace ON landing_pages(workspace_id);
CREATE INDEX idx_landing_pages_slug ON landing_pages(slug) WHERE status = 'PUBLISHED';

-- ============================================================
-- FORM SUBMISSIONS — Raw submitted data per form
-- ============================================================
CREATE TABLE form_submissions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    form_id             UUID NOT NULL REFERENCES forms(id) ON DELETE CASCADE,

    -- Optional link to landing page where submission happened
    landing_page_id     UUID REFERENCES landing_pages(id) ON DELETE SET NULL,

    -- Contact that was created or updated from this submission
    contact_id          UUID REFERENCES contacts(id) ON DELETE SET NULL,

    -- Raw submitted data: { "firstName": "John", "email": "...", ... }
    data                JSONB NOT NULL,

    -- Attribution
    ip_address          VARCHAR(45),
    user_agent          TEXT,
    referrer            VARCHAR(500),
    utm_source          VARCHAR(100),
    utm_medium          VARCHAR(100),
    utm_campaign        VARCHAR(100),

    submitted_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_form_submissions_workspace ON form_submissions(workspace_id, submitted_at DESC);
CREATE INDEX idx_form_submissions_form ON form_submissions(form_id, submitted_at DESC);
CREATE INDEX idx_form_submissions_contact ON form_submissions(contact_id);

-- ============================================================
-- PAGE VISITS — Anonymous view tracking
-- ============================================================
CREATE TABLE page_visits (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    landing_page_id     UUID NOT NULL REFERENCES landing_pages(id) ON DELETE CASCADE,

    -- Hashed IP (sha256) for GDPR — we log anonymous visitor counts, not identities
    visitor_hash        VARCHAR(64),

    referrer            VARCHAR(500),
    utm_source          VARCHAR(100),
    utm_medium          VARCHAR(100),
    utm_campaign        VARCHAR(100),

    visited_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_page_visits_page ON page_visits(landing_page_id, visited_at DESC);
