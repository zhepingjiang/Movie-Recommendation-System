package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.NearbyTheaterDto;
import com.movierec.backend.dto.PagedResponse;
import com.movierec.backend.dto.ShowtimeSlotDto;
import com.movierec.backend.entity.Theater;
import com.movierec.backend.exception.MovieNotFoundException;
import com.movierec.backend.geo.GeoTestPoints;
import com.movierec.backend.repository.MovieRepository;
import com.movierec.backend.repository.TheaterRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sorting, paging and filtering logic with the repository and showtime source mocked. The real
 * H3/SQL path is covered by {@code NearbyTheaterSearchIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class NearbyTheaterServiceTest {

    private static final double SEARCH_LAT = 43.6453;
    private static final double SEARCH_LNG = -79.3806;
    private static final long MOVIE_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-10-20T16:00:00Z");

    @Mock private TheaterRepository theaterRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private ShowtimeProvider showtimeProvider;

    private NearbyTheaterService service;

    @BeforeEach
    void setUp() {
        service = new NearbyTheaterService(
                theaterRepository, movieRepository, showtimeProvider, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Theater theaterAt(long theaterId, double distanceKm) {
        double[] point = GeoTestPoints.destination(SEARCH_LAT, SEARCH_LNG, distanceKm, 90);
        return Theater.builder().id(theaterId).name("Theater " + theaterId).lat(point[0]).lng(point[1]).build();
    }

    private static List<ShowtimeSlotDto> startingIn(Duration delay) {
        return List.of(new ShowtimeSlotDto(NOW.plus(delay), "STANDARD"));
    }

    private void givenTheaters(List<Theater> theaters, Map<Long, List<ShowtimeSlotDto>> showtimesByTheaterId) {
        when(movieRepository.existsById(MOVIE_ID)).thenReturn(true);
        when(theaterRepository.findByH3CellIn(anyCollection())).thenReturn(theaters);
        when(showtimeProvider.findUpcomingShowtimes(anyCollection(), eq(MOVIE_ID), eq(NOW)))
                .thenReturn(showtimesByTheaterId);
    }

    private static List<Long> theaterIds(PagedResponse<NearbyTheaterDto> response) {
        return response.items().stream().map(NearbyTheaterDto::id).toList();
    }

    @Test
    void sortsByDistanceAndRoundsToOneDecimal() {
        givenTheaters(
                List.of(theaterAt(1, 3.26), theaterAt(2, 0.84), theaterAt(3, 1.5)),
                Map.of(1L, startingIn(Duration.ofHours(1)), 2L, startingIn(Duration.ofHours(1)),
                        3L, startingIn(Duration.ofHours(1))));

        PagedResponse<NearbyTheaterDto> response =
                service.findNearbyTheaters(SEARCH_LAT, SEARCH_LNG, 5, MOVIE_ID, NearbyTheaterSort.DISTANCE, 0, 20);

        assertThat(theaterIds(response)).containsExactly(2L, 3L, 1L);
        assertThat(response.items()).extracting(NearbyTheaterDto::distanceKm).containsExactly(0.8, 1.5, 3.3);
    }

    @Test
    void sortsByNextShowtimeThenDistance() {
        givenTheaters(
                List.of(theaterAt(1, 1), theaterAt(2, 2), theaterAt(3, 3)),
                Map.of(1L, startingIn(Duration.ofHours(3)), 2L, startingIn(Duration.ofMinutes(30)),
                        3L, startingIn(Duration.ofMinutes(30))));

        PagedResponse<NearbyTheaterDto> response = service.findNearbyTheaters(
                SEARCH_LAT, SEARCH_LNG, 5, MOVIE_ID, NearbyTheaterSort.NEXT_SHOWTIME, 0, 20);

        assertThat(theaterIds(response)).containsExactly(2L, 3L, 1L);
    }

    @Test
    void dropsTheatersOutsideTheRadiusAndTheatersWithoutShowtimes() {
        givenTheaters(
                List.of(theaterAt(1, 1), theaterAt(2, 2), theaterAt(3, 5.2)),
                Map.of(1L, startingIn(Duration.ofHours(1)), 2L, List.of(),
                        3L, startingIn(Duration.ofHours(1))));

        PagedResponse<NearbyTheaterDto> response =
                service.findNearbyTheaters(SEARCH_LAT, SEARCH_LNG, 5, MOVIE_ID, NearbyTheaterSort.DISTANCE, 0, 20);

        assertThat(theaterIds(response)).containsExactly(1L);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void pagesAfterSorting() {
        givenTheaters(
                List.of(theaterAt(1, 1), theaterAt(2, 2), theaterAt(3, 3)),
                Map.of(1L, startingIn(Duration.ofHours(1)), 2L, startingIn(Duration.ofHours(1)),
                        3L, startingIn(Duration.ofHours(1))));

        PagedResponse<NearbyTheaterDto> secondPage =
                service.findNearbyTheaters(SEARCH_LAT, SEARCH_LNG, 5, MOVIE_ID, NearbyTheaterSort.DISTANCE, 1, 2);
        PagedResponse<NearbyTheaterDto> pastTheEnd =
                service.findNearbyTheaters(SEARCH_LAT, SEARCH_LNG, 5, MOVIE_ID, NearbyTheaterSort.DISTANCE, 5, 2);

        assertThat(theaterIds(secondPage)).containsExactly(3L);
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.totalPages()).isEqualTo(2);
        assertThat(pastTheEnd.items()).isEmpty();
    }

    @Test
    void pageWhoseOffsetOverflowsIntReturnsEmptyPage() {
        givenTheaters(List.of(theaterAt(1, 1)), Map.of(1L, startingIn(Duration.ofHours(1))));

        PagedResponse<NearbyTheaterDto> response = service.findNearbyTheaters(
                SEARCH_LAT, SEARCH_LNG, 5, MOVIE_ID, NearbyTheaterSort.DISTANCE, Integer.MAX_VALUE, 50);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void unknownMovieIsRejected() {
        when(movieRepository.existsById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service.findNearbyTheaters(
                        SEARCH_LAT, SEARCH_LNG, 5, 999L, NearbyTheaterSort.DISTANCE, 0, 20))
                .isInstanceOf(MovieNotFoundException.class);
    }
}
