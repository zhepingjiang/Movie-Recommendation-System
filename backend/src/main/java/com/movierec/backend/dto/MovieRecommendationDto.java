package com.movierec.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single cold-start recommendation as returned by the Python recommendation service's
 * {@code GET /recommendations/{user_id}/cold-start}. Field names are annotated explicitly since
 * that service returns snake_case JSON and this backend doesn't otherwise use a snake_case naming
 * strategy.
 */
public record MovieRecommendationDto(
        Long id,
        String title,
        @JsonProperty("poster_url") String posterUrl,
        @JsonProperty("average_rating") double averageRating,
        List<String> genres,
        @JsonProperty("match_score") double matchScore) {}
