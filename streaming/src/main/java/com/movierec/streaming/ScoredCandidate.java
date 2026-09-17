package com.movierec.streaming;

/**
 * A per-user recommendation candidate produced by a window's aggregation, shaped to match
 * {@code recommendation_cache}'s columns ({@code user_id}, {@code movie_id}, {@code score}) for
 * a future JDBC sink.
 */
public record ScoredCandidate(long userId, long movieId, double score) {
}
