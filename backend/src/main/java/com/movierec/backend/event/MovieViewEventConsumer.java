package com.movierec.backend.event;

import com.movierec.backend.entity.UserEvent;
import com.movierec.backend.repository.MovieRepository;
import com.movierec.backend.repository.UserEventRepository;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.service.TrendingService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@link MovieViewEvent}s from {@code movie-view-events}, records them in
 * {@code user_events}, and increments the movie's trending score in Redis.
 */
@Component
@RequiredArgsConstructor
public class MovieViewEventConsumer {

    private final UserEventRepository userEventRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final TrendingService trendingService;

    @KafkaListener(topics = MovieViewEventProducer.TOPIC)
    @Transactional
    public void onMovieView(MovieViewEvent event) {
        UserEvent userEvent = UserEvent.builder()
                .movie(movieRepository.getReferenceById(event.movieId()))
                .user(event.userId() == null ? null : userRepository.getReferenceById(event.userId()))
                .eventType("VIEW")
                .createdAt(Instant.ofEpochMilli(event.occurredAtEpochMilli()))
                .build();
        userEventRepository.save(userEvent);
        trendingService.recordView(event.movieId());
    }
}
