package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.SimilarMovieDto;
import com.movierec.backend.entity.MovieSimilarityCache;
import com.movierec.backend.entity.MovieSimilarityCacheId;
import com.movierec.backend.repository.MovieSimilarityCacheRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ContentBasedRecommendationServiceTest {

    private static final String MODEL_VERSION = "content_v1";
    private static final Long MOVIE_ID = 1L;

    @Mock private MovieSimilarityCacheRepository movieSimilarityCacheRepository;
    @Mock private MovieService movieService;

    @InjectMocks private ContentBasedRecommendationService service;

    private static MovieSimilarityCache cached(long movieId, long similarMovieId, String score) {
        return MovieSimilarityCache.builder()
                .id(new MovieSimilarityCacheId(movieId, similarMovieId, MODEL_VERSION))
                .score(new BigDecimal(score))
                .build();
    }

    private static MovieSummaryDto movie(long id) {
        return new MovieSummaryDto(id, "Movie " + id, null, null, null, null, null, List.of());
    }

    @Test
    void returnsSimilarMoviesInScoreOrderWithScoresAttached() {
        when(movieSimilarityCacheRepository.findByIdMovieIdAndIdModelVersionOrderByScoreDesc(
                        MOVIE_ID, MODEL_VERSION, PageRequest.of(0, 2)))
                .thenReturn(List.of(cached(MOVIE_ID, 10, "0.9000"), cached(MOVIE_ID, 11, "0.8000")));
        when(movieService.getMoviesByIds(List.of(10L, 11L)))
                .thenReturn(Map.of(10L, movie(10), 11L, movie(11)));

        List<SimilarMovieDto> result = service.getSimilarMovies(MOVIE_ID, 2);

        assertThat(result).containsExactly(new SimilarMovieDto(movie(10), 0.9), new SimilarMovieDto(movie(11), 0.8));
    }

    @Test
    void returnsEmptyListWhenNoCachedNeighborsExist() {
        when(movieSimilarityCacheRepository.findByIdMovieIdAndIdModelVersionOrderByScoreDesc(
                        MOVIE_ID, MODEL_VERSION, PageRequest.of(0, 5)))
                .thenReturn(List.of());
        when(movieService.getMoviesByIds(List.of())).thenReturn(Map.of());

        assertThat(service.getSimilarMovies(MOVIE_ID, 5)).isEmpty();
    }

    @Test
    void silentlySkipsNeighborIdsNoLongerInPostgres() {
        when(movieSimilarityCacheRepository.findByIdMovieIdAndIdModelVersionOrderByScoreDesc(
                        MOVIE_ID, MODEL_VERSION, PageRequest.of(0, 2)))
                .thenReturn(List.of(cached(MOVIE_ID, 10, "0.9000"), cached(MOVIE_ID, 99, "0.7000"))); // 99 was deleted
        when(movieService.getMoviesByIds(List.of(10L, 99L))).thenReturn(Map.of(10L, movie(10)));

        List<SimilarMovieDto> result = service.getSimilarMovies(MOVIE_ID, 2);

        assertThat(result).containsExactly(new SimilarMovieDto(movie(10), 0.9));
    }
}
