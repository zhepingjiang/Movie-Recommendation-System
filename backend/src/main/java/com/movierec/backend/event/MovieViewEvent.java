package com.movierec.backend.event;

/**
 * Kafka message payload published to the {@code movie-view-events} topic whenever
 * a movie detail page is viewed. {@code userId} is null for anonymous views, since
 * {@code GET /api/movies/{id}} does not require authentication.
 *
 * <p>{@code occurredAtEpochMilli} is a plain epoch-millis long rather than {@link java.time.Instant}
 * because the Kafka producer/consumer JSON (de)serializers use their own bare {@code ObjectMapper}
 * (not the app's, which has the JSR-310 module registered) and can't handle {@code Instant} directly.
 */
public record MovieViewEvent(Long movieId, Long userId, long occurredAtEpochMilli) {
}
