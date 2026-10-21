package com.movierec.backend.exception;

/**
 * Thrown when the geocoding service can't answer right now (upstream error or timeout, or too
 * many lookups queued behind its rate limit). Mapped to {@code 503 Service Unavailable} by
 * {@link GlobalExceptionHandler}.
 */
public class GeocodingUnavailableException extends RuntimeException {

    public GeocodingUnavailableException(String message) {
        super(message);
    }

    public GeocodingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
