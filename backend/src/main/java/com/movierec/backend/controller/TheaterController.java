package com.movierec.backend.controller;

import com.movierec.backend.dto.NearbyTheaterDto;
import com.movierec.backend.dto.PagedResponse;
import com.movierec.backend.service.NearbyTheaterService;
import com.movierec.backend.service.NearbyTheaterSort;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for finding theaters near the user. Public: no account needed to look up
 * showtimes.
 */
@RestController
@RequestMapping("/api/theatres")
@RequiredArgsConstructor
public class TheaterController {

    private final NearbyTheaterService nearbyTheaterService;

    /**
     * Lists theaters within a radius that have upcoming showtimes for a movie.
     *
     * @param lat latitude of the search point in degrees
     * @param lng longitude of the search point in degrees
     * @param radiusKm search radius in kilometres, greater than 0 and at most 50
     * @param movieId movie whose showtimes to list
     * @param sort {@code distance} (nearest first, the default) or {@code nextShowtime}
     * @param page zero-based page index
     * @param size page size
     * @return a page of nearby theaters with distance and showtimes; 400 on invalid parameters,
     *     404 if the movie doesn't exist
     */
    @GetMapping("/nearby")
    public PagedResponse<NearbyTheaterDto> getNearbyTheaters(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
            @RequestParam(defaultValue = "5") @DecimalMin(value = "0", inclusive = false) @DecimalMax("50")
                    double radiusKm,
            @RequestParam long movieId,
            @RequestParam(defaultValue = "distance") @Pattern(regexp = "distance|nextShowtime") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        NearbyTheaterSort theaterSort =
                sort.equals("nextShowtime") ? NearbyTheaterSort.NEXT_SHOWTIME : NearbyTheaterSort.DISTANCE;
        return nearbyTheaterService.findNearbyTheaters(lat, lng, radiusKm, movieId, theaterSort, page, size);
    }
}
