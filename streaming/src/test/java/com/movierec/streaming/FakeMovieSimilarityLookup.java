package com.movierec.streaming;

import java.util.List;
import java.util.Map;

/** In-memory {@link MovieSimilarityLookup} for tests, avoiding a real Postgres connection. */
class FakeMovieSimilarityLookup implements MovieSimilarityLookup {

    private final Map<Long, List<ScoredNeighbor>> neighborsByMovieId;

    FakeMovieSimilarityLookup(Map<Long, List<ScoredNeighbor>> neighborsByMovieId) {
        this.neighborsByMovieId = neighborsByMovieId;
    }

    @Override
    public List<ScoredNeighbor> findTopSimilarMovies(long movieId) {
        return neighborsByMovieId.getOrDefault(movieId, List.of());
    }
}
