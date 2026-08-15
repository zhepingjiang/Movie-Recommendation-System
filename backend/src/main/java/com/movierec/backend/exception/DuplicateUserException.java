package com.movierec.backend.exception;

/**
 * Thrown when registration is attempted with a username or email that's already taken.
 * Mapped to {@code 409 Conflict} by {@link GlobalExceptionHandler}.
 */
public class DuplicateUserException extends RuntimeException {

    public DuplicateUserException(String message) {
        super(message);
    }
}
