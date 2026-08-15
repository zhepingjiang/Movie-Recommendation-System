package com.movierec.backend.exception;

/**
 * Thrown when an uploaded file (e.g. an avatar) fails content-type or size validation.
 * Mapped to {@code 400 Bad Request} by {@link GlobalExceptionHandler}.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
