package com.movierec.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CandidateScorerTest {

    @Test
    void averagesSimilarityAcrossAllViewedMoviesThatNeighborACandidate() {
        Set<Long> viewedMovieIds = Set.of(1L, 2L);
        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie = Map.of(
                1L, List.of(new ScoredNeighbor(10L, 0.8), new ScoredNeighbor(20L, 0.4)),
                2L, List.of(new ScoredNeighbor(10L, 0.6)));

        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(viewedMovieIds, neighborsByViewedMovie);

        assertEquals(0.7, scoredCandidates.get(10L), 1e-9);
        assertEquals(0.4, scoredCandidates.get(20L), 1e-9);
    }

    @Test
    void excludesCandidatesAlreadyInTheViewedSet() {
        Set<Long> viewedMovieIds = Set.of(1L, 2L);
        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie =
                Map.of(1L, List.of(new ScoredNeighbor(2L, 0.9), new ScoredNeighbor(30L, 0.5)));

        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(viewedMovieIds, neighborsByViewedMovie);

        assertFalse(scoredCandidates.containsKey(2L));
        assertEquals(0.5, scoredCandidates.get(30L), 1e-9);
    }

    @Test
    void aMovieWithNoNeighborsIsAbsentNotZero() {
        Set<Long> viewedMovieIds = Set.of(1L);
        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie = Map.of(1L, List.of());

        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(viewedMovieIds, neighborsByViewedMovie);

        assertTrue(scoredCandidates.isEmpty());
    }
}
