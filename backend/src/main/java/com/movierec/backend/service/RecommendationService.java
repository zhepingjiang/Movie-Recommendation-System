package com.movierec.backend.service;

import com.movierec.backend.dto.MovieRecommendationDto;
import com.movierec.backend.grpc.recommendation.ColdStartRequest;
import com.movierec.backend.grpc.recommendation.ColdStartResponse;
import com.movierec.backend.grpc.recommendation.MovieRecommendation;
import com.movierec.backend.grpc.recommendation.RecommendationServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Calls the Python recommendation service server-to-server over gRPC. This is the only caller
 * that service should ever have — see {@link com.movierec.backend.controller.RecommendationController}
 * for why the user id passed here always comes from the JWT, never from client input.
 *
 * <p>The channel is plaintext (no TLS): traffic never leaves the host, since the recommendation
 * service's port is bound to loopback only (see docker-compose.yml).
 */
@Service
public class RecommendationService {

    private final ManagedChannel channel;
    private final RecommendationServiceGrpc.RecommendationServiceBlockingStub stub;

    public RecommendationService(
            @Value("${recommendation.service.host}") String host,
            @Value("${recommendation.service.port}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.stub = RecommendationServiceGrpc.newBlockingStub(channel);
    }

    public List<MovieRecommendationDto> getColdStartRecommendations(Long userId, int limit) {
        ColdStartRequest request =
                ColdStartRequest.newBuilder().setUserId(userId).setLimit(limit).build();
        ColdStartResponse response = stub.getColdStartRecommendations(request);
        return response.getRecommendationsList().stream().map(this::toDto).toList();
    }

    private MovieRecommendationDto toDto(MovieRecommendation recommendation) {
        return new MovieRecommendationDto(
                recommendation.getId(),
                recommendation.getTitle(),
                recommendation.getPosterUrl(),
                recommendation.getAverageRating(),
                recommendation.getGenresList(),
                recommendation.getMatchScore());
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
