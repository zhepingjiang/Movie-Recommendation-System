package com.movierec.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class MovieViewEventProducerTest {

    private static final Long MOVIE_ID = 4999L;
    private static final Long USER_ID = 44L;
    private static final String DEDUP_KEY = "view-dedup:44:4999";

    @Mock private KafkaTemplate<String, MovieViewEvent> kafkaTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private MovieViewEventProducer producer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void publishesFirstViewAndClaimsTheDedupKeyForTheWindow() {
        when(valueOperations.setIfAbsent(DEDUP_KEY, "1", MovieViewEventProducer.VIEW_DEDUP_WINDOW)).thenReturn(true);

        producer.publishView(MOVIE_ID, USER_ID);

        ArgumentCaptor<MovieViewEvent> eventCaptor = ArgumentCaptor.forClass(MovieViewEvent.class);
        verify(kafkaTemplate).send(eq(MovieViewEventProducer.TOPIC), eq("4999"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().movieId()).isEqualTo(MOVIE_ID);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    void skipsRepeatViewWithinTheWindow() {
        when(valueOperations.setIfAbsent(DEDUP_KEY, "1", MovieViewEventProducer.VIEW_DEDUP_WINDOW)).thenReturn(false);

        producer.publishView(MOVIE_ID, USER_ID);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(MovieViewEvent.class));
    }

    @Test
    void anonymousViewsAreNeverDeduplicated() {
        producer.publishView(MOVIE_ID, null);

        verifyNoInteractions(redisTemplate);
        verify(kafkaTemplate).send(eq(MovieViewEventProducer.TOPIC), eq("4999"), any(MovieViewEvent.class));
    }

    @Test
    void publishesAnywayWhenRedisIsUnavailable() {
        when(valueOperations.setIfAbsent(DEDUP_KEY, "1", MovieViewEventProducer.VIEW_DEDUP_WINDOW))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        producer.publishView(MOVIE_ID, USER_ID);

        verify(kafkaTemplate).send(eq(MovieViewEventProducer.TOPIC), eq("4999"), any(MovieViewEvent.class));
    }
}
