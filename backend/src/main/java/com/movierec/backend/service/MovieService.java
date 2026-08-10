package com.movierec.backend.service;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Movie;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

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

    public Optional<MovieSummaryDto> getMovieById(Long id) {
        return movieRepository.findByIdWithGenres(id).map(this::toSummaryDto);
    }

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
