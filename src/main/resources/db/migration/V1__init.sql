CREATE EXTENSION IF NOT EXISTS vector;

-- One row per ingested documentation file and chunk table (embedding model).
-- The blob SHA lets re-ingestion skip unchanged files.
CREATE TABLE ingested_file (
    chunk_table  TEXT        NOT NULL,
    path         TEXT        NOT NULL,
    blob_sha     TEXT        NOT NULL,
    title        TEXT,
    url          TEXT,
    kit          TEXT,
    chunk_count  INT         NOT NULL,
    source       TEXT        NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chunk_table, path)
);

-- The chunk tables themselves are created at startup by ChunkTableInitializer because their name and
-- vector dimension depend on the configured embedding model.
