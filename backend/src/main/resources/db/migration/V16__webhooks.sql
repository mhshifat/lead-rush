-- ============================================================
-- V16: Outbound webhooks
-- ============================================================
-- Customers subscribe to event types by URL. When a subscribed event fires,
-- we persist a delivery row and a scheduled worker POSTs it to the endpoint
-- with an HMAC-SHA256 signature (Stripe-style: t=<timestamp>,v1=<sig>).
--
-- Deliveries are retried with exponential backoff; after MAX_ATTEMPTS we give
-- up and flip the endpoint's consecutive_failures counter. Endpoints that
-- cross the threshold get auto-disabled.
-- ============================================================

CREATE TABLE webhook_endpoints (
    id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id             UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    url                      VARCHAR(2000) NOT NULL,
    description              VARCHAR(500),

    -- HMAC secret, shown to the user ONCE on creation. Stored as plaintext
    -- because we need to sign with it on every delivery (no way around that).
    -- If that scares anyone: a leak of the DB already means worse problems.
    secret                   VARCHAR(128) NOT NULL,

    -- Comma-separated event types the customer wants: "contact.created,deal.moved"
    -- An asterisk "*" matches everything.
    events                   TEXT NOT NULL,

    enabled                  BOOLEAN NOT NULL DEFAULT TRUE,

    -- Auto-disable after this many consecutive failures
    consecutive_failures     INTEGER NOT NULL DEFAULT 0,
    disabled_reason          VARCHAR(500),

    last_success_at          TIMESTAMP,
    last_failure_at          TIMESTAMP,

    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_endpoints_workspace ON webhook_endpoints(workspace_id, created_at DESC);

-- ============================================================
CREATE TABLE webhook_deliveries (
    id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id             UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    endpoint_id              UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,

    event_type               VARCHAR(100) NOT NULL,
    event_id                 UUID NOT NULL,        -- stable ID for the event, used for idempotency
    payload                  JSONB NOT NULL,

    -- PENDING → IN_PROGRESS → SUCCEEDED | FAILED | ABANDONED
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    attempt_count            INTEGER NOT NULL DEFAULT 0,
    next_attempt_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    last_status_code         INTEGER,
    last_error               TEXT,
    last_attempt_at          TIMESTAMP,

    delivered_at             TIMESTAMP,
    abandoned_at             TIMESTAMP,

    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Scheduler hot path — picks up rows ready to retry
CREATE INDEX idx_webhook_deliveries_due
    ON webhook_deliveries(status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_webhook_deliveries_endpoint
    ON webhook_deliveries(endpoint_id, created_at DESC);

CREATE INDEX idx_webhook_deliveries_workspace
    ON webhook_deliveries(workspace_id, created_at DESC);
