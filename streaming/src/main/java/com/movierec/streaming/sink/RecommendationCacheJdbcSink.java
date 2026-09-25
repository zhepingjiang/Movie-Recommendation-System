package com.movierec.streaming.sink;

import com.movierec.streaming.dto.ScoredCandidate;
import com.movierec.streaming.dto.ScoredCandidateBatch;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import org.apache.flink.metrics.Gauge;

/**
 * Writes each window firing's {@link ScoredCandidateBatch} into {@code recommendation_cache}
 * under {@code model_version = "nearline_v1"} -- same table/shape the offline jobs
 * (content_based_training.py, svd_training.py, recommendation_blending.py) already write to.
 *
 * <p>Batches are buffered per user (see {@link Writer#write}) and flushed together as one
 * transaction (see {@link Writer#flush}): delete every buffered user's existing {@code
 * nearline_v1} rows, then insert the new ones -- the same replace-not-upsert pattern the offline
 * jobs use, just scoped to the buffered users instead of the whole model_version. This makes a
 * replayed/recovered window idempotent: recomputing the same window (decay is keyed off
 * window-end, not wall-clock time) reproduces the same delete+insert, not a duplicate. It also
 * needs no exactly-once/2PC sink machinery -- at-least-once delivery plus an idempotent write is
 * sufficient, which is why {@link Writer} doesn't implement {@code SupportsCommitter}: the plain
 * non-committing {@link SinkWriter} contract has no 2PC overhead to pay for.
 */
