package com.movierec.backend.controller;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.TrendingMovieDto;
import com.movierec.backend.dto.TrendingScoreDto;
import com.movierec.backend.service.MovieService;
import com.movierec.backend.service.TrendingService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to the trending-movies leaderboard backed by Redis.
 */
@RestController
@RequestMapping("/api/trending")
@RequiredArgsConstructor
public class TrendingController {

    private final TrendingService trendingService;
    private final MovieService movieService;

    /**
     * @param limit maximum number of entries to return, defaults to 10
     * @return the top trending movies by view count, highest first, with full movie details.
     *     A movie id present in Redis but no longer in Postgres is silently skipped.
     */
    @GetMapping
    public List<TrendingMovieDto> getTrending(@RequestParam(defaultValue = "10") int limit) {
        List<TrendingScoreDto> topScores = trendingService.getTopTrending(limit);
        Map<Long, MovieSummaryDto> movies =
                movieService.getMoviesByIds(topScores.stream().map(TrendingScoreDto::movieId).toList());
        return topScores.stream()
                .filter(entry -> movies.containsKey(entry.movieId()))
                .map(entry -> new TrendingMovieDto(movies.get(entry.movieId()), entry.viewCount()))
                .toList();
    }
}
