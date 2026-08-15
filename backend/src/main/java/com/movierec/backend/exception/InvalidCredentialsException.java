package com.movierec.backend.exception;

/**
 * Thrown on login when the identifier or password is wrong. The message is deliberately
 * generic — callers must not distinguish "unknown user" from "wrong password," which would
 * let an attacker enumerate valid usernames/emails. Mapped to {@code 401 Unauthorized} by
 * {@link GlobalExceptionHandler}.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
