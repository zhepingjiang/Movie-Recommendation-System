package com.movierec.backend.event;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link MovieViewEvent}s to the {@code movie-view-events} topic, keyed by
 * movie id.
 *
 * <p>Repeat views of the same movie by the same signed-in user within {@link #VIEW_DEDUP_WINDOW}
 * are collapsed into one event. A view is recorded as a side effect of {@code GET
 * /api/movies/{id}}, so anything that re-issues that GET -- React StrictMode's dev-only double
 * effect, a double click, a reload, a network retry -- would otherwise count as another view in
 * {@code user_events}, trending, and the nearline job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MovieViewEventProducer {

    public static final String TOPIC = "movie-view-events";

    static final Duration VIEW_DEDUP_WINDOW = Duration.ofSeconds(30);
    static final String VIEW_DEDUP_KEY_PREFIX = "view-dedup:";

    private final KafkaTemplate<String, MovieViewEvent> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    public void publishView(Long movieId, Long userId) {
        // FIX: every GET published a view, so one click in the dev frontend (StrictMode runs the
        // fetch effect twice) was recorded as two views in user_events/trending.
        if (isRepeatView(movieId, userId)) {
            return;
        }
        kafkaTemplate.send(
                TOPIC, String.valueOf(movieId), new MovieViewEvent(movieId, userId, Instant.now().toEpochMilli()));
    }

    /**
     * Atomically claims a {@code view-dedup:{userId}:{movieId}} key (SET NX with a TTL) -- true
     * if it was already held, i.e. this user viewed this movie within the dedup window.
     * Anonymous views have no user to key on and are never treated as repeats. Fails open: if
     * Redis is unavailable the view is published anyway, since dropping real views would be
     * worse than an occasional duplicate.
     */
    private boolean isRepeatView(Long movieId, Long userId) {
        if (userId == null) {
            return false;
        }
        String dedupKey = VIEW_DEDUP_KEY_PREFIX + userId + ":" + movieId;
        try {
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", VIEW_DEDUP_WINDOW);
            return Boolean.FALSE.equals(claimed);
        } catch (DataAccessException redisFailure) {
            log.warn("View dedup check failed for {}, publishing without dedup", dedupKey, redisFailure);
            return false;
        }
    }
}
