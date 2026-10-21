package com.movierec.backend.service;

/**
 * Orderings for the nearby-theaters search.
 */
public enum NearbyTheaterSort {
    /** Closest theater first. */
    DISTANCE,
    /** Theater with the earliest upcoming showtime first; ties broken by distance. */
    NEXT_SHOWTIME
}
