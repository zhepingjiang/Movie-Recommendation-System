package com.movierec.backend.exception;

/**
 * Thrown when an operation references a movie id that doesn't exist. Mapped to
 * {@code 404 Not Found} by {@link GlobalExceptionHandler}.
 */
public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(Long movieId) {
        super("Movie " + movieId + " does not exist");
    }
}