public class RecommendationCacheJdbcSink implements Sink<ScoredCandidateBatch> {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public RecommendationCacheJdbcSink(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    // FIX: was built on RichSinkFunction, the legacy SinkFunction API Flink 2.x moved into a
    // .legacy package -- new sinks should be built on the current Sink/SinkWriter API instead.
    public SinkWriter<ScoredCandidateBatch> createWriter(WriterInitContext context) throws IOException {
        return new Writer(jdbcUrl, username, password, context);
    }

    private static final class Writer implements SinkWriter<ScoredCandidateBatch> {

        // Must match models/content_based_training.py's / svd_training.py's /
        // recommendation_blending.py's MODEL_VERSION convention -- there's no shared source of
        // truth between the Java and Python modules (same tradeoff as
        // CachedMovieSimilarityLookup.MODEL_VERSION).
        private static final String MODEL_VERSION = "nearline_v1";

        private static final String DELETE_USER_ROWS_SQL =
                "DELETE FROM recommendation_cache WHERE user_id = ? AND model_version = ?";
        private static final String INSERT_CANDIDATE_SQL = "INSERT INTO recommendation_cache "
                + "(user_id, movie_id, model_version, score, generated_at) VALUES (?, ?, ?, ?, ?)";

        // Safety valve, not the primary flush trigger -- flush(), called by the Flink runtime
        // before every checkpoint, is what normally bounds how much accumulates here (one
        // buffered batch per active user per checkpoint interval). This only matters if that
        // interval is ever widened enough for buffered users to grow unreasonably large between
        // checkpoints.
        private static final int MAX_BUFFERED_USERS_BEFORE_EARLY_FLUSH = 500;

        private final Connection connection;

        // Keyed by userId rather than a plain list: if two window firings for the same user land
        // before the next flush, the newer batch already carries that user's complete top-N, so
        // it simply replaces the older buffered one instead of both being written.
        private final Map<Long, ScoredCandidateBatch> bufferedBatchesByUserId = new LinkedHashMap<>();

        // End-to-end freshness SLI for the nearline layer: at the most recent commit, how long
        // after its window closed (in event time) the stalest committed batch reached Postgres.
        // That covers watermark delay + waiting for the next checkpoint's flush + the write
        // itself; the remaining piece of view-to-row latency (view -> end of the first window
        // containing it) is bounded by WINDOW_SLIDE by design, so it doesn't need measuring.
        // Holds its last value between commits (e.g. during no traffic), which is fine -- nothing
        // is getting staler if nothing is being produced.
        private volatile long lastCommitOutputLagMillis;

        private Writer(String jdbcUrl, String username, String password, WriterInitContext context)
                throws IOException {
            context.metricGroup().gauge("outputFreshnessLagMillis", (Gauge<Long>) () -> lastCommitOutputLagMillis);
            try {
                // FIX (same root cause CachedMovieSimilarityLookup works around): DriverManager's
                // driver registry is JVM-wide and populated once via a ServiceLoader scan through
                // whichever classloader first triggered it. TaskManager JVMs are long-lived
                // across job (re)submissions and each job gets its own isolated classloader, so a
                // job submitted after an earlier one can hit "No suitable driver found" even
                // though the postgres driver is bundled in its jar. Explicitly loading the driver
                // class here forces its static registration block to run against this job's own
                // classloader.
                Class.forName("org.postgresql.Driver");
                connection = DriverManager.getConnection(jdbcUrl, username, password);
                connection.setAutoCommit(false);
            } catch (ClassNotFoundException | SQLException e) {
                throw new IOException("Failed to open recommendation_cache JDBC connection", e);
            }
        }

        @Override
        // FIX: used to delete+insert+commit synchronously per batch here, i.e. one JDBC
        // transaction per active user per window firing (every WINDOW_SLIDE) on a single
        // connection -- buffering here and committing in flush() instead turns that into one
        // transaction per checkpoint interval covering every user buffered since the last one.
        public void write(ScoredCandidateBatch batch, Context context) throws IOException {
            bufferedBatchesByUserId.put(batch.userId(), batch);
            if (bufferedBatchesByUserId.size() >= MAX_BUFFERED_USERS_BEFORE_EARLY_FLUSH) {
                flushBuffer();
            }
        }

        @Override
        public void flush(boolean endOfInput) throws IOException {
            // Flink calls this before every checkpoint (and at end of input), which is what
            // turns "buffer in write(), commit in flush()" into one transaction per checkpoint
            // interval instead of one per user per window firing -- collapsing what would
            // otherwise be one small transaction per active user every window slide into a
            // single batched delete + batched insert covering everyone buffered since the last
            // checkpoint.
            flushBuffer();
        }

        private void flushBuffer() throws IOException {
            if (bufferedBatchesByUserId.isEmpty()) {
                return;
            }

            try {
                try (PreparedStatement deleteStatement = connection.prepareStatement(DELETE_USER_ROWS_SQL)) {
                    for (ScoredCandidateBatch batch : bufferedBatchesByUserId.values()) {
                        deleteStatement.setLong(1, batch.userId());
                        deleteStatement.setString(2, MODEL_VERSION);
                        deleteStatement.addBatch();
                    }
                    deleteStatement.executeBatch();
                }

                try (PreparedStatement insertStatement = connection.prepareStatement(INSERT_CANDIDATE_SQL)) {
                    boolean hasBufferedCandidates = false;
                    for (ScoredCandidateBatch batch : bufferedBatchesByUserId.values()) {
                        Timestamp generatedAt = new Timestamp(batch.windowEndEpochMilli());
                        for (ScoredCandidate candidate : batch.candidates()) {
                            // FIX: this used to read candidate.userId() -- a separate copy of the
                            // same value carried on ScoredCandidate itself, always equal to
                            // batch.userId() by convention (every candidate in a batch is
                            // constructed from that batch's single userId) but never enforced,
                            // while the DELETE above already used batch.userId(). Reading from
                            // the batch for both closes off a divergence between the two that
                            // nothing would have caught.
                            insertStatement.setLong(1, batch.userId());
                            insertStatement.setLong(2, candidate.movieId());
                            insertStatement.setString(3, MODEL_VERSION);
                            insertStatement.setDouble(4, candidate.score());
                            insertStatement.setTimestamp(5, generatedAt);
                            insertStatement.addBatch();
                            hasBufferedCandidates = true;
                        }
                    }
                    if (hasBufferedCandidates) {
                        insertStatement.executeBatch();
                    }
                }

                connection.commit();
                long committedAtMillis = System.currentTimeMillis();
                long oldestWindowEndMillis = bufferedBatchesByUserId.values().stream()
                        .mapToLong(ScoredCandidateBatch::windowEndEpochMilli)
                        .min()
                        .getAsLong();
                lastCommitOutputLagMillis = committedAtMillis - oldestWindowEndMillis;
                bufferedBatchesByUserId.clear();
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IOException(
                        "Failed to flush recommendation_cache batch for " + bufferedBatchesByUserId.size() + " user(s)",
                        e);
            }
        }

        private void rollbackQuietly() {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                // Best-effort -- the SQLException that triggered this rollback is what actually
                // propagates and fails the write.
            }
        }

        @Override
        public void close() throws Exception {
            if (!connection.isClosed()) {
                connection.close();
            }
        }
    }
}
