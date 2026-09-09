-- ============================================================
-- Cast & director support, sourced from the TMDb credits API
-- (see backend/scripts/backfill_movie_credits.py for the data fill)
-- ============================================================

CREATE TABLE movie_people (
    id             BIGSERIAL PRIMARY KEY,
    tmdb_person_id INTEGER NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE movie_cast (
    movie_id   BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    person_id  BIGINT NOT NULL REFERENCES movie_people(id) ON DELETE CASCADE,
    cast_order SMALLINT NOT NULL,  -- TMDb billing order, 0 = top-billed
    PRIMARY KEY (movie_id, person_id)
);

CREATE INDEX idx_movie_cast_movie_order ON movie_cast (movie_id, cast_order);

ALTER TABLE movies ADD COLUMN director_id BIGINT REFERENCES movie_people(id);
