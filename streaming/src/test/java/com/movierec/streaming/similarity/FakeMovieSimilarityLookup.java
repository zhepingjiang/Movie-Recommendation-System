package com.movierec.streaming.similarity;

import com.movierec.streaming.dto.ScoredNeighbor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public Map<Long, List<ScoredNeighbor>> findTopSimilarMovies(Set<Long> movieIds) {
        Map<Long, List<ScoredNeighbor>> result = new HashMap<>();
        for (Long movieId : movieIds) {
            result.put(movieId, neighborsByMovieId.getOrDefault(movieId, List.of()));
        }
        return result;
    }
}
