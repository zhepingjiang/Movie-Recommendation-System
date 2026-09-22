package com.movierec.streaming.scoring;

import com.movierec.streaming.dto.ScoredCandidate;
import com.movierec.streaming.dto.ScoredCandidateBatch;
import com.movierec.streaming.dto.ScoredNeighbor;
import com.movierec.streaming.events.MovieViewEvent;
import com.movierec.streaming.similarity.MovieSimilarityLookup;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * Windowed equivalent of {@code content_based_training.aggregate_user_scores}: for one user's
 * window of {@link MovieViewEvent}s, looks up each viewed movie's cached neighbors, scores
 * candidates via {@link CandidateScorer} (recency-decayed, using the window's end as the decay
 * reference point -- deterministic and replay-safe, unlike wall-clock time), and emits the top
 * {@link #TOP_CANDIDATES_PER_USER} as a single {@link com.movierec.streaming.dto.ScoredCandidateBatch}
 * -- one per user per window firing, so the JDBC sink can replace that user's prior candidates
 * atomically instead of accumulating on top of them.
 */
public class UserWindowedCandidateScorer
        extends ProcessWindowFunction<MovieViewEvent, ScoredCandidateBatch, Long, TimeWindow> {

    // Matches content_based_training.py's USER_PERSIST_N -- the number of candidates persisted
    // per user in the offline pipeline.
    private static final int TOP_CANDIDATES_PER_USER = 50;

    // A viewed movie's contribution halves every 3 minutes of age -- inside the 10-minute window,
    // so a movie about to age out is already discounted to a small fraction of its original
    // weight rather than dropping from full weight to zero at the window boundary.
    private static final Duration RECENCY_HALF_LIFE = Duration.ofMinutes(3);

    private final MovieSimilarityLookup movieSimilarityLookup;

    public UserWindowedCandidateScorer(MovieSimilarityLookup movieSimilarityLookup) {
        this.movieSimilarityLookup = movieSimilarityLookup;
    }

    @Override
    public void process(
            Long userId, Context context, Iterable<MovieViewEvent> events, Collector<ScoredCandidateBatch> out)
            throws Exception {
        Map<Long, Long> viewedMovieIdToViewTimestampMillis = new HashMap<>();
        for (MovieViewEvent event : events) {
            // If the same movie was viewed more than once in this window, its most recent view is
            // what should determine how much it's decayed.
            viewedMovieIdToViewTimestampMillis.merge(event.movieId(), event.occurredAtEpochMilli(), Math::max);
        }

        // FIX: was one findTopSimilarMovies(movieId) call per distinct viewed movie -- now one
        // batched call for the whole window, so CachedMovieSimilarityLookup can serve cache hits
        // and fetch every miss in a single round trip instead of N round trips.
        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie =
                movieSimilarityLookup.findTopSimilarMovies(viewedMovieIdToViewTimestampMillis.keySet());

        // FIX: window end (not wall-clock "now") is the decay reference point, so replaying the
        // same events later reproduces the same scores.
        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(
                viewedMovieIdToViewTimestampMillis, neighborsByViewedMovie, context.window().getEnd(), RECENCY_HALF_LIFE);

        List<ScoredCandidate> topCandidates = new ArrayList<>();
        scoredCandidates.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(TOP_CANDIDATES_PER_USER)
                .forEach(entry -> topCandidates.add(new ScoredCandidate(entry.getKey(), entry.getValue())));

        out.collect(new ScoredCandidateBatch(userId, context.window().getEnd(), topCandidates));
    }
}
