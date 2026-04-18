-- pgvector: enables `vector(n)` column types for AI embeddings
-- (knowledge-base RAG, fuzzy contact deduplication).
-- IF NOT EXISTS makes it safe to re-run against DBs where the extension
-- was already created manually.
CREATE EXTENSION IF NOT EXISTS vector;
