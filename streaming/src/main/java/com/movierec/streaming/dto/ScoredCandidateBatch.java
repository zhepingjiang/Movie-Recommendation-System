package com.movierec.streaming.dto;

import java.util.List;

/**
 * One user's complete top-N output from a single window firing -- the unit {@link
 * com.movierec.streaming.sink.RecommendationCacheJdbcSink} writes atomically (delete this user's
 * prior {@code nearline_v1} rows, insert this batch) so a window replaces, rather than
 * accumulates on top of, the previous firing's candidates for that user.
 *
 * <p>{@code windowEndEpochMilli} (not wall-clock time) becomes every row's {@code generated_at}
 * -- same determinism reasoning as the recency decay in {@link
 * com.movierec.streaming.scoring.UserWindowedCandidateScorer}: replaying the same window
 * reproduces the same rows, timestamp included.
 */
public record ScoredCandidateBatch(long userId, long windowEndEpochMilli, List<ScoredCandidate> candidates) {
}
