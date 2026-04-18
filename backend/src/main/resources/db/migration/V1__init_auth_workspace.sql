-- ============================================================
-- V1: Foundation tables — Users, Workspaces, Memberships
-- ============================================================
-- This is the FIRST Flyway migration. It creates the core tables
-- needed for authentication and multi-tenancy.
--
-- NAMING: All table and column names use snake_case.
-- Hibernate's CamelCaseToUnderscoresNamingStrategy auto-converts
-- Java's camelCase to match these names.
--
-- CONVENTION:
--   - Every table has: id (UUID PK), created_at, updated_at
--   - Tenant-scoped tables add: workspace_id (FK, NOT NULL)
--   - users + workspaces are GLOBAL (no workspace_id)
-- ============================================================

-- UUID generation (built into PostgreSQL 13+, but this ensures compatibility)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- USERS — Global table (not tenant-scoped)
-- ============================================================
-- A user can belong to multiple workspaces.
-- password_hash is NULLABLE because OAuth-only users don't have a password.
-- ============================================================
CREATE TABLE users (
    id                              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email                           VARCHAR(255) NOT NULL UNIQUE,
    password_hash                   VARCHAR(255),                       -- NULL for OAuth-only users
    name                            VARCHAR(255) NOT NULL,
    avatar_url                      VARCHAR(500),

    -- OAuth provider IDs (NULL if not linked)
    google_id                       VARCHAR(255) UNIQUE,
    github_id                       VARCHAR(255) UNIQUE,

    -- Which auth method was used to sign up
    primary_provider                VARCHAR(20) NOT NULL DEFAULT 'LOCAL',   -- LOCAL, GOOGLE, GITHUB

    -- Which auth method was used LAST (for "Last used" badge on login page)
    last_used_provider              VARCHAR(20),

    -- Account activation
    email_verified                  BOOLEAN NOT NULL DEFAULT FALSE,
    last_activation_email_sent_at   TIMESTAMP,

    -- System role (not workspace role — this is platform-level)
    system_role                     VARCHAR(20) NOT NULL DEFAULT 'USER',    -- USER, SUPER_ADMIN

    last_login_at                   TIMESTAMP,
    created_at                      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index for login by email (most common query)
CREATE INDEX idx_users_email ON users(email);

-- ============================================================
-- ACTIVATION TOKENS — For email verification
-- ============================================================
CREATE TABLE activation_tokens (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token               VARCHAR(255) NOT NULL UNIQUE,
    expires_at          TIMESTAMP NOT NULL,
    used                BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activation_tokens_token ON activation_tokens(token);

-- ============================================================
-- REFRESH TOKENS — For JWT token rotation
-- ============================================================
-- When a user refreshes their access token, the old refresh token
-- is revoked and a new one is issued. This prevents replay attacks.
-- ============================================================
CREATE TABLE refresh_tokens (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token               VARCHAR(500) NOT NULL UNIQUE,
    expires_at          TIMESTAMP NOT NULL,
    revoked             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ============================================================
-- WORKSPACES — Global table (this IS the tenant)
-- ============================================================
-- Every workspace is a separate tenant. All business data
-- (contacts, deals, sequences) belongs to a workspace.
-- ============================================================
CREATE TABLE workspaces (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                VARCHAR(255) NOT NULL,
    slug                VARCHAR(100) NOT NULL UNIQUE,        -- URL-friendly: "acme-corp"
    logo_url            VARCHAR(500),

    -- Settings stored as JSON (flexible, avoids schema changes for every setting)
    settings            JSONB DEFAULT '{}',

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspaces_slug ON workspaces(slug);

-- ============================================================
-- WORKSPACE MEMBERSHIPS — Links users to workspaces with roles
-- ============================================================
-- A user can be a member of multiple workspaces.
-- Each membership has a role (OWNER, ADMIN, MANAGER, MEMBER, VIEWER).
--
-- This is the junction table for the many-to-many relationship:
--   users ←→ workspace_memberships ←→ workspaces
-- ============================================================
CREATE TABLE workspace_memberships (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    role                VARCHAR(20) NOT NULL DEFAULT 'MEMBER',   -- OWNER, ADMIN, MANAGER, MEMBER, VIEWER

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    -- A user can only be a member of a workspace ONCE
    UNIQUE(user_id, workspace_id)
);

-- Fast lookup: "what workspaces does this user belong to?"
CREATE INDEX idx_memberships_user ON workspace_memberships(user_id);
-- Fast lookup: "who are the members of this workspace?"
CREATE INDEX idx_memberships_workspace ON workspace_memberships(workspace_id);
