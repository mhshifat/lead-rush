-- ============================================================
-- V25: Contact-email provenance
-- ============================================================
-- Adds a `source` column to contact_emails so we can tell at a glance whether
-- an address came from Hunter, PDL, a pattern guess, the user, etc. Used by
-- the contact detail UI to render "Hunter — verified" / "Pattern guess —
-- unverified" side by side on the contact page.
--
-- Values are uppercase provider-keys matching EnrichmentProviderAdapter.providerKey()
-- ('HUNTER', 'PDL', 'PATTERN_CACHE', 'GITHUB', 'SITEMAP_CRAWLER', 'WEBSITE_SCRAPER',
-- 'COMPANY_CRAWL_CACHE', 'MOCK', 'EXTENSION'). Null means the row was entered
-- manually by a user.
-- ============================================================

ALTER TABLE contact_emails
    ADD COLUMN IF NOT EXISTS source VARCHAR(50);

-- No constraint on values — the app enum can evolve faster than migrations.
