package com.movierec.backend.dto;

import java.time.Instant;

/**
 * One upcoming screening at a theater.
 *
 * @param startTime when the screening starts
 * @param format screening format, e.g. "STANDARD", "IMAX", "3D"
 */
public record ShowtimeSlotDto(Instant startTime, String format) {
}
