-- ============================================================
-- V3: Add missing updated_at columns to child tables
-- ============================================================
-- ContactEmail, ContactPhone, and Tags extend TenantEntity → BaseEntity
-- which has updated_at. The original V2 migration missed these columns.
-- ============================================================

ALTER TABLE contact_emails ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE contact_phones ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE tags ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE contact_tags ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW();
