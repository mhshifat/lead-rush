-- ============================================================
-- V13: Conditional branching on sequence steps
-- ============================================================
-- Until we have IMAP + reply detection, we can't branch on EMAIL_REPLIED.
-- But we CAN branch on what the tracking pixel + click redirect tell us:
--   - skip_if_previous_opened    : skip this step if the contact has opened ANY earlier email in this sequence
--   - skip_if_previous_clicked   : skip this step if the contact has clicked ANY earlier email's link in this sequence
--
-- Use case: a typical three-email drip that stops pestering a lead who already engaged.
-- ============================================================

ALTER TABLE sequence_steps
    ADD COLUMN skip_if_previous_opened  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN skip_if_previous_clicked BOOLEAN NOT NULL DEFAULT FALSE;
