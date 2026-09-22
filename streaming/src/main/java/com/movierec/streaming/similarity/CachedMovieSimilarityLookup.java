package com.movierec.streaming.similarity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.movierec.streaming.dto.ScoredNeighbor;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a batch of movies' cached content-based neighbors from {@code movie_similarity_cache},
 * populated offline by {@code recommendation/models/content_based_training.py}. Backed by a
 * bounded (size + TTL) local Caffeine cache: a batch call first serves whatever it can from the
 * cache, then issues a single {@code WHERE movie_id = ANY(?)} query for the rest, rather than
 * loading the whole table into memory -- keeps memory use capped by policy instead of by however
 * large the table grows, at the cost of occasional cache misses hitting Postgres.
 *
 * <p>{@code connection} and {@code cache} are transient and built lazily on first use rather than
 * in a constructor or Flink lifecycle method, since this class -- like any Flink function -- is
 * serialized to ship to task managers, and neither a live {@link Connection} nor a {@link Cache}
 * is serializable.
 */
public class CachedMovieSimilarityLookup implements MovieSimilarityLookup {

    private static final String FIND_TOP_SIMILAR_MOVIES_SQL =
            "SELECT movie_id, similar_movie_id, score FROM movie_similarity_cache "
                    + "WHERE movie_id = ANY(?) AND model_version = ? ORDER BY movie_id, score DESC";

    // Must match models/content_based_training.py's MODEL_VERSION -- there's no shared source of
    // truth between the Java and Python modules (same tradeoff as the backend's
    // ContentBasedRecommendationService.MODEL_VERSION).
    private static final String MODEL_VERSION = "content_v1";

    // A deliberately modest cap relative to the current ~9,730-movie catalog -- the point isn't
    // that everything fits today, it's that memory use stays bounded by this policy even if the
    // catalog grows well past what a full in-memory copy could hold.
    private static final long CACHE_MAX_SIZE = 2_000;
    // The offline job that populates movie_similarity_cache doesn't run continuously, but this
    // keeps entries from going stale indefinitely between runs.
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private transient Connection connection;
    private transient Cache<Long, List<ScoredNeighbor>> cache;

    public CachedMovieSimilarityLookup(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public Map<Long, List<ScoredNeighbor>> findTopSimilarMovies(Set<Long> movieIds) throws Exception {
        Cache<Long, List<ScoredNeighbor>> similarityCache = cache();

        // FIX: previously every viewed movie hit Postgres directly, once per window firing --
        // with 10 overlapping sliding windows per event, the same movie was re-queried up to 10x.
        // Serving whatever's already cached avoids re-hitting the DB for movies looked up in a
        // prior (still-live) window.
        Map<Long, List<ScoredNeighbor>> result = new HashMap<>(similarityCache.getAllPresent(movieIds));

        Set<Long> missedMovieIds = new HashSet<>(movieIds);
        missedMovieIds.removeAll(result.keySet());
        if (!missedMovieIds.isEmpty()) {
            // FIX: batches every cache miss into one round trip instead of one query per movie.
            Map<Long, List<ScoredNeighbor>> fetchedNeighborsByMovieId = fetchFromDatabase(missedMovieIds);
            similarityCache.putAll(fetchedNeighborsByMovieId);
            result.putAll(fetchedNeighborsByMovieId);
        }

        return result;
    }

    private Map<Long, List<ScoredNeighbor>> fetchFromDatabase(Set<Long> movieIds) throws Exception {
        Connection dbConnection = connection();

        Map<Long, List<ScoredNeighbor>> neighborsByMovieId = new HashMap<>();
        // Movies with genuinely no similarity rows still need an entry (an empty list) --
        // otherwise they'd never register as a cache hit and would re-query Postgres forever.
        for (Long movieId : movieIds) {
            neighborsByMovieId.put(movieId, new ArrayList<>());
        }

        try (PreparedStatement statement = dbConnection.prepareStatement(FIND_TOP_SIMILAR_MOVIES_SQL)) {
            Array movieIdsArray = dbConnection.createArrayOf("bigint", movieIds.toArray());
            statement.setArray(1, movieIdsArray);
            statement.setString(2, MODEL_VERSION);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long movieId = resultSet.getLong("movie_id");
                    ScoredNeighbor neighbor =
                            new ScoredNeighbor(resultSet.getLong("similar_movie_id"), resultSet.getDouble("score"));
                    neighborsByMovieId.get(movieId).add(neighbor);
                }
            }
        }

        return neighborsByMovieId;
    }

    private Connection connection() throws Exception {
        if (connection == null || connection.isClosed()) {
            // FIX: DriverManager's driver registry is JVM-wide and populated once via a
            // ServiceLoader scan through whichever classloader first triggered it -- since
            // TaskManager JVMs are long-lived across job (re)submissions and each job gets its
            // own isolated classloader, a job submitted after an earlier one can hit "No suitable
            // driver found" even though the postgres driver is bundled in its jar, because
            // DriverManager never re-scanned using *this* job's classloader. Explicitly loading
            // the driver class here forces its static registration block to run against this
            // job's own classloader, regardless of what ran before it in this JVM.
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(jdbcUrl, username, password);
        }
        return connection;
    }

    private Cache<Long, List<ScoredNeighbor>> cache() {
        if (cache == null) {
            // FIX: bounded (size + TTL), not a full unbounded copy of movie_similarity_cache --
            // memory use stays capped by this policy regardless of how large that table grows.
            cache = Caffeine.newBuilder().maximumSize(CACHE_MAX_SIZE).expireAfterWrite(CACHE_TTL).build();
        }
        return cache;
    }
}
