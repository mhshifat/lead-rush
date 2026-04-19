-- ============================================================
-- V24: Remove chat feature
-- ============================================================
-- Chat (both the in-app inbox and the embeddable widget) has been replaced by
-- a separate product — Converse AI. This migration drops the three chat tables
-- created in V17 and their dependent objects.
--
-- Order matters: drop FK-referencing tables before their parents.
-- ============================================================

DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS chat_conversations CASCADE;
DROP TABLE IF EXISTS chat_widgets CASCADE;
