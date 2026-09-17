package com.movierec.streaming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Windowed equivalent of {@code content_based_training.score_candidates}: scores every movie
 * similar to something in {@code viewedMovieIds} by the mean similarity across all of the
 * window's viewed movies it neighbors. Movies already in {@code viewedMovieIds} are excluded. A
 * movie with no similarity relationship to anything viewed is simply absent from the result, not
 * scored 0.
 */
public final class CandidateScorer {

    private CandidateScorer() {
    }

    public static Map<Long, Double> scoreCandidates(
            Set<Long> viewedMovieIds, Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie) {
        Map<Long, List<Double>> candidateSimilarityScores = new HashMap<>();
        for (Long viewedMovieId : viewedMovieIds) {
            for (ScoredNeighbor neighbor : neighborsByViewedMovie.getOrDefault(viewedMovieId, List.of())) {
                if (!viewedMovieIds.contains(neighbor.similarMovieId())) {
                    candidateSimilarityScores
                            .computeIfAbsent(neighbor.similarMovieId(), unused -> new ArrayList<>())
                            .add(neighbor.score());
                }
            }
        }

        Map<Long, Double> scoredCandidates = new HashMap<>();
        candidateSimilarityScores.forEach((candidateMovieId, similarityScores) -> {
            double averageSimilarityScore =
                    similarityScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            scoredCandidates.put(candidateMovieId, averageSimilarityScore);
        });
        return scoredCandidates;
    }
}
