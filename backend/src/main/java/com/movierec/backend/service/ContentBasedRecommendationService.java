package com.movierec.backend.service;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.SimilarMovieDto;
import com.movierec.backend.entity.MovieSimilarityCache;
import com.movierec.backend.repository.MovieSimilarityCacheRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Content-based "similar movies" for a given movie, computed offline by the recommendation
 * service's content-based training job (see recommendation/models/content_based_training.py) and
 * read directly from Postgres -- like {@link PersonalizedRecommendationService}, this never calls
 * the Python service at request time, since the scores are already precomputed and sitting in
 * {@code movie_similarity_cache}.
 *
 * <p>Unlike personalized recommendations, there's no trending backfill here: a movie either has
 * content neighbors or it doesn't (e.g. no description/genres yet), and padding a "similar to
 * this movie" list with unrelated trending movies would misrepresent what the list means.
 */
@Service
@RequiredArgsConstructor
public class ContentBasedRecommendationService {

    // Must match models/content_based_training.py's MODEL_VERSION -- there's no shared source of
    // truth across the two services for this value, so keep them in sync by hand.
    private static final String MODEL_VERSION = "content_v1";

    private final MovieSimilarityCacheRepository movieSimilarityCacheRepository;
    private final MovieService movieService;

    public List<SimilarMovieDto> getSimilarMovies(Long movieId, int limit) {
        List<MovieSimilarityCache> cached = movieSimilarityCacheRepository
                .findByIdMovieIdAndIdModelVersionOrderByScoreDesc(movieId, MODEL_VERSION, PageRequest.of(0, limit));

        List<Long> similarIds = cached.stream().map(c -> c.getId().getSimilarMovieId()).toList();
        Map<Long, MovieSummaryDto> similarMovies = movieService.getMoviesByIds(similarIds);

        return cached.stream()
                .map(c -> {
                    MovieSummaryDto movie = similarMovies.get(c.getId().getSimilarMovieId());
                    return movie == null ? null : new SimilarMovieDto(movie, c.getScore().doubleValue());
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
