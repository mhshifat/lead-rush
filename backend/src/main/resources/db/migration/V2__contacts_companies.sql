-- ============================================================
-- V2: Contacts, Companies, Tags, Custom Fields
-- ============================================================
-- All tables here are TENANT-SCOPED (have workspace_id).
-- The TenantEntity base class + Hibernate @Filter ensures
-- every query automatically filters by workspace_id.
-- ============================================================

-- ============================================================
-- COMPANIES — Firmographic data about organizations
-- ============================================================
-- A company can have many contacts (employees).
-- Created automatically when a contact's email domain is new,
-- or manually by the user.
-- ============================================================
CREATE TABLE companies (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,
    domain              VARCHAR(255),                   -- e.g., "acme.com" (extracted from email)
    industry            VARCHAR(255),
    company_size        VARCHAR(50),                    -- e.g., "1-10", "11-50", "51-200", "201-500", "500+"
    annual_revenue      VARCHAR(100),                   -- stored as string: "$1M-$5M", etc.
    description         TEXT,
    website             VARCHAR(500),
    logo_url            VARCHAR(500),
    phone               VARCHAR(50),
    address             TEXT,
    city                VARCHAR(255),
    state               VARCHAR(255),
    country             VARCHAR(255),
    zip_code            VARCHAR(20),

    -- Flexible data stored as JSON (tech stack, social links, etc.)
    metadata            JSONB DEFAULT '{}',

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Most queries filter by workspace first, then search/sort
CREATE INDEX idx_companies_workspace ON companies(workspace_id);
CREATE INDEX idx_companies_workspace_domain ON companies(workspace_id, domain);
CREATE INDEX idx_companies_workspace_name ON companies(workspace_id, name);

-- ============================================================
-- CONTACTS — People (leads, prospects, customers)
-- ============================================================
-- The core entity of Lead Rush. Each contact belongs to a workspace
-- and optionally belongs to a company.
-- ============================================================
CREATE TABLE contacts (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    first_name          VARCHAR(255) NOT NULL,
    last_name           VARCHAR(255),
    title               VARCHAR(255),                   -- job title: "VP of Sales"

    -- Link to company (optional — some contacts are freelancers)
    company_id          UUID REFERENCES companies(id) ON DELETE SET NULL,

    -- Lifecycle stage — tracks where this contact is in the funnel
    lifecycle_stage     VARCHAR(50) DEFAULT 'LEAD',     -- LEAD, CONTACTED, QUALIFIED, OPPORTUNITY, CUSTOMER, LOST

    -- Lead scoring
    lead_score          INTEGER DEFAULT 0,

    -- How this contact was acquired
    source              VARCHAR(100),                   -- MANUAL, CSV_IMPORT, FORM, LINKEDIN, API, ENRICHMENT

    avatar_url          VARCHAR(500),
    website             VARCHAR(500),
    linkedin_url        VARCHAR(500),
    twitter_url         VARCHAR(500),

    -- Flexible data (custom fields, enrichment data, etc.)
    metadata            JSONB DEFAULT '{}',

    -- Timestamps
    last_contacted_at   TIMESTAMP,                      -- last time we sent them something
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Primary search indexes
CREATE INDEX idx_contacts_workspace ON contacts(workspace_id);
CREATE INDEX idx_contacts_workspace_name ON contacts(workspace_id, first_name, last_name);
CREATE INDEX idx_contacts_workspace_company ON contacts(workspace_id, company_id);
CREATE INDEX idx_contacts_workspace_stage ON contacts(workspace_id, lifecycle_stage);
CREATE INDEX idx_contacts_workspace_score ON contacts(workspace_id, lead_score DESC);
CREATE INDEX idx_contacts_workspace_created ON contacts(workspace_id, created_at DESC);

-- ============================================================
-- CONTACT EMAILS — Multiple emails per contact
-- ============================================================
-- A contact can have multiple emails (work, personal, etc.).
-- One is marked as primary.
-- ============================================================
CREATE TABLE contact_emails (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id            UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    contact_id              UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,

    email                   VARCHAR(255) NOT NULL,
    email_type              VARCHAR(20) DEFAULT 'WORK',     -- WORK, PERSONAL, OTHER
    is_primary              BOOLEAN DEFAULT FALSE,
    verification_status     VARCHAR(20) DEFAULT 'UNKNOWN',  -- UNKNOWN, VALID, INVALID, CATCH_ALL

    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contact_emails_workspace ON contact_emails(workspace_id);
CREATE INDEX idx_contact_emails_contact ON contact_emails(contact_id);
CREATE INDEX idx_contact_emails_email ON contact_emails(workspace_id, email);

-- ============================================================
-- CONTACT PHONES — Multiple phones per contact
-- ============================================================
CREATE TABLE contact_phones (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    contact_id          UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,

    phone               VARCHAR(50) NOT NULL,
    phone_type          VARCHAR(20) DEFAULT 'WORK',     -- WORK, MOBILE, PERSONAL, OTHER
    is_primary          BOOLEAN DEFAULT FALSE,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_contact_phones_contact ON contact_phones(contact_id);

-- ============================================================
-- TAGS — Flexible labeling system
-- ============================================================
-- Tags are per-workspace. A contact can have many tags.
-- ============================================================
CREATE TABLE tags (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                VARCHAR(100) NOT NULL,
    color               VARCHAR(7),                     -- hex color: "#3B82F6"

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Tag name must be unique within a workspace
    UNIQUE(workspace_id, name)
);

CREATE INDEX idx_tags_workspace ON tags(workspace_id);

-- ============================================================
-- CONTACT_TAGS — Many-to-many: contacts ←→ tags
-- ============================================================
CREATE TABLE contact_tags (
    contact_id          UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    tag_id              UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,

    PRIMARY KEY (contact_id, tag_id)
);

CREATE INDEX idx_contact_tags_contact ON contact_tags(contact_id);
CREATE INDEX idx_contact_tags_tag ON contact_tags(tag_id);

-- ============================================================
-- CUSTOM FIELDS — User-defined properties
-- ============================================================
-- Workspaces can define their own fields (e.g., "Preferred Language", "Budget").
-- These are schema definitions — the actual values are in custom_field_values.
-- ============================================================
CREATE TABLE custom_fields (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    name                VARCHAR(255) NOT NULL,
    field_type          VARCHAR(20) NOT NULL,            -- TEXT, NUMBER, DATE, SELECT, BOOLEAN, URL, EMAIL
    entity_type         VARCHAR(20) NOT NULL DEFAULT 'CONTACT',  -- CONTACT, COMPANY, DEAL
    description         VARCHAR(500),
    is_required         BOOLEAN DEFAULT FALSE,

    -- For SELECT type: list of options as JSON array ["Option 1", "Option 2"]
    options             JSONB,

    display_order       INTEGER DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(workspace_id, name, entity_type)
);

CREATE INDEX idx_custom_fields_workspace ON custom_fields(workspace_id, entity_type);

-- ============================================================
-- CUSTOM FIELD VALUES — Actual values for custom fields
-- ============================================================
-- Polymorphic: entity_type + entity_id point to any entity (contact, company, deal).
-- This avoids creating separate value tables for each entity type.
-- ============================================================
CREATE TABLE custom_field_values (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    custom_field_id     UUID NOT NULL REFERENCES custom_fields(id) ON DELETE CASCADE,

    entity_type         VARCHAR(20) NOT NULL,            -- CONTACT, COMPANY, DEAL
    entity_id           UUID NOT NULL,                   -- the ID of the contact/company/deal

    value               TEXT,                            -- stored as string, parsed based on field_type

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(custom_field_id, entity_type, entity_id)
);

CREATE INDEX idx_cfv_workspace ON custom_field_values(workspace_id);
CREATE INDEX idx_cfv_entity ON custom_field_values(entity_type, entity_id);
