package com.movierec.backend.dto;

/**
 * A single entry in the trending-movies leaderboard, with the movie id hydrated into full
 * movie details.
 *
 * @param movie the movie details
 * @param viewCount the movie's current score in the {@code trending:movies} Redis Sorted Set
 */
public record TrendingMovieDto(MovieSummaryDto movie, double viewCount) {
}
