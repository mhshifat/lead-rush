-- ============================================================
-- V6: Add missing updated_at column to email_link_clicks
-- ============================================================
-- email_link_clicks extends TenantEntity → BaseEntity which has updated_at.
-- V5 missed this column.
-- ============================================================

ALTER TABLE email_link_clicks ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
