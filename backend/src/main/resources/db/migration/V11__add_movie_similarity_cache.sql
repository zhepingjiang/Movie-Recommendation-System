-- ============================================================
-- Content-based item-item similarity cache (offline batch-computed, mirrors recommendation_cache)
-- ============================================================
CREATE TABLE movie_similarity_cache (
    movie_id         BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    similar_movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    model_version    VARCHAR(30) NOT NULL,   -- e.g. 'content_v1'
    score            NUMERIC(6,4) NOT NULL,  -- cosine similarity, 0-1
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (movie_id, similar_movie_id, model_version)
);

CREATE INDEX idx_movie_similarity_cache_movie_model_score
    ON movie_similarity_cache (movie_id, model_version, score DESC);
