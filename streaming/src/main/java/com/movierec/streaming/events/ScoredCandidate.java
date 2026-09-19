package com.movierec.streaming.events;

/**
 * A single scored candidate for a user, shaped to match {@code recommendation_cache}'s columns
 * ({@code user_id}, {@code movie_id}, {@code score}). Always carried inside a {@link
 * ScoredCandidateBatch} -- one window firing's full top-N for a user -- rather than emitted
 * individually, since {@link com.movierec.streaming.sink.RecommendationCacheJdbcSink} writes a
 * user's candidates as one all-or-nothing batch.
 */
public record ScoredCandidate(long userId, long movieId, double score) {
}
