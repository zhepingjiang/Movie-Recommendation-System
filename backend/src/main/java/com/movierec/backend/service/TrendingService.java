package com.movierec.backend.service;

import com.movierec.backend.dto.TrendingScoreDto;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * Maintains the "trending movies" leaderboard in a Redis Sorted Set keyed by
 * {@link #TRENDING_KEY}, where the member is the movie id and the score is its view count.
 *
 * <p>The score is intentionally just a running view count with no time decay or other
 * ranking formula yet.
 */
@Service
@RequiredArgsConstructor
public class TrendingService {

    static final String TRENDING_KEY = "trending:movies";

    private final StringRedisTemplate redisTemplate;

    /**
     * Increments the given movie's trending score by 1, representing one more view.
     *
     * @param movieId the movie that was viewed
     */
    public void recordView(Long movieId) {
        redisTemplate.opsForZSet().incrementScore(TRENDING_KEY, String.valueOf(movieId), 1);
    }

    /**
     * Reads the top trending movies by score, highest first.
     *
     * @param limit maximum number of entries to return
     * @return the top movie ids with their current view-count scores, highest first
     */
    public List<TrendingScoreDto> getTopTrending(int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(TRENDING_KEY, 0, limit - 1);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> new TrendingScoreDto(Long.valueOf(tuple.getValue()), tuple.getScore()))
                .toList();
    }
}
