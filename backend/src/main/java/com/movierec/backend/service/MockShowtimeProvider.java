package com.movierec.backend.service;

import com.movierec.backend.dto.ShowtimeSlotDto;
import com.movierec.backend.entity.Theater;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Placeholder showtimes until there is a real showtime data source. Nothing is stored: times are
 * generated per request, seeded by (theater, movie, date), so the same theater shows the same
 * times on every refresh and nothing in the database goes stale.
 */
@Component
public class MockShowtimeProvider implements ShowtimeProvider {

    /** Every theater in the seed data is in Ontario. */
    static final ZoneId THEATER_ZONE = ZoneId.of("America/Toronto");

    /** How many upcoming showtimes to return per theater at most. */
    static final int MAX_SHOWTIMES_PER_THEATER = 6;

    private static final List<LocalTime> CANDIDATE_START_TIMES = List.of(
            LocalTime.of(11, 0), LocalTime.of(12, 30), LocalTime.of(13, 10), LocalTime.of(14, 45),
            LocalTime.of(16, 0), LocalTime.of(17, 20), LocalTime.of(18, 45), LocalTime.of(19, 30),
            LocalTime.of(21, 0), LocalTime.of(22, 15));
    private static final List<String> FORMATS = List.of("STANDARD", "STANDARD", "STANDARD", "IMAX", "3D", "ULTRAAVX");
    private static final int MIN_SCREENINGS_PER_DAY = 3;
    private static final int MAX_SCREENINGS_PER_DAY = 5;

    @Override
    public Map<Long, List<ShowtimeSlotDto>> findUpcomingShowtimes(
            Collection<Theater> theaters, long movieId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, THEATER_ZONE);
        Map<Long, List<ShowtimeSlotDto>> showtimesByTheaterId = new HashMap<>();
        for (Theater theater : theaters) {
            // Today and tomorrow, so a late-evening search still has something to show.
            List<ShowtimeSlotDto> upcomingShowtimes = new ArrayList<>();
            for (LocalDate screeningDate : List.of(today, today.plusDays(1))) {
                for (ShowtimeSlotDto showtime : screeningsOn(theater.getId(), movieId, screeningDate)) {
                    if (!showtime.startTime().isBefore(now)) {
                        upcomingShowtimes.add(showtime);
                    }
                }
            }
            showtimesByTheaterId.put(
                    theater.getId(),
                    List.copyOf(upcomingShowtimes.subList(0, Math.min(MAX_SHOWTIMES_PER_THEATER, upcomingShowtimes.size()))));
        }
        return showtimesByTheaterId;
    }

    /**
     * The mock schedule for one theater, movie and day, earliest first.
     *
     * @param theaterId theater the schedule is for
     * @param movieId movie the schedule is for
     * @param screeningDate local date in {@link #THEATER_ZONE}
     * @return that day's screenings, including any already in the past
     */
    static List<ShowtimeSlotDto> screeningsOn(long theaterId, long movieId, LocalDate screeningDate) {
        Random seededRandom = new Random(Objects.hash(theaterId, movieId, screeningDate));
        int screeningCount = MIN_SCREENINGS_PER_DAY + seededRandom.nextInt(MAX_SCREENINGS_PER_DAY - MIN_SCREENINGS_PER_DAY + 1);
        List<LocalTime> shuffledStartTimes = new ArrayList<>(CANDIDATE_START_TIMES);
        Collections.shuffle(shuffledStartTimes, seededRandom);
        return shuffledStartTimes.subList(0, screeningCount).stream()
                .sorted()
                .map(startTime -> new ShowtimeSlotDto(
                        screeningDate.atTime(startTime).atZone(THEATER_ZONE).toInstant(),
                        FORMATS.get(seededRandom.nextInt(FORMATS.size()))))
                .toList();
    }
}
