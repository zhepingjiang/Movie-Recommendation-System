package com.movierec.backend.dto;

import java.time.Instant;

/**
 * Response body for {@code PUT /api/movies/{id}/rating}: the saved rating.
 */
public record RatingDto(Long movieId, Short score, Instant updatedAt) {}
