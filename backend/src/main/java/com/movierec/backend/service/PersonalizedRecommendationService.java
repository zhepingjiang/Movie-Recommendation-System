package com.movierec.backend.service;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.TrendingScoreDto;
import com.movierec.backend.entity.RecommendationCache;
import com.movierec.backend.repository.RecommendationCacheRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Personalized recommendations for real users, computed offline by the recommendation service's
 * nightly SVD job (see recommendation/models/svd_training.py) and read directly from Postgres --
 * unlike {@link RecommendationService}, this never calls the Python service at request time,
 * since the scores are already precomputed and sitting in {@code recommendation_cache}.
 *
 * <p>If the Flink streaming job has written fresh {@code nearline_v1} rows for the user (from
 * their recent views), those are merged into the offline {@code blended_v1} list at request time,
 * weighted by how fresh they are -- see {@link NearlineRecommendationBlender}.
 *
 * <p>When a user has fewer cached recommendations than requested (a brand-new real user, or one
 * who hasn't been through a training run yet), the shortfall is filled with trending movies so
 * the row is never emptier than it needs to be.
 */
@Service
public class PersonalizedRecommendationService {

    // Must match models/recommendation_blending.py's MODEL_VERSION -- there's no shared source of
    // truth across the two services for this value, so keep them in sync by hand.
    private static final String MODEL_VERSION = "blended_v1";
    // Must match streaming's RecommendationCacheJdbcSink model version, same caveat as above.
    private static final String NEARLINE_MODEL_VERSION = "nearline_v1";

    // Upper bounds on how many rows either model persists per user (streaming's
    // TOP_CANDIDATES_PER_USER = 50; blended_v1 is the union of svd_v1's and content_v1's 50 each).
    // Merging reads each model's whole per-user list, not just the top `limit`, so min-max
    // normalization sees the same candidate set the producing job scored.
    private static final int NEARLINE_CANDIDATES_PER_USER = 50;
    private static final int BLENDED_CANDIDATES_PER_USER = 100;

    private final RecommendationCacheRepository recommendationCacheRepository;
    private final MovieService movieService;
    private final TrendingService trendingService;
    private final Clock clock;
    private final Duration nearlineFreshnessHalfLife;
    private final double nearlineMaxWeight;
    private final Duration nearlineMaxAge;

    public PersonalizedRecommendationService(
            RecommendationCacheRepository recommendationCacheRepository,
            MovieService movieService,
            TrendingService trendingService,
            Clock clock,
            @Value("${recommendation.nearline.freshness-half-life}") Duration nearlineFreshnessHalfLife,
            @Value("${recommendation.nearline.max-weight}") double nearlineMaxWeight,
            @Value("${recommendation.nearline.max-age}") Duration nearlineMaxAge) {
        this.recommendationCacheRepository = recommendationCacheRepository;
        this.movieService = movieService;
        this.trendingService = trendingService;
        this.clock = clock;
        this.nearlineFreshnessHalfLife = nearlineFreshnessHalfLife;
        this.nearlineMaxWeight = nearlineMaxWeight;
        this.nearlineMaxAge = nearlineMaxAge;
    }

    public List<MovieSummaryDto> getRecommendations(Long userId, int limit) {
        List<Long> personalizedIds = rankPersonalizedMovieIds(userId, limit);
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
     * Top {@code limit} movie ids for the user: {@code blended_v1} as-is when there are no fresh
     * nearline rows (the common case -- one cheap paged query, same as before nearline existed),
     * otherwise both models' full per-user lists merged by {@link NearlineRecommendationBlender}.
     */
    private List<Long> rankPersonalizedMovieIds(Long userId, int limit) {
        Instant now = clock.instant();
        List<RecommendationCache> nearlineRows = recommendationCacheRepository
                .findByIdUserIdAndIdModelVersionAndGeneratedAtAfterOrderByScoreDesc(
                        userId, NEARLINE_MODEL_VERSION, now.minus(nearlineMaxAge),
                        PageRequest.of(0, NEARLINE_CANDIDATES_PER_USER));

        if (nearlineRows.isEmpty()) {
            return recommendationCacheRepository
                    .findByIdUserIdAndIdModelVersionOrderByScoreDesc(userId, MODEL_VERSION, PageRequest.of(0, limit))
                    .stream()
                    .map(row -> row.getId().getMovieId())
                    .toList();
        }

        List<RecommendationCache> blendedRows = recommendationCacheRepository
                .findByIdUserIdAndIdModelVersionOrderByScoreDesc(
                        userId, MODEL_VERSION, PageRequest.of(0, Math.max(limit, BLENDED_CANDIDATES_PER_USER)));

        // Every row in a nearline batch shares one generated_at (the window end), so the newest
        // one is the batch's age.
        Instant nearlineGeneratedAt =
                nearlineRows.stream().map(RecommendationCache::getGeneratedAt).max(Comparator.naturalOrder()).orElseThrow();
        double nearlineWeight = NearlineRecommendationBlender.nearlineWeight(
                Duration.between(nearlineGeneratedAt, now), nearlineFreshnessHalfLife, nearlineMaxWeight);

        return NearlineRecommendationBlender.rankMovieIds(
                        toScoresByMovieId(blendedRows), toScoresByMovieId(nearlineRows), nearlineWeight)
                .stream()
                .limit(limit)
                .toList();
    }

    private static Map<Long, Double> toScoresByMovieId(List<RecommendationCache> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> row.getId().getMovieId(), row -> row.getScore().doubleValue()));
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
