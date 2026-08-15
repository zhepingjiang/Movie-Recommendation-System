package com.movierec.backend.dto;

import java.time.Instant;

/**
 * Standard error body returned by {@link com.movierec.backend.exception.GlobalExceptionHandler}.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {}
