package com.movierec.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

/**
 * Guards the fix in {@link MovieViewEventLoggerJob#createStreamExecutionEnvironment()}: without
 * checkpointing and a restart strategy, a task failure just kills the job outright instead of
 * retrying, and {@code RecommendationCacheJdbcSink}'s replay-is-idempotent design has nothing to
 * actually replay from.
 *
 * <p>This only checks that the environment comes back configured correctly -- constructing a
 * {@link StreamExecutionEnvironment} and calling {@code enableCheckpointing()} does no I/O by
 * itself (only {@code env.execute()} does), so it's cheap to assert on directly. Proving that a
 * real failure actually recovers and replays a window needs a running cluster with an injected
 * failure, which belongs in a live check against the docker-compose stack (kill the taskmanager
 * mid-window, confirm the job restarts and {@code nearline_v1} rows still land correctly), not a
 * unit test here.
 */
class MovieViewEventLoggerJobTest {

    // Duplicated from MovieViewEventLoggerJob rather than reaching into its private constants --
    // same tradeoff as UserWindowedCandidateScorerTest's WINDOW_SIZE_MILLIS/WINDOW_SLIDE_MILLIS.
    private static final long EXPECTED_CHECKPOINT_INTERVAL_MILLIS = 30_000L;
    private static final String EXPECTED_RESTART_STRATEGY = "exponential-delay";
    // Matches application.properties' flink.checkpoint.dir default -- assumes FLINK_CHECKPOINT_DIR
    // isn't set in the environment running this test, same assumption the job's own
    // KAFKA_BOOTSTRAP_SERVERS/POSTGRES_* config already makes.
    private static final String EXPECTED_CHECKPOINT_DIRECTORY = "file:///opt/flink-checkpoints";

    @Test
    void checkpointingIsEnabledWithAnExponentialBackoffRestartStrategy() {
        StreamExecutionEnvironment env = MovieViewEventLoggerJob.createStreamExecutionEnvironment();

        assertTrue(env.getCheckpointConfig().isCheckpointingEnabled());
        assertEquals(EXPECTED_CHECKPOINT_INTERVAL_MILLIS, env.getCheckpointConfig().getCheckpointInterval());

        ReadableConfig configuration = env.getConfiguration();
        assertEquals(EXPECTED_RESTART_STRATEGY, configuration.get(RestartStrategyOptions.RESTART_STRATEGY));
        assertEquals(EXPECTED_CHECKPOINT_DIRECTORY, configuration.get(CheckpointingOptions.CHECKPOINTS_DIRECTORY));

        assertEquals(
                Duration.ofSeconds(1),
                configuration.get(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_INITIAL_BACKOFF));
        assertEquals(
                Duration.ofMinutes(1),
                configuration.get(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_MAX_BACKOFF));
        assertEquals(
                1.5, configuration.get(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_BACKOFF_MULTIPLIER));
        assertEquals(
                Duration.ofHours(1),
                configuration.get(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_RESET_BACKOFF_THRESHOLD));
        assertEquals(
                0.1, configuration.get(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_JITTER_FACTOR));
        // Not Flink's default (infinite) -- the whole point of setting this one is a finite
        // ceiling so a permanent failure eventually surfaces as FAILED instead of restarting
        // forever.
        assertEquals(10, configuration.get(RestartStrategyOptions.RESTART_STRATEGY_EXPONENTIAL_DELAY_ATTEMPTS));
    }
}
