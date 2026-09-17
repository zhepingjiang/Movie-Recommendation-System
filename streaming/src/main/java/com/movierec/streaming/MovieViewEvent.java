package com.movierec.streaming;

/**
 * Mirrors {@code com.movierec.backend.event.MovieViewEvent} -- the JSON payload the backend
 * publishes to the {@code movie-view-events} topic. Kept as a local copy rather than a shared
 * dependency since {@code streaming/} is a standalone Gradle module with its own Flink-compatible
 * Java 17 toolchain, separate from {@code backend/}'s Java 21 build.
 */
public record MovieViewEvent(Long movieId, Long userId, long occurredAtEpochMilli) {
}
