package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.TrendingScoreDto;
import com.movierec.backend.entity.RecommendationCache;
import com.movierec.backend.entity.RecommendationCacheId;
import com.movierec.backend.repository.RecommendationCacheRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PersonalizedRecommendationServiceTest {

    private static final String MODEL_VERSION = "svd_v1";
    private static final Long USER_ID = 1L;

    @Mock private RecommendationCacheRepository recommendationCacheRepository;
    @Mock private MovieService movieService;
    @Mock private TrendingService trendingService;

    @InjectMocks private PersonalizedRecommendationService service;

    private static RecommendationCache cached(long userId, long movieId) {
        return RecommendationCache.builder().id(new RecommendationCacheId(userId, movieId, MODEL_VERSION)).build();
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
}
