-- One row per ingestion run (app, Job or CronJob), so every app pod can report index freshness,
-- even though short-lived Job pods are never scraped by Prometheus.
CREATE TABLE ingestion_run (
    id            BIGSERIAL PRIMARY KEY,
    chunk_table   TEXT        NOT NULL,
    source        TEXT,
    started_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    success       BOOLEAN     NOT NULL,
    added         INT         NOT NULL DEFAULT 0,
    updated       INT         NOT NULL DEFAULT 0,
    deleted       INT         NOT NULL DEFAULT 0,
    failed        INT         NOT NULL DEFAULT 0,
    total_chunks  INT         NOT NULL DEFAULT 0,
    error         TEXT
);

CREATE INDEX ingestion_run_table_finished ON ingestion_run (chunk_table, finished_at DESC);
