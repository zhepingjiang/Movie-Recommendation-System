package com.movierec.backend.service;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Movie;
import com.movierec.backend.event.MovieViewEventProducer;
import com.movierec.backend.repository.MovieRepository;
import com.movierec.backend.repository.MovieSpecifications;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Business logic for browsing and searching movies, backing
 * {@link com.movierec.backend.controller.MovieController}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;
    private final MovieViewEventProducer movieViewEventProducer;

    /**
     * Searches movies by combining the optional title/genre/rating filters into a single
     * {@link Specification}, then fetches results in two passes to keep queries cheap:
     * <ol>
     *   <li>Run the filtered, paginated query against {@code movies} alone (no joins) to
     *       determine which rows belong on this page.</li>
     *   <li>Re-fetch just those ids with their {@code genres} collection eagerly joined, so the
     *       DTO mapping below doesn't trigger a lazy-load per movie (N+1 queries).</li>
     * </ol>
     * Movies missing from the second lookup (shouldn't normally happen) fall back to the
     * original entity, which still has genres accessible lazily within this read-only transaction.
     *
     * @param query optional case-insensitive title substring filter
     * @param genre optional exact genre name filter
     * @param minRating optional inclusive lower bound on average rating
     * @param pageable pagination/sorting parameters
     * @return a page of {@link MovieSummaryDto}
     */
    public Page<MovieSummaryDto> getMovies(String query, String genre, BigDecimal minRating, Pageable pageable) {
        Specification<Movie> spec = null;
        if (StringUtils.hasText(query)) {
            spec = MovieSpecifications.titleContains(query);
        }
        if (StringUtils.hasText(genre)) {
            Specification<Movie> genreSpec = MovieSpecifications.hasGenre(genre);
            spec = spec == null ? genreSpec : spec.and(genreSpec);
        }
        if (minRating != null) {
            Specification<Movie> ratingSpec = MovieSpecifications.minRating(minRating);
            spec = spec == null ? ratingSpec : spec.and(ratingSpec);
        }

        Page<Movie> page = spec == null ? movieRepository.findAll(pageable) : movieRepository.findAll(spec, pageable);
        List<Long> ids = page.getContent().stream().map(Movie::getId).toList();

        Map<Long, Movie> moviesWithGenres =
                ids.isEmpty()
                        ? Map.of()
                        : movieRepository.findAllByIdInWithGenres(ids).stream()
                                .collect(Collectors.toMap(Movie::getId, Function.identity()));

        return page.map(movie -> toSummaryDto(moviesWithGenres.getOrDefault(movie.getId(), movie)));
    }

    /**
     * Fetches a single movie by id, with its genres eagerly loaded. On a hit, publishes a
     * view event (to {@code movie-view-events}) for the recommendation pipeline.
     *
     * @param userId the viewing user's id, or null for an anonymous request
     * @return the movie as a {@link MovieSummaryDto}, or empty if no movie has that id
     */
    public Optional<MovieSummaryDto> getMovieById(Long id, Long userId) {
        Optional<MovieSummaryDto> result = movieRepository.findByIdWithGenres(id).map(this::toSummaryDto);
        if (result.isPresent()) {
            movieViewEventProducer.publishView(id, userId);
        }
        return result;
    }

    /**
     * Fetches movies by id, with genres eagerly loaded.
     *
     * @param ids the movie ids to fetch
     * @return the found movies keyed by id; ids with no matching movie are simply absent
     */
    public Map<Long, MovieSummaryDto> getMoviesByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return movieRepository.findAllByIdInWithGenres(ids).stream()
                .collect(Collectors.toMap(Movie::getId, this::toSummaryDto));
    }

    /** Flattens a {@link Movie} entity's genre associations into a {@link MovieSummaryDto}. */
    private MovieSummaryDto toSummaryDto(Movie movie) {
        List<String> genreNames =
                movie.getGenres().stream().map(Genre::getName).sorted().toList();

        return new MovieSummaryDto(
                movie.getId(),
                movie.getTitle(),
                movie.getPosterUrl(),
                movie.getDescription(),
                movie.getReleaseDate(),
                movie.getDurationMin(),
                movie.getAverageRating(),
                genreNames);
    }
}
