CREATE TABLE fd_competition (
                                id               BIGINT PRIMARY KEY,     -- from API (e.g., 2021)
                                code             TEXT UNIQUE,
                                name             TEXT NOT NULL,
                                type             TEXT,                   -- 'LEAGUE', etc.
                                emblem           TEXT,                   -- store URI as text
                                area_id          BIGINT,
                                area_name        TEXT,
                                last_updated     TIMESTAMPTZ
);

CREATE TABLE fd_season (
                           id               BIGINT PRIMARY KEY,
                           competition_id   BIGINT NOT NULL REFERENCES fd_competition(id),
                           start_date       DATE,
                           end_date         DATE,
                           current_matchday INT,
                           stages           JSONB,                  -- keep as array until you know queries
                           winner_team_id   BIGINT                  -- nullable
);

CREATE TABLE fd_team (
                         id               BIGINT PRIMARY KEY,
                         name             TEXT NOT NULL,
                         short_name       TEXT,
                         tla              TEXT,
                         crest            TEXT,
                         founded          INT,
                         last_updated     TIMESTAMPTZ
);

-- Fixtures / matches (expand as needed)
CREATE TABLE fd_match (
                          id               BIGINT PRIMARY KEY,
                          competition_id   BIGINT NOT NULL REFERENCES fd_competition(id),
                          season_id        BIGINT REFERENCES fd_season(id),
                          utc_date         TIMESTAMPTZ,
                          status           TEXT,
                          matchday         INT,
                          home_team_id     BIGINT REFERENCES fd_team(id),
                          away_team_id     BIGINT REFERENCES fd_team(id),
                          score_json       JSONB,       -- flexible score breakdown
                          raw_ref_id       BIGINT REFERENCES fd_raw_ingest(id) -- lineage
);


CREATE INDEX fd_match_competition_date ON fd_match (competition_id, utc_date);
CREATE INDEX fd_match_team_date        ON fd_match (home_team_id, utc_date);
CREATE INDEX fd_match_score_gin        ON fd_match USING GIN (score_json jsonb_path_ops);
