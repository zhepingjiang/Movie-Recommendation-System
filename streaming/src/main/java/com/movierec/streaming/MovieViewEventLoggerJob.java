package com.movierec.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Phase 1: proves the Flink cluster can consume the backend's existing {@code movie-view-events}
 * Kafka topic. Deliberately does nothing but deserialize and log -- the similarity lookup and
 * windowed aggregation come in Phase 2, once this connector wiring is confirmed working end to end.
 */
public class MovieViewEventLoggerJob {

    // kafka:29092 is the INTERNAL listener added to docker-compose.yml's kafka service --
    // "localhost:9092" (what the host-run backend uses) isn't reachable from inside this
    // container.
    private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka:29092";
    private static final String TOPIC = "movie-view-events";
    private static final String CONSUMER_GROUP_ID = "flink-movie-view-event-logger";

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<MovieViewEvent> movieViewEventSource = KafkaSource.<MovieViewEvent>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(TOPIC)
                .setGroupId(CONSUMER_GROUP_ID)
                // Only events published after this job starts -- Phase 1 is just proving
                // connectivity, not replaying the topic's full history.
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new MovieViewEventDeserializationSchema())
                .build();

        DataStreamSource<MovieViewEvent> movieViewEvents = env.fromSource(
                movieViewEventSource, WatermarkStrategy.noWatermarks(), "movie-view-events-source");

        movieViewEvents.print();

        env.execute("movie-view-event-logger-job");
    }

    private static class MovieViewEventDeserializationSchema implements DeserializationSchema<MovieViewEvent> {

        private transient ObjectMapper objectMapper;

        @Override
        public void open(InitializationContext context) {
            objectMapper = new ObjectMapper();
        }

        @Override
        public MovieViewEvent deserialize(byte[] message) throws IOException {
            return objectMapper.readValue(message, MovieViewEvent.class);
        }

        @Override
        public boolean isEndOfStream(MovieViewEvent nextElement) {
            return false;
        }

        @Override
        public TypeInformation<MovieViewEvent> getProducedType() {
            return TypeInformation.of(MovieViewEvent.class);
        }
    }
}
