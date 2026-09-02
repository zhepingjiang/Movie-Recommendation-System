package com.movierec.backend.controller;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.PagedResponse;
import com.movierec.backend.dto.SimilarMovieDto;
import com.movierec.backend.security.AuthenticatedUser;
import com.movierec.backend.service.ContentBasedRecommendationService;
import com.movierec.backend.service.MovieService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for browsing and searching movies.
 */
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final ContentBasedRecommendationService contentBasedRecommendationService;

    /**
     * Lists movies with optional filtering and pagination.
     *
     * @param query optional case-insensitive substring match on the movie title
     * @param genre optional exact genre name filter
     * @param minRating optional lower bound (inclusive) on average rating
     * @param pageable pagination/sorting parameters; defaults to 20 items per page sorted by id
     * @return a page of matching movies wrapped in a {@link PagedResponse}
     */
    @GetMapping
    public PagedResponse<MovieSummaryDto> getMovies(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) BigDecimal minRating,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<MovieSummaryDto> page = movieService.getMovies(query, genre, minRating, pageable);
        return new PagedResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Fetches a single movie by its id. Publicly accessible; if the caller is authenticated,
     * the view is attributed to them, otherwise it's recorded as anonymous.
     *
     * @param id the movie id
     * @param principal the authenticated caller, or null if the request is anonymous
     * @return 200 with the movie if found, otherwise 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<MovieSummaryDto> getMovieById(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        Long userId = principal == null ? null : principal.getId();
        return movieService
                .getMovieById(id, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Content-based "more like this" for a single movie, computed offline from movie
     * metadata (description/genres) -- see {@link ContentBasedRecommendationService}.
     *
     * @param id the movie id
     * @param limit how many similar movies to return
     * @return the movie's cached similar-movies list, highest similarity first (may be shorter
     *     than {@code limit}, or empty, if the movie has no cached neighbors yet)
     */
    @GetMapping("/{id}/similar")
    public List<SimilarMovieDto> getSimilarMovies(
            @PathVariable Long id, @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return contentBasedRecommendationService.getSimilarMovies(id, limit);
    }
}
