package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.TrendingScoreDto;
import com.movierec.backend.entity.RecommendationCache;
import com.movierec.backend.entity.RecommendationCacheId;
import com.movierec.backend.repository.RecommendationCacheRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PersonalizedRecommendationServiceTest {

    private static final String MODEL_VERSION = "blended_v1";
    private static final String NEARLINE_MODEL_VERSION = "nearline_v1";
    private static final Long USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");
    private static final Duration FRESHNESS_HALF_LIFE = Duration.ofMinutes(15);
    private static final double MAX_NEARLINE_WEIGHT = 0.5;
    private static final Duration MAX_NEARLINE_AGE = Duration.ofHours(2);

    @Mock private RecommendationCacheRepository recommendationCacheRepository;
    @Mock private MovieService movieService;
    @Mock private TrendingService trendingService;

    private PersonalizedRecommendationService service;

    @BeforeEach
    void setUp() {
        // Built by hand rather than @InjectMocks: the constructor also takes the clock and the
        // @Value-injected nearline tuning values, which Mockito can't supply.
        service = new PersonalizedRecommendationService(
                recommendationCacheRepository, movieService, trendingService,
                Clock.fixed(NOW, ZoneOffset.UTC), FRESHNESS_HALF_LIFE, MAX_NEARLINE_WEIGHT, MAX_NEARLINE_AGE);
    }

    private static RecommendationCache cached(long userId, long movieId) {
        return RecommendationCache.builder().id(new RecommendationCacheId(userId, movieId, MODEL_VERSION)).build();
    }

    private static RecommendationCache scored(String modelVersion, long movieId, double score, Instant generatedAt) {
        return RecommendationCache.builder()
                .id(new RecommendationCacheId(USER_ID, movieId, modelVersion))
                .score(BigDecimal.valueOf(score))
                .generatedAt(generatedAt)
                .build();
    }

    private void givenNearlineRows(RecommendationCache... nearlineRows) {
        when(recommendationCacheRepository.findByIdUserIdAndIdModelVersionAndGeneratedAtAfterOrderByScoreDesc(
                        USER_ID, NEARLINE_MODEL_VERSION, NOW.minus(MAX_NEARLINE_AGE), PageRequest.of(0, 50)))
                .thenReturn(List.of(nearlineRows));
    }

    private void givenBlendedRowsForMerge(RecommendationCache... blendedRows) {
        when(recommendationCacheRepository.findByIdUserIdAndIdModelVersionOrderByScoreDesc(
                        USER_ID, MODEL_VERSION, PageRequest.of(0, 100)))
                .thenReturn(List.of(blendedRows));
    }

    private static MovieSummaryDto movie(long id) {
        return new MovieSummaryDto(id, "Movie " + id, null, null, null, null, null, List.of());
    }

    @Test
    void returnsPersonalizedListWhenFullyPopulated() {
        when(recommendationCacheRepository.findByIdUserIdAndIdModelVersionOrderByScoreDesc(
                        USER_ID, MODEL_VERSION, PageRequest.of(0, 2)))
                .thenReturn(List.of(cached(USER_ID, 10), cached(USER_ID, 11)));
        when(movieService.getMoviesByIds(List.of(10L, 11L)))
                .thenReturn(Map.of(10L, movie(10), 11L, movie(11)));

        List<MovieSummaryDto> result = service.getRecommendations(USER_ID, 2);

        assertThat(result).extracting(MovieSummaryDto::id).containsExactly(10L, 11L);
        verify(trendingService, never()).getTopTrending(anyInt());
    }

    @Test
    void backfillsWithTrendingWhenShortOfLimit() {
        when(recommendationCacheRepository.findByIdUserIdAndIdModelVersionOrderByScoreDesc(
                        USER_ID, MODEL_VERSION, PageRequest.of(0, 3)))
                .thenReturn(List.of(cached(USER_ID, 10)));
        when(movieService.getMoviesByIds(List.of(10L))).thenReturn(Map.of(10L, movie(10)));
        // 10 is trending too (already selected -- must be excluded), plus two genuinely new ones.
        when(trendingService.getTopTrending(3))
                .thenReturn(List.of(
                        new TrendingScoreDto(10L, 99.0), new TrendingScoreDto(20L, 5.0), new TrendingScoreDto(21L, 4.0)));
        when(movieService.getMoviesByIds(List.of(20L, 21L))).thenReturn(Map.of(20L, movie(20), 21L, movie(21)));

        List<MovieSummaryDto> result = service.getRecommendations(USER_ID, 3);

        assertThat(result).extracting(MovieSummaryDto::id).containsExactly(10L, 20L, 21L);
    }

    @Test
    void fallsBackEntirelyToTrendingWhenNoPersonalizedRowsExist() {
        when(recommendationCacheRepository.findByIdUserIdAndIdModelVersionOrderByScoreDesc(
                        USER_ID, MODEL_VERSION, PageRequest.of(0, 2)))
                .thenReturn(List.of());
        when(movieService.getMoviesByIds(List.of())).thenReturn(Map.of());
        when(trendingService.getTopTrending(2))
                .thenReturn(List.of(new TrendingScoreDto(30L, 12.0), new TrendingScoreDto(31L, 8.0)));
        when(movieService.getMoviesByIds(List.of(30L, 31L))).thenReturn(Map.of(30L, movie(30), 31L, movie(31)));

        List<MovieSummaryDto> result = service.getRecommendations(USER_ID, 2);

        assertThat(result).extracting(MovieSummaryDto::id).containsExactly(30L, 31L);
    }

    @Test
    void silentlySkipsPersonalizedMovieIdsNoLongerInPostgresAndBackfillsTheGap() {
        when(recommendationCacheRepository.findByIdUserIdAndIdModelVersionOrderByScoreDesc(
                        USER_ID, MODEL_VERSION, PageRequest.of(0, 2)))
                .thenReturn(List.of(cached(USER_ID, 10), cached(USER_ID, 99))); // 99 was deleted
        when(movieService.getMoviesByIds(List.of(10L, 99L))).thenReturn(Map.of(10L, movie(10)));
        when(trendingService.getTopTrending(2)).thenReturn(List.of(new TrendingScoreDto(40L, 7.0)));
        when(movieService.getMoviesByIds(List.of(40L))).thenReturn(Map.of(40L, movie(40)));

        List<MovieSummaryDto> result = service.getRecommendations(USER_ID, 2);

        assertThat(result).extracting(MovieSummaryDto::id).containsExactly(10L, 40L);
    }

    @Test
    void freshNearlineCandidatesAreMergedIntoTheBlendedList() {
        // Nearline batch written at NOW -> full MAX_NEARLINE_WEIGHT (0.5).
        givenNearlineRows(
                scored(NEARLINE_MODEL_VERSION, 20, 0.9, NOW),
                scored(NEARLINE_MODEL_VERSION, 11, 0.3, NOW),
                scored(NEARLINE_MODEL_VERSION, 21, 0.1, NOW));
        givenBlendedRowsForMerge(
                scored(MODEL_VERSION, 10, 0.8, NOW.minus(Duration.ofDays(1))),
                scored(MODEL_VERSION, 11, 0.6, NOW.minus(Duration.ofDays(1))),
                scored(MODEL_VERSION, 12, 0.4, NOW.minus(Duration.ofDays(1))));
        // Normalized: blended {10:1, 11:0.5, 12:0}, nearline {20:1, 11:0.25, 21:0}.
        // Merged at 0.5/0.5: 10 -> 0.5, 20 -> 0.5, 11 -> 0.375, 12 -> 0, 21 -> 0 (ties by movie id),
        // so the nearline-only candidate 20 jumps ahead of blended's own #2.
        when(movieService.getMoviesByIds(List.of(10L, 20L, 11L)))
                .thenReturn(Map.of(10L, movie(10), 11L, movie(11), 20L, movie(20)));

        List<MovieSummaryDto> result = service.getRecommendations(USER_ID, 3);

        assertThat(result).extracting(MovieSummaryDto::id).containsExactly(10L, 20L, 11L);
        verify(trendingService, never()).getTopTrending(anyInt());
    }

    @Test
    void staleNearlineBatchBarelyMovesTheBlendedOrder() {
        // Four half-lives old -> weight 0.5 / 16 = 0.03125, not enough to overtake blended's top
        // or #2 pick with a nearline-only candidate.
        Instant staleGeneratedAt = NOW.minus(FRESHNESS_HALF_LIFE.multipliedBy(4));
        givenNearlineRows(
                scored(NEARLINE_MODEL_VERSION, 20, 0.9, staleGeneratedAt),
                scored(NEARLINE_MODEL_VERSION, 21, 0.1, staleGeneratedAt));
        givenBlendedRowsForMerge(
                scored(MODEL_VERSION, 10, 0.8, NOW),
                scored(MODEL_VERSION, 11, 0.6, NOW),
                scored(MODEL_VERSION, 12, 0.4, NOW));
        when(movieService.getMoviesByIds(List.of(10L, 11L)))
                .thenReturn(Map.of(10L, movie(10), 11L, movie(11)));

        List<MovieSummaryDto> result = service.getRecommendations(USER_ID, 2);

        // 10 -> 0.96875, 11 -> 0.484375, 20 -> 0.03125.
        assertThat(result).extracting(MovieSummaryDto::id).containsExactly(10L, 11L);
    }

    @Test
    void usesThePlainPagedBlendedQueryWhenThereAreNoFreshNearlineRows() {
        givenNearlineRows();
        when(recommendationCacheRepository.findByIdUserIdAndIdModelVersionOrderByScoreDesc(
                        USER_ID, MODEL_VERSION, PageRequest.of(0, 2)))
                .thenReturn(List.of(cached(USER_ID, 10), cached(USER_ID, 11)));
        when(movieService.getMoviesByIds(List.of(10L, 11L)))
                .thenReturn(Map.of(10L, movie(10), 11L, movie(11)));

        List<MovieSummaryDto> result = service.getRecommendations(USER_ID, 2);

        assertThat(result).extracting(MovieSummaryDto::id).containsExactly(10L, 11L);
        verify(recommendationCacheRepository, never())
                .findByIdUserIdAndIdModelVersionOrderByScoreDesc(eq(USER_ID), eq(MODEL_VERSION), eq(PageRequest.of(0, 100)));
    }

    @Test
    void nearlineOnlyUserStillGetsNearlineCandidates() {
        givenNearlineRows(
                scored(NEARLINE_MODEL_VERSION, 20, 0.9, NOW),
                scored(NEARLINE_MODEL_VERSION, 21, 0.3, NOW));
        givenBlendedRowsForMerge();
        when(movieService.getMoviesByIds(List.of(20L, 21L)))
                .thenReturn(Map.of(20L, movie(20), 21L, movie(21)));

        List<MovieSummaryDto> result = service.getRecommendations(USER_ID, 2);

        assertThat(result).extracting(MovieSummaryDto::id).containsExactly(20L, 21L);
    }
}
