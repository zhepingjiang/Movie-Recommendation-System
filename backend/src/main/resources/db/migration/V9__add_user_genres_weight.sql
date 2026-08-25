-- Affinity weight per (user, genre) pick, plus freshness timestamps. Every row is seeded
-- at weight=1.0 today since the onboarding UI only supports a flat select/deselect; this
-- makes room for either a future intensity picker or behavior-driven re-weighting without
-- another migration.
ALTER TABLE user_genres
    ADD COLUMN weight     NUMERIC(6,4) NOT NULL DEFAULT 1.0,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
