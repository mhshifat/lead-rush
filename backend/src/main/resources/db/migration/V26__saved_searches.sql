-- Saved LinkedIn searches — lets the extension highlight "new since last visit"
-- profiles on return trips to the same search URL.

CREATE TABLE saved_searches (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    known_profile_urls TEXT,
    last_checked_at TIMESTAMP,
    created_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tenant filter is applied on every query via Hibernate @Filter; the composite
-- index makes the (workspaceId, url) lookup that powers /check cheap.
CREATE INDEX idx_saved_searches_workspace ON saved_searches(workspace_id);
CREATE INDEX idx_saved_searches_workspace_url ON saved_searches(workspace_id, url);
