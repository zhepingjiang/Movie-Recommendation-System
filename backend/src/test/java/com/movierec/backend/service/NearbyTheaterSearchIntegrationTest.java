package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.movierec.backend.dto.NearbyTheaterDto;
import com.movierec.backend.dto.PagedResponse;
import com.movierec.backend.entity.Theater;
import com.movierec.backend.geo.GeoTestPoints;
import com.movierec.backend.geo.H3Cells;
import com.movierec.backend.repository.MovieRepository;
import com.movierec.backend.repository.TheaterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the nearby search against a real Postgres with every Flyway migration applied (including
 * the V16 Ontario seed), so the H3 IN-query, the entity's h3_cell hook and the seed's h3 values
 * are all exercised for real. Needs Docker running.
 *
 * <p>Each test runs in a rolled-back transaction, so inserted theaters don't leak between tests.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({NearbyTheaterService.class, MockShowtimeProvider.class, NearbyTheaterSearchIntegrationTest.FixedClockConfig.class})
class NearbyTheaterSearchIntegrationTest {

    /** Same major version as docker-compose.yml's postgres service. */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    private static final Instant NOW = Instant.parse("2026-10-20T20:00:00Z");

    // Middle of the boreal forest between Thunder Bay and Timmins: no seeded theater within 50 km
    // (asserted below), so synthetic theaters here are the only results.
    private static final double REMOTE_LAT = 49.5;
    private static final double REMOTE_LNG = -85.0;

    private static final double UNION_STATION_LAT = 43.6453;
    private static final double UNION_STATION_LNG = -79.3806;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired private NearbyTheaterService nearbyTheaterService;
    @Autowired private TheaterRepository theaterRepository;
    @Autowired private MovieRepository movieRepository;

    private long movieId;

    @BeforeEach
    void pickSeededMovie() {
        movieId = movieRepository.findAll(PageRequest.of(0, 1)).getContent().getFirst().getId();
    }

    private Theater saveTheaterAt(String name, double distanceKm, double bearingDegrees) {
        double[] point = GeoTestPoints.destination(REMOTE_LAT, REMOTE_LNG, distanceKm, bearingDegrees);
        return theaterRepository.saveAndFlush(Theater.builder()
                .name(name)
                .lat(point[0])
                .lng(point[1])
                .createdAt(NOW)
                .updatedAt(NOW)
                .build());
    }

    private PagedResponse<NearbyTheaterDto> searchNear(double lat, double lng, double radiusKm) {
        return nearbyTheaterService.findNearbyTheaters(lat, lng, radiusKm, movieId, NearbyTheaterSort.DISTANCE, 0, 50);
    }

    private static List<String> names(PagedResponse<NearbyTheaterDto> response) {
        return response.items().stream().map(NearbyTheaterDto::name).toList();
    }

    @Test
    void noTheatersNearbyReturnsAnEmptyPage() {
        PagedResponse<NearbyTheaterDto> response = searchNear(REMOTE_LAT, REMOTE_LNG, 50);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void radiusBoundaryIncludesJustInsideAndExcludesJustOutside() {
        saveTheaterAt("Just inside", 4.95, 30);
        saveTheaterAt("Just outside", 5.05, 210);

        assertThat(names(searchNear(REMOTE_LAT, REMOTE_LNG, 5))).containsExactly("Just inside");
    }

    @Test
    void findsTheatersAtTheEdgeOfALargeRadiusInEveryDirection() {
        // 49 km out in eight directions: these sit many H3 rings away and near the disk's edge,
        // where a too-small k would silently drop them.
        for (int bearingDegrees = 0; bearingDegrees < 360; bearingDegrees += 45) {
            saveTheaterAt("Bearing " + bearingDegrees, 49, bearingDegrees);
        }

        assertThat(searchNear(REMOTE_LAT, REMOTE_LNG, 50).items()).hasSize(8);
    }

    @Test
    void resultsAreSortedByDistanceWithOneDecimal() {
        saveTheaterAt("Far", 3.04, 0);
        saveTheaterAt("Near", 0.66, 120);
        saveTheaterAt("Middle", 1.5, 250);

        PagedResponse<NearbyTheaterDto> response = searchNear(REMOTE_LAT, REMOTE_LNG, 5);

        assertThat(names(response)).containsExactly("Near", "Middle", "Far");
        assertThat(response.items()).extracting(NearbyTheaterDto::distanceKm).containsExactly(0.7, 1.5, 3.0);
    }

    @Test
    void entityHookComputesH3CellOnInsertAndUpdate() {
        Theater theater = saveTheaterAt("Moving theater", 1, 0);
        assertThat(theater.getH3Cell()).isEqualTo(H3Cells.cellFor(theater.getLat(), theater.getLng()));

        double[] newPoint = GeoTestPoints.destination(REMOTE_LAT, REMOTE_LNG, 20, 90);
        theater.setLat(newPoint[0]);
        theater.setLng(newPoint[1]);
        Theater updated = theaterRepository.saveAndFlush(theater);

        assertThat(updated.getH3Cell()).isEqualTo(H3Cells.cellFor(newPoint[0], newPoint[1]));
    }

    @Test
    void pastShowtimesAreNeverReturned() {
        saveTheaterAt("Showtime theater", 1, 0);

        assertThat(searchNear(REMOTE_LAT, REMOTE_LNG, 5).items())
                .singleElement()
                .satisfies(theater -> assertThat(theater.showtimes())
                        .isNotEmpty()
                        .allSatisfy(showtime -> assertThat(showtime.startTime()).isAfterOrEqualTo(NOW)));
    }

    @Test
    void seededOntarioTheatersAreFoundNearUnionStation() {
        // Proves V16's h3_cell values agree with the search's: Scotiabank Theatre (259 Richmond
        // St W) is ~1 km from Union Station.
        assertThat(theaterRepository.count()).isGreaterThan(100);

        assertThat(names(searchNear(UNION_STATION_LAT, UNION_STATION_LNG, 2))).contains("Scotiabank Theatre Toronto");
    }
}
