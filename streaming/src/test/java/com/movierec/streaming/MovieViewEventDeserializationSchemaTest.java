package com.movierec.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.movierec.streaming.MovieViewEventLoggerJob.MovieViewEventDeserializationSchema;
import com.movierec.streaming.events.MovieViewEvent;
import java.nio.charset.StandardCharsets;
import org.apache.flink.api.common.serialization.DeserializationSchema.InitializationContext;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.SimpleCounter;
import org.apache.flink.metrics.groups.UnregisteredMetricsGroup;
import org.apache.flink.util.SimpleUserCodeClassLoader;
import org.apache.flink.util.UserCodeClassLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MovieViewEventDeserializationSchemaTest {

    private MovieViewEventDeserializationSchema deserializationSchema;
    private SimpleCounter skippedMovieViewEvents;

    @BeforeEach
    void openDeserializationSchema() {
        skippedMovieViewEvents = new SimpleCounter();
        MetricGroup metricGroup = new UnregisteredMetricsGroup() {
            @Override
            public Counter counter(String name) {
                return skippedMovieViewEvents;
            }
        };
        deserializationSchema = new MovieViewEventDeserializationSchema();
        deserializationSchema.open(new InitializationContext() {
            @Override
            public MetricGroup getMetricGroup() {
                return metricGroup;
            }

            @Override
            public UserCodeClassLoader getUserCodeClassLoader() {
                return SimpleUserCodeClassLoader.create(getClass().getClassLoader());
            }
        });
    }

    @Test
    void deserializesAWellFormedEvent() {
        MovieViewEvent event = deserialize("{\"movieId\":7,\"userId\":42,\"occurredAtEpochMilli\":1000}");

        assertEquals(new MovieViewEvent(7L, 42L, 1000L), event);
        assertEquals(0, skippedMovieViewEvents.getCount());
    }

    @Test
    void ignoresFieldsTheBackendAddsLater() {
        MovieViewEvent event =
                deserialize("{\"movieId\":7,\"userId\":42,\"occurredAtEpochMilli\":1000,\"newField\":\"x\"}");

        assertEquals(new MovieViewEvent(7L, 42L, 1000L), event);
        assertEquals(0, skippedMovieViewEvents.getCount());
    }

    @Test
    void skipsAndCountsUnparseableJsonInsteadOfThrowing() {
        assertNull(deserialize("not json at all"));
        assertEquals(1, skippedMovieViewEvents.getCount());
    }

    @Test
    void skipsAndCountsAnEventWithNoMovieId() {
        assertNull(deserialize("{\"userId\":42,\"occurredAtEpochMilli\":1000}"));
        assertEquals(1, skippedMovieViewEvents.getCount());
    }

    private MovieViewEvent deserialize(String json) {
        return deserializationSchema.deserialize(json.getBytes(StandardCharsets.UTF_8));
    }
}
