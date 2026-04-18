-- ============================================================
-- V18: IMAP reply detection
-- ============================================================
-- To detect replies we open IMAP on each connected mailbox. SMTP credentials
-- usually work for IMAP too with a different host/port, so we add the IMAP
-- connection fields separately and reuse the encrypted SMTP password by default.
--
-- `imap_last_seen_uid` tracks the highest UID we've processed so each poll only
-- scans new messages — IMAP's SEARCH on UIDs is O(1) rather than scanning every
-- message in the INBOX.
-- ============================================================

ALTER TABLE mailboxes
    ADD COLUMN imap_host           VARCHAR(255),
    ADD COLUMN imap_port           INTEGER,
    ADD COLUMN imap_username       VARCHAR(255),
    ADD COLUMN reply_detection_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN imap_last_seen_uid  BIGINT,
    ADD COLUMN imap_last_error     TEXT,
    ADD COLUMN imap_last_polled_at TIMESTAMP;

-- Fast lookup for the polling job: which mailboxes need a scan?
CREATE INDEX idx_mailboxes_imap_enabled
    ON mailboxes(reply_detection_enabled)
    WHERE reply_detection_enabled = TRUE;
