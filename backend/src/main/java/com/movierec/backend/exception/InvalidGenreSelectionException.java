package com.movierec.backend.exception;

/**
 * Thrown when registration is submitted with no resolvable genre picks — either the {@code genres}
 * field was empty, or every submitted name failed to match an active {@link
 * com.movierec.backend.entity.Genre}. Mapped to {@code 400 Bad Request} by {@link
 * GlobalExceptionHandler}.
 */
public class InvalidGenreSelectionException extends RuntimeException {

    public InvalidGenreSelectionException(String message) {
        super(message);
    }
}
