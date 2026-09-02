package com.movierec.backend.dto;

/**
 * A single entry in a movie's "similar movies" list, with the similar movie's id hydrated into
 * full movie details.
 *
 * @param movie the similar movie's details
 * @param score the content-based model's cosine similarity score, 0-1
 */
public record SimilarMovieDto(MovieSummaryDto movie, double score) {
}
