-- ------------------------------------------------------------
-- User <-> Genre (many-to-many): genres picked during onboarding,
-- used as the cold-start signal before enough interaction data exists.
-- ------------------------------------------------------------
CREATE TABLE user_genres (
    user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    genre_id  BIGINT NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, genre_id)
);

CREATE INDEX idx_user_genres_genre ON user_genres (genre_id);
