package com.movierec.backend.controller;

import com.movierec.backend.dto.GeocodeResultDto;
import com.movierec.backend.service.GeocodingService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that turns a typed address into coordinates for the nearby-theaters page. The
 * browser never calls the geocoder directly, so it gets one shared rate limit and cache (see
 * {@link GeocodingService}).
 */
@RestController
@RequestMapping("/api/geocode")
@RequiredArgsConstructor
public class GeocodeController {

    private final GeocodingService geocodingService;

    /**
     * Looks up places in Ontario matching an address.
     *
     * @param q free-text address or place name
     * @return matching places, best first (empty if none); 400 on a blank or over-long query, 503
     *     if the geocoder is unavailable or busy
     */
    @GetMapping
    public List<GeocodeResultDto> geocode(@RequestParam @NotBlank @Size(max = 200) String q) {
        return geocodingService.geocode(q);
    }
}
