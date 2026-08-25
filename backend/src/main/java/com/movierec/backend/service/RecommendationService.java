package com.movierec.backend.service;

import com.movierec.backend.dto.MovieRecommendationDto;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Calls the Python recommendation service server-to-server. This is the only caller that service
 * should ever have — see {@link com.movierec.backend.controller.RecommendationController} for why
 * the user id passed here always comes from the JWT, never from client input.
 */
@Service
public class RecommendationService {

    private final RestClient restClient;

    public RecommendationService(@Value("${recommendation.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<MovieRecommendationDto> getColdStartRecommendations(Long userId, int limit) {
        MovieRecommendationDto[] result = restClient
                .get()
                .uri("/recommendations/{userId}/cold-start?limit={limit}", userId, limit)
                .retrieve()
                .body(MovieRecommendationDto[].class);
        return result == null ? List.of() : List.of(result);
    }
}
