package com.movierec.streaming;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movierec.streaming.events.MovieViewEvent;
import com.movierec.streaming.scoring.UserWindowedCandidateScorer;
import com.movierec.streaming.similarity.CachedMovieSimilarityLookup;
import com.movierec.streaming.sink.RecommendationCacheJdbcSink;
import com.movierec.streaming.watermark.IdleAdvancingWatermarkGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ExternalizedCheckpointRetention;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    // How long with no events at all before the watermark starts advancing on wall-clock time
    // (see IdleAdvancingWatermarkGenerator) -- well under WINDOW_SLIDE, so a quiet period delays
    // a window's firing by at most ~this long instead of indefinitely.
    private static final Duration SOURCE_IDLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration WINDOW_SIZE = Duration.ofMinutes(10);
    private static final Duration WINDOW_SLIDE = Duration.ofMinutes(1);

    // Shorter than WINDOW_SLIDE so a recovery never has to redo more than about one window
    // firing's worth of buffered events per user.
    private static final Duration CHECKPOINT_INTERVAL = Duration.ofSeconds(30);
    // Flink's default is 10 minutes -- longer than this job's whole window, so a stuck
    // checkpoint (e.g. MinIO unreachable, or a sink flush blocked on Postgres) would go
    // unnoticed for 20 checkpoint intervals. 2 minutes still leaves plenty of headroom over a
    // healthy checkpoint here (sub-second at current state size).
    private static final Duration CHECKPOINT_TIMEOUT = Duration.ofMinutes(2);
    // Guarantees the job some checkpoint-free processing time even if checkpoints start running
    // long, instead of back-to-back checkpoints starving normal processing.
    private static final Duration MIN_PAUSE_BETWEEN_CHECKPOINTS = Duration.ofSeconds(10);
    // Consecutive checkpoint failures tolerated before failing (and restarting) the job. Absorbs
    // a short MinIO blip (~3 intervals) without a restart, while a longer outage still escalates
    // into the restart strategy below instead of silently running with no recovery point.
    private static final int TOLERABLE_CONSECUTIVE_CHECKPOINT_FAILURES = 3;

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
        // FIX: Flink deletes a job's checkpoints when it's cancelled (the default is
        // DELETE_ON_CANCELLATION), so a `flink cancel` followed by resubmitting a fixed jar had no
        // state to resume from -- open windows were lost. Retaining them allows
        // `flink run -s <checkpoint path>` to pick up exactly where the cancelled job stopped.
        configuration.set(
                CheckpointingOptions.EXTERNALIZED_CHECKPOINT_RETENTION,
                ExternalizedCheckpointRetention.RETAIN_ON_CANCELLATION);
        configuration.set(CheckpointingOptions.CHECKPOINTING_TIMEOUT, CHECKPOINT_TIMEOUT);
        configuration.set(CheckpointingOptions.MIN_PAUSE_BETWEEN_CHECKPOINTS, MIN_PAUSE_BETWEEN_CHECKPOINTS);
        configuration.set(CheckpointingOptions.TOLERABLE_FAILURE_NUMBER, TOLERABLE_CONSECUTIVE_CHECKPOINT_FAILURES);
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY, "exponential-delay");
        // Pinned explicitly rather than left as Flink's unstated defaults -- same values Flink
        // 2.2.1 already defaults to, just visible here and immune to a future Flink upgrade
        // quietly changing what "default" means.
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_INITIAL_BACKOFF, Duration.ofSeconds(1));
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_MAX_BACKOFF, Duration.ofMinutes(1));
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_BACKOFF_MULTIPLIER, 1.5);
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_RESET_BACKOFF_THRESHOLD, Duration.ofHours(1));
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_JITTER_FACTOR, 0.1);
        // FIX: unlike the above, this one is NOT Flink's default (which is unbounded/infinite
        // retries) -- without a ceiling, a permanent failure (bad credentials, schema drift, a
        // genuinely poison write) crash-loops the job forever instead of ever surfacing as
        // failed, since it looks identical to a transient blip that will self-heal. 10
        // consecutive failures without an intervening RESET_BACKOFF_THRESHOLD-long stretch of
        // healthy running is enough tolerance for a string of transient Postgres/Kafka hiccups,
        // capped by MAX_BACKOFF between each, before giving up and going to FAILED -- which
        // needs manual investigation/resubmission (FlinkNearlineJobNotRunning in
        // monitoring/alerts/flink-nearline.yml fires when that happens).
        configuration.set(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_ATTEMPTS, 10);

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
                // FIX: was OffsetsInitializer.latest(), which on every fresh submission (e.g.
                // after `flink cancel` + resubmit) skipped every view made while the job was down.
                // Resuming from this consumer group's offsets -- which the Kafka source commits
                // on each completed checkpoint -- picks those views back up. LATEST only applies
                // the very first time this group ever runs (no committed offsets yet), so a brand
                // new deployment still doesn't replay the topic's full history. (Restoring from a
                // checkpoint with `-s` ignores this entirely and uses the checkpointed offsets.)
                // OffsetResetStrategy is deprecated in kafka-clients 4.x (the source of the
                // compiler's deprecation note), but flink-connector-kafka 5.0.0-2.2 has no
                // overload taking its replacement yet -- this is still the connector's only API
                // for "committed offsets, falling back to X".
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
                .setValueOnlyDeserializer(new MovieViewEventDeserializationSchema())
                .build();

        // FIX: was WatermarkStrategy.forBoundedOutOfOrderness(...), whose watermark only moves
        // when a newer event arrives -- after a quiet period, the windows holding the last few
        // views never fired (so no nearline recs were written for them) until some later view
        // pushed the watermark forward.
        WatermarkStrategy<MovieViewEvent> watermarkStrategy = WatermarkStrategy
                .<MovieViewEvent>forGenerator(generatorContext -> new IdleAdvancingWatermarkGenerator<>(
                        MAX_EVENT_OUT_OF_ORDERNESS, SOURCE_IDLE_TIMEOUT, System::currentTimeMillis))
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
                // Stable, readable operator_name label for this operator's metrics -- the
                // watermark-lag alert and Grafana panel filter on it.
                .name("user-windowed-candidate-scorer")
                // FIX: was .addSink(...) against RichSinkFunction, the legacy SinkFunction API
                // Flink 2.x moved into a .legacy package -- sinkTo(...) against the current
                // Sink/SinkWriter API is the supported path for new sinks going forward.
                .sinkTo(new RecommendationCacheJdbcSink(POSTGRES_JDBC_URL, POSTGRES_USERNAME, POSTGRES_PASSWORD))
                .name("recommendation-cache-jdbc-sink");

        env.execute("movie-view-event-logger-job");
    }

    // Package-private (not private) so MovieViewEventDeserializationSchemaTest can exercise it
    // directly.
    static class MovieViewEventDeserializationSchema implements DeserializationSchema<MovieViewEvent> {

        private static final Logger LOG = LoggerFactory.getLogger(MovieViewEventDeserializationSchema.class);

        private transient ObjectMapper objectMapper;
        private transient Counter skippedMovieViewEvents;

        @Override
        public void open(InitializationContext context) {
            objectMapper = new ObjectMapper()
                    // FIX: Jackson's default fails on any field MovieViewEvent doesn't declare, so
                    // the backend adding a new field to its event would have made every event
                    // unreadable -- ignore unknown fields instead.
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            skippedMovieViewEvents = context.getMetricGroup().counter("skippedMovieViewEvents");
        }

        @Override
        public MovieViewEvent deserialize(byte[] message) {
            // FIX: a single malformed record used to throw straight out of here, failing the
            // task -- and since the record is still at the same offset after restoring, every
            // restart hit it again until the restart strategy's attempt cap was used up and the
            // whole job went to FAILED. Returning null makes Flink's Kafka source skip the record
            // (it only collects non-null results); the counter makes skips visible.
            try {
                MovieViewEvent event = objectMapper.readValue(message, MovieViewEvent.class);
                // Parses fine but unusable downstream -- movieId is what the similarity lookup
                // and candidate scoring key on.
                if (event == null || event.movieId() == null) {
                    return skip("missing movieId", message, null);
                }
                return event;
            } catch (IOException e) {
                return skip("unparseable JSON", message, e);
            }
        }

        private MovieViewEvent skip(String reason, byte[] message, Exception cause) {
            skippedMovieViewEvents.inc();
            LOG.warn("Skipping movie view event ({}): {}", reason, new String(message, StandardCharsets.UTF_8), cause);
            return null;
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
