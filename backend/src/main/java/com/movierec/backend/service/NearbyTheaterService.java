package com.movierec.backend.service;

import com.movierec.backend.dto.NearbyTheaterDto;
import com.movierec.backend.dto.PagedResponse;
import com.movierec.backend.dto.ShowtimeSlotDto;
import com.movierec.backend.entity.Theater;
import com.movierec.backend.exception.MovieNotFoundException;
import com.movierec.backend.geo.GeoDistance;
import com.movierec.backend.geo.H3Cells;
import com.movierec.backend.repository.MovieRepository;
import com.movierec.backend.repository.TheaterRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Find nearby theaters": theaters within a radius of a point that have upcoming showtimes for a
 * movie.
 *
 * <p>Two-step filter: an indexed H3 cell lookup ({@link H3Cells#cellsWithinRadius}) narrows the
 * table to theaters that could be in range, then the exact haversine distance decides. Sorting
 * and paging happen in memory after that -- even a 50 km radius around downtown Toronto is a few
 * dozen theaters.
 */
@Service
@RequiredArgsConstructor
public class NearbyTheaterService {

    private final TheaterRepository theaterRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeProvider showtimeProvider;
    private final Clock clock;

    /**
     * Finds theaters near a point with upcoming showtimes for a movie.
     *
     * @param lat latitude of the search point in degrees, in [-90, 90]
     * @param lng longitude of the search point in degrees, in [-180, 180]
     * @param radiusKm search radius in kilometres, in (0, 50]
     * @param movieId movie whose showtimes to list; must exist
     * @param sort result ordering
     * @param page zero-based page index
     * @param size page size
     * @return the requested page of theaters, each with its distance and upcoming showtimes
     * @throws MovieNotFoundException if {@code movieId} does not exist
     */
    @Transactional(readOnly = true)
    public PagedResponse<NearbyTheaterDto> findNearbyTheaters(
            double lat, double lng, double radiusKm, long movieId, NearbyTheaterSort sort, int page, int size) {
        if (!movieRepository.existsById(movieId)) {
            throw new MovieNotFoundException(movieId);
        }

        List<TheaterWithDistance> theatersInRadius =
                theaterRepository.findByH3CellIn(H3Cells.cellsWithinRadius(lat, lng, radiusKm)).stream()
                        .map(theater -> new TheaterWithDistance(
                                theater, GeoDistance.haversineKm(lat, lng, theater.getLat(), theater.getLng())))
                        .filter(candidate -> candidate.distanceKm() <= radiusKm)
                        .toList();

        // One batched lookup for every theater in range (sorting by next showtime needs them all,
        // not just the page's).
        Instant now = clock.instant();
        Map<Long, List<ShowtimeSlotDto>> showtimesByTheaterId = showtimeProvider.findUpcomingShowtimes(
                theatersInRadius.stream().map(TheaterWithDistance::theater).toList(), movieId, now);

        List<NearbyTheaterDto> matchingTheaters = theatersInRadius.stream()
                .filter(candidate -> !showtimesByTheaterId.getOrDefault(candidate.theater().getId(), List.of()).isEmpty())
                .sorted(comparatorFor(sort, showtimesByTheaterId))
                .map(candidate -> toDto(candidate, showtimesByTheaterId.get(candidate.theater().getId())))
                .toList();

        // FIX: page * size was int multiplication, so a large page overflowed to a negative index and
        // subList threw; compute the offset as a long.
        int fromIndex = (int) Math.min((long) page * size, matchingTheaters.size());
        int toIndex = Math.min(fromIndex + size, matchingTheaters.size());
        int totalPages = (matchingTheaters.size() + size - 1) / size;
        return new PagedResponse<>(
                matchingTheaters.subList(fromIndex, toIndex), page, size, matchingTheaters.size(), totalPages);
    }

    private static Comparator<TheaterWithDistance> comparatorFor(
            NearbyTheaterSort sort, Map<Long, List<ShowtimeSlotDto>> showtimesByTheaterId) {
        Comparator<TheaterWithDistance> byDistance = Comparator.comparingDouble(TheaterWithDistance::distanceKm);
        return switch (sort) {
            case DISTANCE -> byDistance;
            // Intentionally only the first showtime: ShowtimeProvider returns each theater's upcoming
            // showtimes earliest first (past ones excluded), so getFirst() is that theater's next
            // showtime. Never empty here -- theaters without showtimes are filtered out before sorting.
            case NEXT_SHOWTIME -> Comparator.<TheaterWithDistance, Instant>comparing(
                            candidate -> showtimesByTheaterId.get(candidate.theater().getId()).getFirst().startTime())
                    .thenComparing(byDistance);
        };
    }

    private static NearbyTheaterDto toDto(TheaterWithDistance candidate, List<ShowtimeSlotDto> showtimes) {
        Theater theater = candidate.theater();
        return new NearbyTheaterDto(
                theater.getId(),
                theater.getName(),
                theater.getBrand(),
                theater.getAddress(),
                theater.getCity(),
                theater.getLat(),
                theater.getLng(),
                Math.round(candidate.distanceKm() * 10) / 10.0,
                showtimes);
    }

    private record TheaterWithDistance(Theater theater, double distanceKm) {}
}
