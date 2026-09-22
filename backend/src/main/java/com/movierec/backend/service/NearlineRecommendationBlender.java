package com.movierec.backend.service;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Request-time merge of the streaming job's {@code nearline_v1} candidates into the offline
 * {@code blended_v1} list. Pure static math (no Spring/JPA), mirroring
 * recommendation/models/recommendation_blending.py's {@code blend_scores}: each model is min-max
 * normalized over its own candidate set, missing scores are 0-filled over the union, and the two
 * are mixed with a single confidence weight.
 *
 * <p>Where the offline blend's confidence axis is rating history ({@code effective_alpha}), this
 * one is freshness: nearline rows are only rewritten when a user is actively viewing, so an idle
 * user's last batch sits in {@code recommendation_cache} until the next offline blend wipes it.
 * Decaying by {@code generated_at} age makes that leftover batch fade out smoothly instead of
 * dominating the list long after the session that produced it ended.
 */
final class NearlineRecommendationBlender {

    private NearlineRecommendationBlender() {
    }

    /**
     * {@code maxNearlineWeight * 2^(-age / halfLife)}. A negative age (a {@code generated_at}
     * slightly ahead of this JVM's clock) is treated as zero so skew can never push the weight
     * above {@code maxNearlineWeight}.
     */
    static double nearlineWeight(Duration nearlineAge, Duration freshnessHalfLife, double maxNearlineWeight) {
        double ageMillis = Math.max(0, nearlineAge.toMillis());
        return maxNearlineWeight * Math.pow(2, -ageMillis / freshnessHalfLife.toMillis());
    }

    /**
     * Returns every candidate movie id from either model, highest merged score first (ties broken
     * by movie id so the order is deterministic).
     */
    static List<Long> rankMovieIds(
            Map<Long, Double> blendedScoresByMovieId,
            Map<Long, Double> nearlineScoresByMovieId,
            double nearlineWeight) {
        Map<Long, Double> normalizedBlendedScores = minMaxNormalize(blendedScoresByMovieId);
        Map<Long, Double> normalizedNearlineScores = minMaxNormalize(nearlineScoresByMovieId);

        Set<Long> candidateMovieIds = new HashSet<>(blendedScoresByMovieId.keySet());
        candidateMovieIds.addAll(nearlineScoresByMovieId.keySet());

        Map<Long, Double> mergedScoresByMovieId = new HashMap<>();
        for (Long candidateMovieId : candidateMovieIds) {
            double mergedScore = (1 - nearlineWeight) * normalizedBlendedScores.getOrDefault(candidateMovieId, 0.0)
                    + nearlineWeight * normalizedNearlineScores.getOrDefault(candidateMovieId, 0.0);
            mergedScoresByMovieId.put(candidateMovieId, mergedScore);
        }

        return mergedScoresByMovieId.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Same contract as the Python {@code min_max_normalize}: scales to [0, 1] within this one set,
     * and a tied set (including a single candidate) normalizes to 1.0 rather than dividing by zero.
     */
    static Map<Long, Double> minMaxNormalize(Map<Long, Double> scoresByMovieId) {
        if (scoresByMovieId.isEmpty()) {
            return Collections.emptyMap();
        }
        double lowestScore = Collections.min(scoresByMovieId.values());
        double highestScore = Collections.max(scoresByMovieId.values());

        Map<Long, Double> normalizedScoresByMovieId = new HashMap<>();
        scoresByMovieId.forEach((movieId, score) -> normalizedScoresByMovieId.put(
                movieId, highestScore == lowestScore ? 1.0 : (score - lowestScore) / (highestScore - lowestScore)));
        return normalizedScoresByMovieId;
    }
}
