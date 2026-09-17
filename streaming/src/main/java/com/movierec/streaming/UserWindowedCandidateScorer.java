package com.movierec.streaming;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * Windowed equivalent of {@code content_based_training.aggregate_user_scores}: for one user's
 * window of {@link MovieViewEvent}s, looks up each viewed movie's cached neighbors, scores
 * candidates via {@link CandidateScorer}, and emits the top {@link #TOP_CANDIDATES_PER_USER}.
 */
public class UserWindowedCandidateScorer extends ProcessWindowFunction<MovieViewEvent, ScoredCandidate, Long, TimeWindow> {

    // Matches content_based_training.py's USER_PERSIST_N -- the number of candidates persisted
    // per user in the offline pipeline.
    private static final int TOP_CANDIDATES_PER_USER = 50;

    private final MovieSimilarityLookup movieSimilarityLookup;

    public UserWindowedCandidateScorer(MovieSimilarityLookup movieSimilarityLookup) {
        this.movieSimilarityLookup = movieSimilarityLookup;
    }

    @Override
    public void process(Long userId, Context context, Iterable<MovieViewEvent> events, Collector<ScoredCandidate> out)
            throws Exception {
        Set<Long> viewedMovieIds = new HashSet<>();
        for (MovieViewEvent event : events) {
            viewedMovieIds.add(event.movieId());
        }

        Map<Long, List<ScoredNeighbor>> neighborsByViewedMovie = new HashMap<>();
        for (Long viewedMovieId : viewedMovieIds) {
            neighborsByViewedMovie.put(viewedMovieId, movieSimilarityLookup.findTopSimilarMovies(viewedMovieId));
        }

        Map<Long, Double> scoredCandidates = CandidateScorer.scoreCandidates(viewedMovieIds, neighborsByViewedMovie);

        scoredCandidates.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(TOP_CANDIDATES_PER_USER)
                .forEach(entry -> out.collect(new ScoredCandidate(userId, entry.getKey(), entry.getValue())));
    }
}
