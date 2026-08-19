package com.movierec.backend.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link MovieViewEvent}s to the {@code movie-view-events} topic, keyed by
 * movie id.
 */
@Component
@RequiredArgsConstructor
public class MovieViewEventProducer {

    public static final String TOPIC = "movie-view-events";

    private final KafkaTemplate<String, MovieViewEvent> kafkaTemplate;

    public void publishView(Long movieId, Long userId) {
        kafkaTemplate.send(
                TOPIC, String.valueOf(movieId), new MovieViewEvent(movieId, userId, Instant.now().toEpochMilli()));
    }
}
