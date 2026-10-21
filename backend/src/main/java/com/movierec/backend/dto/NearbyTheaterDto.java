package com.movierec.backend.dto;

import java.util.List;

/**
 * A theater within the search radius, with its upcoming showtimes for the requested movie.
 *
 * @param id theater id
 * @param name theater name
 * @param brand chain name (e.g. "Cineplex"), or null for independents
 * @param address street address, or null if OpenStreetMap has none
 * @param city city, or null if OpenStreetMap has none
 * @param lat theater latitude in degrees
 * @param lng theater longitude in degrees
 * @param distanceKm straight-line distance from the search point, rounded to 1 decimal
 * @param showtimes upcoming showtimes, earliest first; never empty
 */
public record NearbyTheaterDto(
        Long id,
        String name,
        String brand,
        String address,
        String city,
        double lat,
        double lng,
        double distanceKm,
        List<ShowtimeSlotDto> showtimes) {
}
