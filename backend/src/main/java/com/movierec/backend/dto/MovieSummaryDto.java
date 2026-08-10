package com.movierec.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MovieSummaryDto(
        Long id,
        String title,
        String posterUrl,
        String description,
        LocalDate releaseDate,
        Integer durationMin,
        BigDecimal averageRating,
        List<String> genres) {}
