package com.movierec.backend.service;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Movie;
import com.movierec.backend.repository.MovieRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    public Page<MovieSummaryDto> getMovies(Pageable pageable) {
        Page<Movie> page = movieRepository.findAll(pageable);
        List<Long> ids = page.getContent().stream().map(Movie::getId).toList();

        Map<Long, Movie> moviesWithGenres =
                ids.isEmpty()
                        ? Map.of()
                        : movieRepository.findAllByIdInWithGenres(ids).stream()
                                .collect(Collectors.toMap(Movie::getId, Function.identity()));

        return page.map(movie -> toSummaryDto(moviesWithGenres.getOrDefault(movie.getId(), movie)));
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
