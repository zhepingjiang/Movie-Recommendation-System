package com.movierec.streaming.similarity;

import com.movierec.streaming.dto.ScoredNeighbor;
import com.movierec.streaming.scoring.UserWindowedCandidateScorer;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks up cached content-based neighbors for a batch of movies at once (a window's full set of
 * distinct viewed movies, in practice) -- batch-shaped rather than one-movie-at-a-time so an
 * implementation can serve cache hits locally and fetch every miss in a single round trip, instead
 * of one round trip per movie. Abstracted behind an interface (rather than calling JDBC directly
 * from {@link UserWindowedCandidateScorer}) so tests can inject a fake in-memory implementation
 * instead of hitting Postgres.
 */
public interface MovieSimilarityLookup extends Serializable {

    // FIX: was a single-movieId signature, forcing one JDBC round trip per viewed movie per
    // window firing across heavily-overlapping sliding windows. Batch-shaped so a caller can look
    // up a whole window's distinct movies in one call.
    Map<Long, List<ScoredNeighbor>> findTopSimilarMovies(Set<Long> movieIds) throws Exception;
}
