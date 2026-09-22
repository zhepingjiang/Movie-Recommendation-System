package com.movierec.streaming.dto;

/**
 * A single scored candidate for a user, shaped to match {@code recommendation_cache}'s columns
 * ({@code movie_id}, {@code score}) -- {@code user_id} is deliberately not carried here since a
 * {@link ScoredCandidate} only ever exists inside a {@link ScoredCandidateBatch}, which already
 * scopes every candidate it holds to one user; duplicating that id on each candidate would just be
 * a second, unenforced copy of the batch's own field. Always carried inside a {@link
 * ScoredCandidateBatch} -- one window firing's full top-N for a user -- rather than emitted
 * individually, since {@link com.movierec.streaming.sink.RecommendationCacheJdbcSink} writes a
 * user's candidates as one all-or-nothing batch.
 */
public record ScoredCandidate(long movieId, double score) {
}
