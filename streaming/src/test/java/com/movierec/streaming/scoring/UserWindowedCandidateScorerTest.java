package com.movierec.streaming.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.movierec.streaming.events.MovieViewEvent;
import com.movierec.streaming.events.ScoredCandidate;
import com.movierec.streaming.events.ScoredNeighbor;
import com.movierec.streaming.similarity.FakeMovieSimilarityLookup;
import com.movierec.streaming.similarity.MovieSimilarityLookup;
import java.util.List;
import java.util.Map;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperatorBuilder;
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real Flink windowing pipeline (SlidingEventTimeWindows + EventTimeTrigger +
 * {@link UserWindowedCandidateScorer}) with synthetic timestamped events and watermarks, rather
 * than calling the window function directly -- proves the window actually groups and fires as
 * intended, on top of the aggregation math already covered by {@link CandidateScorerTest}.
 */
class UserWindowedCandidateScorerTest {

    private static final long WINDOW_SIZE_MILLIS = 10 * 60 * 1000L;
    private static final long WINDOW_SLIDE_MILLIS = 60 * 1000L;

    @Test
    void windowEmitsScoredCandidatesForMoviesViewedWithinIt() throws Exception {
        FakeMovieSimilarityLookup similarityLookup = new FakeMovieSimilarityLookup(Map.of(
                1L, List.of(new ScoredNeighbor(10L, 0.8), new ScoredNeighbor(20L, 0.4)),
                2L, List.of(new ScoredNeighbor(10L, 0.6))));

        KeyedOneInputStreamOperatorTestHarness<Long, MovieViewEvent, ScoredCandidate> testHarness =
                createTestHarness(similarityLookup);
        try {
            testHarness.open();

            long baseTimeMillis = 0L;
            testHarness.processElement(new MovieViewEvent(1L, 42L, baseTimeMillis), baseTimeMillis);
            testHarness.processElement(new MovieViewEvent(2L, 42L, baseTimeMillis + 1_000L), baseTimeMillis + 1_000L);
            // With size=10min/slide=1min, an event at t=0 falls into 10 overlapping windows;
            // the earliest-ending one closes at exactly one slide interval (1min). Advancing the
            // watermark just past that fires only that single window, not all 10.
            testHarness.processWatermark(new Watermark(WINDOW_SLIDE_MILLIS + 1));

            List<ScoredCandidate> scoredCandidates = testHarness.extractOutputValues();

            assertEquals(2, scoredCandidates.size());
            Map<Long, Double> scoreByMovieId = scoredCandidates.stream()
                    .collect(java.util.stream.Collectors.toMap(ScoredCandidate::movieId, ScoredCandidate::score));
            assertEquals(42L, scoredCandidates.get(0).userId());
            assertEquals(0.7, scoreByMovieId.get(10L), 1e-9);
            assertEquals(0.4, scoreByMovieId.get(20L), 1e-9);
        } finally {
            testHarness.close();
        }
    }

    private static KeyedOneInputStreamOperatorTestHarness<Long, MovieViewEvent, ScoredCandidate> createTestHarness(
            MovieSimilarityLookup similarityLookup) throws Exception {
        KeySelector<MovieViewEvent, Long> userIdKeySelector = MovieViewEvent::userId;

        WindowOperatorBuilder<MovieViewEvent, Long, TimeWindow> windowOperatorBuilder = new WindowOperatorBuilder<>(
                SlidingEventTimeWindows.of(
                        java.time.Duration.ofMillis(WINDOW_SIZE_MILLIS), java.time.Duration.ofMillis(WINDOW_SLIDE_MILLIS)),
                EventTimeTrigger.create(),
                new ExecutionConfig(),
                TypeInformation.of(MovieViewEvent.class),
                userIdKeySelector,
                Types.LONG);

        OneInputStreamOperator<MovieViewEvent, ScoredCandidate> windowOperator =
                windowOperatorBuilder.process(new UserWindowedCandidateScorer(similarityLookup));

        return new KeyedOneInputStreamOperatorTestHarness<>(windowOperator, userIdKeySelector, Types.LONG);
    }
}
