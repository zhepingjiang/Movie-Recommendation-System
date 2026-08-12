package com.movierec.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only projection of a {@link com.movierec.backend.entity.Movie} returned by the API,
 * flattening its genre associations into a plain list of names.
 */
public record MovieSummaryDto(
        Long id,
        String title,
        String posterUrl,
        String description,
        LocalDate releaseDate,
        Integer durationMin,
        BigDecimal averageRating,
        List<String> genres) {}
