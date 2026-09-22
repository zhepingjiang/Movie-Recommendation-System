package com.movierec.streaming.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.movierec.streaming.dto.ScoredNeighbor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CandidateScorerTest {

    private static final long REFERENCE_TIMESTAMP_MILLIS = 1_000_000L;
    private static final Duration HALF_LIFE = Duration.ofMinutes(3);

    @Test
    void averagesSimilarityAcrossAllViewedMoviesThatNeighborACandidateWhenAgesAreEqual() {
        // Both viewed at the reference timestamp itself -- zero age, so decay weight is 1 for
        // both and this reduces to a plain average.
        Map<Long, Long> viewedMovieIdToViewTimestampMillis = Map.of(1L, REFERENCE_TIMESTAMP_MILLIS, 2L, REFERENCE_TIMESTAMP_MILLIS);
        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie = Map.of(
                1L, List.of(new ScoredNeighbor(10L, 0.8), new ScoredNeighbor(20L, 0.4)),
                2L, List.of(new ScoredNeighbor(10L, 0.6)));

        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(
                viewedMovieIdToViewTimestampMillis, neighborsByViewedMovie, REFERENCE_TIMESTAMP_MILLIS, HALF_LIFE);

        assertEquals(0.7, scoredCandidates.get(10L), 1e-9);
        assertEquals(0.4, scoredCandidates.get(20L), 1e-9);
    }

    @Test
    void olderViewedMoviesContributeLessThanFresherOnes() {
        // Movie 1 is one full half-life older than movie 2 (which was just viewed), so its
        // contribution should be weighted half as heavily.
        long oneHalfLifeAgoMillis = REFERENCE_TIMESTAMP_MILLIS - HALF_LIFE.toMillis();
        Map<Long, Long> viewedMovieIdToViewTimestampMillis = Map.of(1L, oneHalfLifeAgoMillis, 2L, REFERENCE_TIMESTAMP_MILLIS);
        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie =
                Map.of(1L, List.of(new ScoredNeighbor(10L, 0.8)), 2L, List.of(new ScoredNeighbor(10L, 0.4)));

        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(
                viewedMovieIdToViewTimestampMillis, neighborsByViewedMovie, REFERENCE_TIMESTAMP_MILLIS, HALF_LIFE);

        // (0.8*0.5 + 0.4*1) / (0.5 + 1) = 0.8 / 1.5 -- pulled toward the fresher movie's score,
        // not the unweighted average of 0.6.
        assertEquals(0.8 / 1.5, scoredCandidates.get(10L), 1e-9);
    }

    @Test
    void excludesCandidatesAlreadyInTheViewedSet() {
        Map<Long, Long> viewedMovieIdToViewTimestampMillis = Map.of(1L, REFERENCE_TIMESTAMP_MILLIS, 2L, REFERENCE_TIMESTAMP_MILLIS);
        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie =
                Map.of(1L, List.of(new ScoredNeighbor(2L, 0.9), new ScoredNeighbor(30L, 0.5)));

        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(
                viewedMovieIdToViewTimestampMillis, neighborsByViewedMovie, REFERENCE_TIMESTAMP_MILLIS, HALF_LIFE);

        assertFalse(scoredCandidates.containsKey(2L));
        assertEquals(0.5, scoredCandidates.get(30L), 1e-9);
    }

    @Test
    void aMovieWithNoNeighborsIsAbsentNotZero() {
        Map<Long, Long> viewedMovieIdToViewTimestampMillis = Map.of(1L, REFERENCE_TIMESTAMP_MILLIS);
        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie = Map.of(1L, List.of());

        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(
                viewedMovieIdToViewTimestampMillis, neighborsByViewedMovie, REFERENCE_TIMESTAMP_MILLIS, HALF_LIFE);

        assertTrue(scoredCandidates.isEmpty());
    }
}
