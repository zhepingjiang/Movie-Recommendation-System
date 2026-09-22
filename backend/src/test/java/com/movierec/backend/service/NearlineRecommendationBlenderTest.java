package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NearlineRecommendationBlenderTest {

    private static final Duration HALF_LIFE = Duration.ofMinutes(15);

    @Test
    void freshBatchGetsTheFullMaxWeight() {
        assertThat(NearlineRecommendationBlender.nearlineWeight(Duration.ZERO, HALF_LIFE, 0.5)).isEqualTo(0.5);
    }

    @Test
    void weightHalvesEveryHalfLife() {
        assertThat(NearlineRecommendationBlender.nearlineWeight(HALF_LIFE, HALF_LIFE, 0.5))
                .isCloseTo(0.25, within(1e-9));
        assertThat(NearlineRecommendationBlender.nearlineWeight(HALF_LIFE.multipliedBy(2), HALF_LIFE, 0.5))
                .isCloseTo(0.125, within(1e-9));
    }

    @Test
    void generatedAtAheadOfTheClockNeverExceedsTheMaxWeight() {
        assertThat(NearlineRecommendationBlender.nearlineWeight(Duration.ofSeconds(-30), HALF_LIFE, 0.5))
                .isEqualTo(0.5);
    }

    @Test
    void zeroNearlineWeightKeepsTheBlendedOrder() {
        assertThat(NearlineRecommendationBlender.rankMovieIds(
                        Map.of(10L, 0.9, 11L, 0.5, 12L, 0.1), Map.of(12L, 0.99, 20L, 0.8), 0.0))
                .startsWith(10L, 11L, 12L);
    }

    @Test
    void zeroFillsCandidatesOnlyOneModelCovers() {
        // blended normalized {10:1, 11:0}, nearline normalized {20:1, 21:0}; weight 0.25:
        // 10 -> 0.75, 20 -> 0.25, 11 -> 0, 21 -> 0 (tie broken by movie id).
        assertThat(NearlineRecommendationBlender.rankMovieIds(
                        Map.of(10L, 0.9, 11L, 0.5), Map.of(20L, 0.7, 21L, 0.2), 0.25))
                .containsExactly(10L, 20L, 11L, 21L);
    }

    @Test
    void candidateBothModelsRankHighlyOvertakesEitherModelsSoloTopPick() {
        // blended normalized {10:1, 11:0.5, 12:0}, nearline normalized {20:1, 11:0.875, 21:0}.
        // At 0.5: 11 -> 0.6875 beats 10 -> 0.5 and 20 -> 0.5, each only liked by one model.
        assertThat(NearlineRecommendationBlender.rankMovieIds(
                        Map.of(10L, 0.9, 11L, 0.7, 12L, 0.5), Map.of(20L, 0.9, 11L, 0.85, 21L, 0.5), 0.5))
                .containsExactly(11L, 10L, 20L, 12L, 21L);
    }

    @Test
    void minMaxNormalizeScalesToZeroOneRange() {
        assertThat(NearlineRecommendationBlender.minMaxNormalize(Map.of(1L, 2.0, 2L, 4.0, 3L, 3.0)))
                .isEqualTo(Map.of(1L, 0.0, 2L, 1.0, 3L, 0.5));
    }

    @Test
    void tiedScoresNormalizeToOneRatherThanDividingByZero() {
        assertThat(NearlineRecommendationBlender.minMaxNormalize(Map.of(1L, 0.4)))
                .isEqualTo(Map.of(1L, 1.0));
        assertThat(NearlineRecommendationBlender.minMaxNormalize(Map.of())).isEmpty();
    }
}
