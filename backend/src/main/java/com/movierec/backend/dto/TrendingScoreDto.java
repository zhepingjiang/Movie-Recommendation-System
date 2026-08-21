package com.movierec.backend.dto;

/**
 * A raw entry read from the {@code trending:movies} Redis Sorted Set, before the movie id
 * is hydrated into full movie details.
 *
 * @param movieId the movie id
 * @param viewCount the movie's current score in the Sorted Set
 */
public record TrendingScoreDto(Long movieId, double viewCount) {
}
