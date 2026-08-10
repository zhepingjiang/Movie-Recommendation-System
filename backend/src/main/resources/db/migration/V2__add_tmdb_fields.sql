-- ============================================================
-- Add TMDb-sourced fields to movies
-- ============================================================

ALTER TABLE movies ADD COLUMN tmdb_id INTEGER UNIQUE;
ALTER TABLE movies ADD COLUMN poster_url TEXT;

-- vote_average from TMDb can reach 10.00; NUMERIC(3,2) tops out at 9.99.
ALTER TABLE movies ALTER COLUMN average_rating TYPE NUMERIC(4,2);
