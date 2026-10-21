package com.movierec.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.movierec.backend.dto.GeocodeResultDto;
import com.movierec.backend.exception.GeocodingUnavailableException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Turns a typed address into coordinates using OpenStreetMap's public Nominatim service, limited
 * to Ontario (the only province with theater data).
 *
 * <p>Nominatim's usage policy (https://operations.osmfoundation.org/policies/nominatim/) allows
 * at most 1 request per second, requires an identifying User-Agent, asks that results be cached,
 * and forbids search-as-you-type -- so this is called on submit only, all lookups share one
 * 1/s throttle, and answers are cached in memory. Fine for a demo; production traffic needs a
 * self-hosted or paid geocoder.
 */
@Slf4j
@Service
public class GeocodingService {

    /** Ontario's bounding box as Nominatim's viewbox: west, north, east, south. */
    private static final String ONTARIO_VIEWBOX = "-95.16,56.86,-74.32,41.67";
    private static final int MAX_RESULTS = 5;
    private static final long MIN_MILLIS_BETWEEN_REQUESTS = 1000;
    /** A lookup that would have to wait longer than this for its throttle slot is refused instead. */
    private static final long MAX_THROTTLE_WAIT_MILLIS = 3000;
    private static final int MAX_CACHED_QUERIES = 1000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient nominatimClient;
    private final Map<String, List<GeocodeResultDto>> resultsByQuery =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<GeocodeResultDto>> eldest) {
                    return size() > MAX_CACHED_QUERIES;
                }
            });
    private final Object throttleLock = new Object();
    private long nextRequestAllowedAtMillis = 0;

    /**
     * @param nominatimBaseUrl base URL of the Nominatim instance
     * @param userAgent identifying User-Agent sent on every request, as Nominatim's policy requires
     */
    public GeocodingService(
            @Value("${geocoding.nominatim.base-url}") String nominatimBaseUrl,
            @Value("${geocoding.nominatim.user-agent}") String userAgent) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(REQUEST_TIMEOUT);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        this.nominatimClient = RestClient.builder()
                .baseUrl(nominatimBaseUrl)
                .defaultHeader("User-Agent", userAgent)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Looks up places in Ontario matching a typed address.
     *
     * @param address free-text address or place name, e.g. "Union Station, Toronto"
     * @return up to {@value #MAX_RESULTS} matches, best first; empty if nothing in Ontario matches
     * @throws GeocodingUnavailableException if Nominatim fails or the throttle queue is full
     */
    public List<GeocodeResultDto> geocode(String address) {
        String normalizedQuery = address.trim().replaceAll("\s+", " ").toLowerCase(Locale.ROOT);
        List<GeocodeResultDto> cachedResults = resultsByQuery.get(normalizedQuery);
        if (cachedResults != null) {
            return cachedResults;
        }

        waitForThrottleSlot();
        NominatimPlace[] places;
        try {
            places = nominatimClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("q", normalizedQuery)
                            .queryParam("format", "jsonv2")
                            .queryParam("countrycodes", "ca")
                            .queryParam("viewbox", ONTARIO_VIEWBOX)
                            .queryParam("bounded", 1)
                            .queryParam("limit", MAX_RESULTS)
                            .build())
                    .retrieve()
                    .body(NominatimPlace[].class);
        } catch (RestClientException e) {
            // The query itself is the user's address, so it's deliberately left out of the log.
            log.warn("Nominatim lookup failed: {}", e.getMessage());
            throw new GeocodingUnavailableException("Address lookup is unavailable right now", e);
        }

        List<GeocodeResultDto> results = places == null ? List.of() : Arrays.stream(places)
                .map(place -> new GeocodeResultDto(
                        place.displayName(), Double.parseDouble(place.lat()), Double.parseDouble(place.lon())))
                .toList();
        resultsByQuery.put(normalizedQuery, results);
        return results;
    }

    /**
     * Blocks until this lookup may call Nominatim, keeping all lookups at least
     * {@value #MIN_MILLIS_BETWEEN_REQUESTS} ms apart. Refuses rather than queueing a caller behind
     * more than {@value #MAX_THROTTLE_WAIT_MILLIS} ms of other lookups, so a burst can't tie up
     * request threads.
     */
    private void waitForThrottleSlot() {
        long waitMillis;
        synchronized (throttleLock) {
            long nowMillis = System.currentTimeMillis();
            long slotMillis = Math.max(nowMillis, nextRequestAllowedAtMillis);
            waitMillis = slotMillis - nowMillis;
            if (waitMillis > MAX_THROTTLE_WAIT_MILLIS) {
                throw new GeocodingUnavailableException("Too many address lookups right now, try again shortly");
            }
            nextRequestAllowedAtMillis = slotMillis + MIN_MILLIS_BETWEEN_REQUESTS;
        }
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GeocodingUnavailableException("Address lookup was interrupted", e);
            }
        }
    }

    /** The fields this service reads from one Nominatim jsonv2 search result. */
    record NominatimPlace(@JsonProperty("display_name") String displayName, String lat, String lon) {}
}
