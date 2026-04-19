-- Background company-crawl queue + discovered-person cache.
--
-- company_crawls        → one row per (workspace, domain) we've seen. Acts as both queue
--                         (status=PENDING) and cache key (status=COMPLETED, last_crawled_at).
-- company_crawl_persons → every Person we extracted during a crawl. Joined by
--                         workspace + domain when enriching later contacts at the same company.
--
-- The sweep job populates company_crawls with PENDING rows for every domain that has
-- contacts but hasn't been crawled in the last 30 days. The worker job pulls PENDING rows,
-- runs the sitemap crawler, and writes discovered persons + flips status to COMPLETED.

CREATE TABLE company_crawls (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    domain VARCHAR(255) NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',     -- PENDING, IN_PROGRESS, COMPLETED, FAILED
    attempt_count INT NOT NULL DEFAULT 0,
    persons_found INT NOT NULL DEFAULT 0,
    last_error TEXT,

    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP,
    last_crawled_at TIMESTAMP,                         -- set on COMPLETED

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (workspace_id, domain)
);

CREATE INDEX idx_company_crawls_due
    ON company_crawls (status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_company_crawls_workspace_domain
    ON company_crawls (workspace_id, domain);

CREATE TABLE company_crawl_persons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    domain VARCHAR(255) NOT NULL,

    name VARCHAR(255),
    email VARCHAR(320),
    job_title VARCHAR(255),
    source_url TEXT,
    source_adapter VARCHAR(50),                        -- SITEMAP_CRAWLER, etc.

    discovered_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_company_crawl_persons_lookup
    ON company_crawl_persons (workspace_id, domain);

CREATE INDEX idx_company_crawl_persons_name
    ON company_crawl_persons (workspace_id, domain, lower(name));
