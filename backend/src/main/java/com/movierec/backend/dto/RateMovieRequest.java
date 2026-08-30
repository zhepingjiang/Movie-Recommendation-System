package com.movierec.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/movies/{id}/rating}.
 */
public record RateMovieRequest(@NotNull @Min(1) @Max(5) Short score) {}
