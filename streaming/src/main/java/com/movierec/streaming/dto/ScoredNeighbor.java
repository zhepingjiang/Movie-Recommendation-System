package com.movierec.streaming.dto;

/**
 * One row from {@code movie_similarity_cache}: a movie similar to the one that was looked up,
 * with its cosine similarity score.
 */
public record ScoredNeighbor(long similarMovieId, double score) {
}
