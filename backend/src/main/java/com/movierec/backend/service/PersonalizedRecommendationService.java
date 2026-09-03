package com.movierec.backend.service;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.TrendingScoreDto;
import com.movierec.backend.entity.RecommendationCache;
import com.movierec.backend.repository.RecommendationCacheRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Personalized recommendations for real users, computed offline by the recommendation service's
 * nightly SVD job (see recommendation/models/svd_training.py) and read directly from Postgres --
 * unlike {@link RecommendationService}, this never calls the Python service at request time,
 * since the scores are already precomputed and sitting in {@code recommendation_cache}.
 *
 * <p>When a user has fewer cached recommendations than requested (a brand-new real user, or one
 * who hasn't been through a training run yet), the shortfall is filled with trending movies so
 * the row is never emptier than it needs to be.
 */
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendationService {

    // Must match models/recommendation_blending.py's MODEL_VERSION -- there's no shared source of
    // truth across the two services for this value, so keep them in sync by hand.
    private static final String MODEL_VERSION = "blended_v1";

    private final RecommendationCacheRepository recommendationCacheRepository;
    private final MovieService movieService;
    private final TrendingService trendingService;

    public List<MovieSummaryDto> getRecommendations(Long userId, int limit) {
        List<RecommendationCache> cached = recommendationCacheRepository
                .findByIdUserIdAndIdModelVersionOrderByScoreDesc(userId, MODEL_VERSION, PageRequest.of(0, limit));

        List<Long> personalizedIds = cached.stream().map(rc -> rc.getId().getMovieId()).toList();
        Map<Long, MovieSummaryDto> personalizedMovies = movieService.getMoviesByIds(personalizedIds);
        List<MovieSummaryDto> personalized =
                personalizedIds.stream().map(personalizedMovies::get).filter(Objects::nonNull).toList();

        int remaining = limit - personalized.size();
        if (remaining <= 0) {
            return personalized;
        }

        List<MovieSummaryDto> result = new ArrayList<>(personalized);
        result.addAll(backfillWithTrending(personalized, remaining, limit));
        return result;
    }

    /**
     * Fills a shortfall with trending movies, excluding anything already selected so a movie
     * never appears twice in the same response. Requests a full {@code limit} worth of trending
     * candidates (not just {@code remaining}) as headroom against overlaps with the personalized
     * list and ids no longer in Postgres.
     */
    private List<MovieSummaryDto> backfillWithTrending(
            List<MovieSummaryDto> alreadySelected, int remaining, int limit) {
        Set<Long> excluded = alreadySelected.stream().map(MovieSummaryDto::id).collect(Collectors.toSet());

        List<Long> trendingIds = trendingService.getTopTrending(limit).stream()
                .map(TrendingScoreDto::movieId)
                .filter(id -> !excluded.contains(id))
                .limit(remaining)
                .toList();

        Map<Long, MovieSummaryDto> trendingMovies = movieService.getMoviesByIds(trendingIds);
        return trendingIds.stream().map(trendingMovies::get).filter(Objects::nonNull).toList();
    }
}
