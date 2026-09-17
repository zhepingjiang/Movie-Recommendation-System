package com.movierec.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movierec.streaming.events.MovieViewEvent;
import com.movierec.streaming.scoring.UserWindowedCandidateScorer;
import com.movierec.streaming.similarity.CachedMovieSimilarityLookup;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Properties;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;

/**
 * Phase 2: for each user, aggregates recently-viewed movies over a sliding window and scores
 * recommendation candidates via a {@code movie_similarity_cache} JDBC lookup -- see
 * {@link UserWindowedCandidateScorer}. Output is only printed for now; the JDBC sink into
 * {@code recommendation_cache} is Phase 3.
 */
public class MovieViewEventLoggerJob {

    private static final Properties APPLICATION_PROPERTIES = loadApplicationProperties();

    // Default lives in application.properties (docker-compose.yml's INTERNAL listener,
    // "kafka:29092") -- "localhost:9092" (what the host-run backend uses) isn't reachable from
    // inside this container. Overridable via env var since the address differs outside
    // docker-compose (e.g. minikube).
    private static final String KAFKA_BOOTSTRAP_SERVERS = resolveConfig("KAFKA_BOOTSTRAP_SERVERS", "kafka.bootstrap.servers");
    private static final String TOPIC = "movie-view-events";
    private static final String CONSUMER_GROUP_ID = "flink-movie-view-event-logger";

    private static final String POSTGRES_JDBC_URL = resolveConfig("POSTGRES_JDBC_URL", "postgres.jdbc.url");
    private static final String POSTGRES_USERNAME = resolveConfig("POSTGRES_USERNAME", "postgres.username");
    private static final String POSTGRES_PASSWORD = resolveConfig("POSTGRES_PASSWORD", "postgres.password");

    // Events are produced synchronously when the backend serves a request, so out-of-orderness
    // should be small -- this just guards against Kafka partition skew/network jitter.
    private static final Duration MAX_EVENT_OUT_OF_ORDERNESS = Duration.ofSeconds(5);
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(10);
    private static final Duration WINDOW_SLIDE = Duration.ofMinutes(1);

    private static Properties loadApplicationProperties() {
        Properties applicationProperties = new Properties();
        try (InputStream applicationPropertiesStream =
                MovieViewEventLoggerJob.class.getClassLoader().getResourceAsStream("application.properties")) {
            applicationProperties.load(applicationPropertiesStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load application.properties", e);
        }
        return applicationProperties;
    }

    private static String resolveConfig(String envVarName, String applicationPropertyKey) {
        String envOverride = System.getenv(envVarName);
        if (envOverride != null && !envOverride.isBlank()) {
            return envOverride;
        }
        return APPLICATION_PROPERTIES.getProperty(applicationPropertyKey);
    }

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

        WatermarkStrategy<MovieViewEvent> watermarkStrategy = WatermarkStrategy
                .<MovieViewEvent>forBoundedOutOfOrderness(MAX_EVENT_OUT_OF_ORDERNESS)
                .withTimestampAssigner((event, recordTimestamp) -> event.occurredAtEpochMilli());

        DataStreamSource<MovieViewEvent> movieViewEvents =
                env.fromSource(movieViewEventSource, watermarkStrategy, "movie-view-events-source");

        movieViewEvents
                // userId is null for anonymous views -- can't keyBy a null key.
                .filter(event -> event.userId() != null)
                .keyBy(MovieViewEvent::userId)
                .window(SlidingEventTimeWindows.of(WINDOW_SIZE, WINDOW_SLIDE))
                .process(new UserWindowedCandidateScorer(
                        new CachedMovieSimilarityLookup(POSTGRES_JDBC_URL, POSTGRES_USERNAME, POSTGRES_PASSWORD)))
                .print();

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
