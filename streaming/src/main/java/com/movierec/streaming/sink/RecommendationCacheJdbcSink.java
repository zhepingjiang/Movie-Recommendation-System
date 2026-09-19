package com.movierec.streaming.sink;

import com.movierec.streaming.events.ScoredCandidate;
import com.movierec.streaming.events.ScoredCandidateBatch;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;

/**
 * Writes each window firing's {@link ScoredCandidateBatch} into {@code recommendation_cache}
 * under {@code model_version = "nearline_v1"} -- same table/shape the offline jobs
 * (content_based_training.py, svd_training.py, recommendation_blending.py) already write to.
 *
 * <p>Each batch is written as one transaction: delete this user's existing {@code nearline_v1}
 * rows, then (if the batch is non-empty) insert the new ones -- the same replace-not-upsert
 * pattern the offline jobs use, just scoped to one user instead of the whole model_version, since
 * only one user's candidates change per window firing. This makes a replayed/recovered window
 * idempotent: recomputing the same window (decay is keyed off window-end, not wall-clock time)
 * reproduces the same delete+insert, not a duplicate. It also needs no exactly-once/2PC sink
 * machinery -- at-least-once delivery plus an idempotent write is sufficient, which is why {@link
 * Writer} doesn't implement {@code SupportsCommitter}: the plain non-committing {@link SinkWriter}
 * contract has no 2PC overhead to pay for.
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
        return new Writer(jdbcUrl, username, password);
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

        private final Connection connection;

        private Writer(String jdbcUrl, String username, String password) throws IOException {
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
        public void write(ScoredCandidateBatch batch, Context context) throws IOException {
            Timestamp generatedAt = new Timestamp(batch.windowEndEpochMilli());
            try {
                try (PreparedStatement deleteStatement = connection.prepareStatement(DELETE_USER_ROWS_SQL)) {
                    deleteStatement.setLong(1, batch.userId());
                    deleteStatement.setString(2, MODEL_VERSION);
                    deleteStatement.executeUpdate();
                }

                if (!batch.candidates().isEmpty()) {
                    try (PreparedStatement insertStatement = connection.prepareStatement(INSERT_CANDIDATE_SQL)) {
                        for (ScoredCandidate candidate : batch.candidates()) {
                            insertStatement.setLong(1, candidate.userId());
                            insertStatement.setLong(2, candidate.movieId());
                            insertStatement.setString(3, MODEL_VERSION);
                            insertStatement.setDouble(4, candidate.score());
                            insertStatement.setTimestamp(5, generatedAt);
                            insertStatement.addBatch();
                        }
                        insertStatement.executeBatch();
                    }
                }

                connection.commit();
            } catch (SQLException e) {
                rollbackQuietly();
                throw new IOException("Failed to write recommendation_cache batch for user " + batch.userId(), e);
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
        public void flush(boolean endOfInput) {
            // No-op: write() already commits synchronously per batch, so there's nothing
            // buffered here for flush to push out.
        }

        @Override
        public void close() throws Exception {
            if (!connection.isClosed()) {
                connection.close();
            }
        }
    }
}
