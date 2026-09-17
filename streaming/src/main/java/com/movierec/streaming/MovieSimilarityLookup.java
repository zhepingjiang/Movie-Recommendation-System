package com.movierec.streaming;

import java.io.Serializable;
import java.util.List;

/**
 * Looks up a movie's cached content-based neighbors. Abstracted behind an interface (rather than
 * calling JDBC directly from {@link UserWindowedCandidateScorer}) so tests can inject a fake
 * in-memory implementation instead of hitting Postgres.
 */
public interface MovieSimilarityLookup extends Serializable {

    List<ScoredNeighbor> findTopSimilarMovies(long movieId) throws Exception;
}
