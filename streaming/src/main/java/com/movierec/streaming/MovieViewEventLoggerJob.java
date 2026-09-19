package com.movierec.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movierec.streaming.events.MovieViewEvent;
import com.movierec.streaming.scoring.UserWindowedCandidateScorer;
import com.movierec.streaming.similarity.CachedMovieSimilarityLookup;
import com.movierec.streaming.sink.RecommendationCacheJdbcSink;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Properties;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;

/**
 * For each user, aggregates recently-viewed movies over a sliding window, scores recommendation
 * candidates via a {@code movie_similarity_cache} JDBC lookup (see {@link
 * UserWindowedCandidateScorer}), and writes each window's top candidates into {@code
 * recommendation_cache} under {@code model_version = "nearline_v1"} (see {@link
 * RecommendationCacheJdbcSink}).
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

    private static final String FLINK_CHECKPOINT_DIRECTORY =
            resolveConfig("FLINK_CHECKPOINT_DIR", "flink.checkpoint.dir");

    // Events are produced synchronously when the backend serves a request, so out-of-orderness
    // should be small -- this just guards against Kafka partition skew/network jitter.
    private static final Duration MAX_EVENT_OUT_OF_ORDERNESS = Duration.ofSeconds(5);
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(10);
    private static final Duration WINDOW_SLIDE = Duration.ofMinutes(1);

    // Shorter than WINDOW_SLIDE so a recovery never has to redo more than about one window
    // firing's worth of buffered events per user.
    private static final Duration CHECKPOINT_INTERVAL = Duration.ofSeconds(30);

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

    // Package-private (rather than inlined into main()) so MovieViewEventLoggerJobTest can
    // assert on the resulting environment's checkpointing/restart-strategy config without
    // submitting an actual job -- constructing a StreamExecutionEnvironment and calling
    // enableCheckpointing() does no I/O by itself, only env.execute() does.
    //
    // FIX: this job had no checkpointing and no restart strategy at all, which meant (1) a task
    // failure just killed the job outright instead of retrying, and (2) even with a restart,
    // there was no checkpoint to resume from, so the window operator's buffered events and the
    // Kafka source's consumed offsets were both lost rather than replayed -- silently dropping
    // whatever was in flight instead of reproducing it. The delete-then-insert write in
    // RecommendationCacheJdbcSink is idempotent under replay, but only if a replay actually
    // happens. Flink 2.x removed the old env.setRestartStrategy(...) method
    // (org.apache.flink.api.common.restartstrategy.RestartStrategies no longer exists) --
    // restart strategy is now Configuration-driven via RestartStrategyOptions instead, so it has
    // to be set here rather than called on env directly.
    static StreamExecutionEnvironment createStreamExecutionEnvironment() {
        Configuration configuration = new Configuration();
        configuration.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, FLINK_CHECKPOINT_DIRECTORY);
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY, "exponential-delay");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(configuration);
        // FIX: see above -- without this, nothing ever checkpoints the window operator's state
        // or the Kafka source's offsets, so RESTART_STRATEGY above would have nothing to restart
        // from even once retries are enabled.
        env.enableCheckpointing(CHECKPOINT_INTERVAL.toMillis());
        return env;
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = createStreamExecutionEnvironment();

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
                // FIX: was .addSink(...) against RichSinkFunction, the legacy SinkFunction API
                // Flink 2.x moved into a .legacy package -- sinkTo(...) against the current
                // Sink/SinkWriter API is the supported path for new sinks going forward.
                .sinkTo(new RecommendationCacheJdbcSink(POSTGRES_JDBC_URL, POSTGRES_USERNAME, POSTGRES_PASSWORD))
                .name("recommendation-cache-jdbc-sink");

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
