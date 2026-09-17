package com.movierec.streaming.similarity;

import com.movierec.streaming.events.ScoredNeighbor;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link MovieSimilarityLookup} for tests, avoiding a real Postgres connection. Public
 * since it's used from other packages' tests (e.g. {@code scoring}).
 */
public class FakeMovieSimilarityLookup implements MovieSimilarityLookup {

    private final Map<Long, List<ScoredNeighbor>> neighborsByMovieId;

    public FakeMovieSimilarityLookup(Map<Long, List<ScoredNeighbor>> neighborsByMovieId) {
        this.neighborsByMovieId = neighborsByMovieId;
    }

    @Override
    public List<ScoredNeighbor> findTopSimilarMovies(long movieId) {
        return neighborsByMovieId.getOrDefault(movieId, List.of());
    }
}
