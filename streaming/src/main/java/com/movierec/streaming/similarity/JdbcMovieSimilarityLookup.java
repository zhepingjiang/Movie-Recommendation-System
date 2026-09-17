package com.movierec.streaming.similarity;

import com.movierec.streaming.events.ScoredNeighbor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a movie's cached content-based neighbors from {@code movie_similarity_cache}, populated
 * offline by {@code recommendation/models/content_based_training.py}. Same query shape as
 * {@code MovieSimilarityCacheRepository.findByIdMovieIdAndIdModelVersionOrderByScoreDesc} on the
 * backend.
 *
 * <p>{@code connection} is transient and opened lazily on first use rather than in a constructor
 * or Flink lifecycle method, since this class -- like any Flink function -- is serialized to ship
 * to task managers, and a live {@link Connection} isn't serializable.
 */
public class JdbcMovieSimilarityLookup implements MovieSimilarityLookup {

    private static final String FIND_TOP_SIMILAR_MOVIES_SQL =
            "SELECT similar_movie_id, score FROM movie_similarity_cache "
                    + "WHERE movie_id = ? AND model_version = ? ORDER BY score DESC LIMIT ?";

    // Must match models/content_based_training.py's MODEL_VERSION -- there's no shared source of
    // truth between the Java and Python modules (same tradeoff as the backend's
    // ContentBasedRecommendationService.MODEL_VERSION).
    private static final String MODEL_VERSION = "content_v1";

    // Matches content_based_training.py's PERSIST_N -- the number of neighbors actually persisted
    // per movie, so this isn't an artificial truncation.
    private static final int TOP_K_NEIGHBORS = 20;

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private transient Connection connection;

    public JdbcMovieSimilarityLookup(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public List<ScoredNeighbor> findTopSimilarMovies(long movieId) throws Exception {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl, username, password);
        }

        try (PreparedStatement statement = connection.prepareStatement(FIND_TOP_SIMILAR_MOVIES_SQL)) {
            statement.setLong(1, movieId);
            statement.setString(2, MODEL_VERSION);
            statement.setInt(3, TOP_K_NEIGHBORS);

            List<ScoredNeighbor> topSimilarMovies = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    topSimilarMovies.add(new ScoredNeighbor(resultSet.getLong("similar_movie_id"), resultSet.getDouble("score")));
                }
            }
            return topSimilarMovies;
        }
    }
}
