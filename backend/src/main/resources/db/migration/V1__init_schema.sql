-- ============================================================
-- Movie Recommendation Platform - PostgreSQL Schema
-- ============================================================

-- ------------------------------------------------------------
-- 1. Genres
-- ------------------------------------------------------------
CREATE TABLE genres (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
-- 2. Tags (free-form, admin-manageable, extensible)
-- ------------------------------------------------------------
CREATE TABLE tags (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
-- 3. Movies
-- ------------------------------------------------------------
CREATE TABLE movies (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    release_date  DATE,
    duration_min  INTEGER,
    -- Denormalized fields, maintained asynchronously (not in the write path of a rating submission).
    -- Backed by a Redis sorted-set cache (e.g. movies:by_rating) for hot read paths like "top rated".
    average_rating NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count   INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_movies_release_date ON movies (release_date);

-- ------------------------------------------------------------
-- 4. Movie <-> Genre (many-to-many)
-- ------------------------------------------------------------
CREATE TABLE movie_genres (
    movie_id  BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    genre_id  BIGINT NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (movie_id, genre_id)
);

CREATE INDEX idx_movie_genres_genre ON movie_genres (genre_id);

-- ------------------------------------------------------------
-- 5. Movie <-> Tag (many-to-many)
-- ------------------------------------------------------------
CREATE TABLE movie_tags (
    movie_id  BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    tag_id    BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (movie_id, tag_id)
);

CREATE INDEX idx_movie_tags_tag ON movie_tags (tag_id);

-- ------------------------------------------------------------
-- 6. Theaters
-- ------------------------------------------------------------
CREATE TABLE theaters (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(500),
    city        VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
-- 7. Showtimes
-- ------------------------------------------------------------
CREATE TABLE showtimes (
    id              BIGSERIAL PRIMARY KEY,
    movie_id        BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    theater_id      BIGINT NOT NULL REFERENCES theaters(id) ON DELETE CASCADE,
    start_time      TIMESTAMPTZ NOT NULL,
    seats_available INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_showtimes_movie_time ON showtimes (movie_id, start_time);
CREATE INDEX idx_showtimes_theater_time ON showtimes (theater_id, start_time);

-- ------------------------------------------------------------
-- 8. Users
-- ------------------------------------------------------------
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
-- 9. Ratings (explicit signal)
-- ------------------------------------------------------------
CREATE TABLE ratings (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id    BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    score       SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, movie_id)
);

-- ------------------------------------------------------------
-- 10. User Interactions (implicit signal - main recommendation input)
-- ------------------------------------------------------------
CREATE TABLE user_interactions (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id      BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    interaction_type VARCHAR(30) NOT NULL, -- e.g. VIEW, CLICK, WATCHLIST_ADD
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_interactions_user_time ON user_interactions (user_id, created_at DESC);
CREATE INDEX idx_user_interactions_movie ON user_interactions (movie_id);

-- ------------------------------------------------------------
-- 11. Recommendation Cache (offline batch-computed baseline, supports A/B testing)
-- ------------------------------------------------------------
CREATE TABLE recommendation_cache (
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id     BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    model_version VARCHAR(30) NOT NULL,   -- e.g. 'cf_v1', 'cf_v2_experiment'
    score        NUMERIC(6,4) NOT NULL,   -- model's confidence/relevance score
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, movie_id, model_version)
);

CREATE INDEX idx_recommendation_cache_user_model_score
    ON recommendation_cache (user_id, model_version, score DESC);
