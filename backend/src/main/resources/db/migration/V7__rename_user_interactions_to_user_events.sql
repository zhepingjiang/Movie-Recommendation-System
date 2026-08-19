-- Renamed to user_events: this table now also receives rows from the Kafka
-- movie-view-events consumer, not just in-process writes.
ALTER TABLE user_interactions RENAME TO user_events;
ALTER TABLE user_events RENAME COLUMN interaction_type TO event_type;

-- GET /api/movies/{id} is public, so anonymous views have no user_id.
ALTER TABLE user_events ALTER COLUMN user_id DROP NOT NULL;

ALTER INDEX idx_user_interactions_user_time RENAME TO idx_user_events_user_time;
ALTER INDEX idx_user_interactions_movie RENAME TO idx_user_events_movie;
