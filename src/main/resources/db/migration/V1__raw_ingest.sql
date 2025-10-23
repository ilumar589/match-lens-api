CREATE TABLE fd_raw_ingest (
                               id               BIGSERIAL PRIMARY KEY,
                               source           TEXT        NOT NULL,          -- 'football-data.org'
                               endpoint         TEXT        NOT NULL,          -- '/competitions/{code}'
                               external_key     TEXT        NOT NULL,          -- e.g. 'PL'
                               fetched_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                               last_modified    TIMESTAMPTZ,
                               payload          JSONB                     -- full upstream JSON
);

-- Avoid duplicates of identical payloads per key
CREATE UNIQUE INDEX fd_raw_ingest_dedup
    ON fd_raw_ingest (source, endpoint, external_key);

-- For ad-hoc searching
CREATE INDEX fd_raw_ingest_payload_gin ON fd_raw_ingest USING GIN (payload jsonb_path_ops);

CREATE INDEX IF NOT EXISTS fd_raw_ingest_recent_idx
    ON fd_raw_ingest (source, endpoint, external_key, fetched_at DESC);
