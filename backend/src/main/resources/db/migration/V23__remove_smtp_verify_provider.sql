-- The SMTP_VERIFY enrichment adapter was removed — aggressive SMTP RCPT TO probing
-- carries too much deliverability risk (server IPs get flagged by Spamhaus, tanking
-- real sequence-send deliverability) and cloud providers block outbound port 25 anyway.
--
-- Clean up any auto-created provider rows so the settings page doesn't display a
-- dangling entry for an adapter that no longer exists.

DELETE FROM enrichment_providers WHERE provider_key = 'SMTP_VERIFY';
DELETE FROM enrichment_results WHERE provider_key = 'SMTP_VERIFY';
