package com.movierec.backend.dto;

/**
 * One place matching a typed address.
 *
 * @param displayName the full place description, e.g. "Union Station, Front Street West, Toronto, ..."
 * @param lat latitude in degrees
 * @param lng longitude in degrees
 */
public record GeocodeResultDto(String displayName, double lat, double lng) {
}
