package com.movierec.backend.service;

import com.movierec.backend.dto.ShowtimeSlotDto;
import com.movierec.backend.entity.Theater;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Source of showtimes for the nearby-theaters search. Batched by design: an implementation backed
 * by a real data source (the showtimes table, or an external feed) must answer for all theaters
 * in one query rather than one per theater.
 */
public interface ShowtimeProvider {

    /**
     * Looks up upcoming showtimes of one movie at several theaters.
     *
     * @param theaters the theaters to look up
     * @param movieId the movie being searched for
     * @param now the current time; showtimes starting before it are excluded
     * @return upcoming showtimes per theater id, earliest first; theaters with none may be absent
     */
    Map<Long, List<ShowtimeSlotDto>> findUpcomingShowtimes(Collection<Theater> theaters, long movieId, Instant now);
}
