package com.movierec.backend.controller;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.PagedResponse;
import com.movierec.backend.service.MovieService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

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

    @GetMapping("/{id}")
    public ResponseEntity<MovieSummaryDto> getMovieById(@PathVariable Long id) {
        return movieService.getMovieById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
