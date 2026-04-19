-- Follow-up to V21: CompanyCrawlPerson extends TenantEntity which inherits created_at
-- and updated_at from BaseEntity. V21 defined only discovered_at, so Hibernate's
-- ddl-auto=validate rejects the schema at boot. Add the missing columns here.

ALTER TABLE company_crawl_persons
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
