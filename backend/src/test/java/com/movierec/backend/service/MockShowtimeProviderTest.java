package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.movierec.backend.dto.ShowtimeSlotDto;
import com.movierec.backend.entity.Theater;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockShowtimeProviderTest {

    private static final long MOVIE_ID = 42L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private final MockShowtimeProvider provider = new MockShowtimeProvider();

    private static Theater theaterWithId(long theaterId) {
        return Theater.builder().id(theaterId).name("Theater " + theaterId).lat(43.6).lng(-79.4).build();
    }

    private static Instant torontoTime(int hour, int minute) {
        return ZonedDateTime.of(TODAY.atTime(hour, minute), MockShowtimeProvider.THEATER_ZONE).toInstant();
    }

    private List<ShowtimeSlotDto> upcomingAt(long theaterId, Instant now) {
        return provider.findUpcomingShowtimes(List.of(theaterWithId(theaterId)), MOVIE_ID, now).get(theaterId);
    }

    @Test
    void excludesShowtimesThatAlreadyStarted() {
        Instant now = torontoTime(17, 0);

        assertThat(upcomingAt(1L, now))
                .isNotEmpty()
                .allSatisfy(showtime -> assertThat(showtime.startTime()).isAfterOrEqualTo(now));
    }

    @Test
    void lateEveningSearchFallsThroughToTomorrow() {
        Instant now = torontoTime(23, 30);

        assertThat(upcomingAt(1L, now))
                .isNotEmpty()
                .allSatisfy(showtime -> assertThat(showtime.startTime()).isAfter(torontoTime(23, 59)));
    }

    @Test
    void sameInputsGiveTheSameScheduleOnEveryCall() {
        Instant now = torontoTime(9, 0);

        assertThat(upcomingAt(7L, now)).isEqualTo(upcomingAt(7L, now));
    }

    @Test
    void scheduleIsSortedAndCapped() {
        assertThat(upcomingAt(3L, torontoTime(0, 0)))
                .hasSizeLessThanOrEqualTo(MockShowtimeProvider.MAX_SHOWTIMES_PER_THEATER)
                .isSortedAccordingTo(Comparator.comparing(ShowtimeSlotDto::startTime));
    }

    @Test
    void dailyScheduleHasThreeToFiveScreenings() {
        assertThat(MockShowtimeProvider.screeningsOn(1L, MOVIE_ID, TODAY)).hasSizeBetween(3, 5);
    }
}
