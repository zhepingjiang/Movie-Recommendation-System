package com.movierec.streaming.scoring;

import com.movierec.streaming.dto.ScoredNeighbor;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Windowed equivalent of {@code content_based_training.score_candidates}: scores every movie
 * similar to something in {@code viewedMovieIdToViewTimestampMillis} by the similarity-weighted
 * average across all of the window's viewed movies it neighbors, where each viewed movie's
 * contribution is discounted by how long ago (relative to {@code referenceTimestampMillis}) it
 * was viewed -- an exponential decay with the given half-life, rather than every viewed movie
 * counting equally regardless of age. Movies already viewed are excluded from the candidate set.
 * A movie with no similarity relationship to anything viewed is simply absent from the result, not
 * scored 0.
 */
public final class CandidateScorer {

    private CandidateScorer() {
    }

    public static Map<Long, Double> scoreCandidates(
            Map<Long, Long> viewedMovieIdToViewTimestampMillis,
            Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie,
            long referenceTimestampMillis,
            Duration halfLife) {
        Set<Long> viewedMovieIds = viewedMovieIdToViewTimestampMillis.keySet();
        double halfLifeMillis = halfLife.toMillis();

        Map<Long, Double> candidateWeightedScoreSum = new HashMap<>();
        Map<Long, Double> candidateWeightSum = new HashMap<>();
        viewedMovieIdToViewTimestampMillis.forEach((viewedMovieId, viewTimestampMillis) -> {
            // FIX: previously every viewed movie counted with equal (weight=1) contribution
            // regardless of age, so a movie's influence didn't fade -- it just vanished outright
            // the instant it aged out of the window. Exponential decay makes that a smooth
            // fade-out instead of a step function at the window boundary.
            long ageMillis = referenceTimestampMillis - viewTimestampMillis;
            double decayWeight = Math.pow(2, -ageMillis / halfLifeMillis);

            for (ScoredNeighbor neighbor : neighborsByViewedMovie.getOrDefault(viewedMovieId, List.of())) {
                if (!viewedMovieIds.contains(neighbor.similarMovieId())) {
                    // FIX: weighted sum/weighted count (not a plain sum/count) so more-recent
                    // viewed movies pull the average further than older ones.
                    candidateWeightedScoreSum.merge(neighbor.similarMovieId(), neighbor.score() * decayWeight, Double::sum);
                    candidateWeightSum.merge(neighbor.similarMovieId(), decayWeight, Double::sum);
                }
            }
        });

        Map<Long, Double> scoredCandidates = new HashMap<>();
        candidateWeightedScoreSum.forEach((candidateMovieId, weightedScoreSum) -> scoredCandidates.put(
                candidateMovieId, weightedScoreSum / candidateWeightSum.get(candidateMovieId)));
        return scoredCandidates;
    }
}
