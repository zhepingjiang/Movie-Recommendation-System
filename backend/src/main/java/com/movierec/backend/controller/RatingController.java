package com.movierec.backend.controller;

import com.movierec.backend.dto.RateMovieRequest;
import com.movierec.backend.dto.RatingDto;
import com.movierec.backend.security.AuthenticatedUser;
import com.movierec.backend.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the authenticated user rate a movie 1-5. Requires authentication: only GETs under
 * {@code /api/movies/**} are public (see {@link com.movierec.backend.security.SecurityConfig}),
 * so this PUT falls through to the default {@code anyRequest().authenticated()} rule.
 */
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PutMapping("/{id}/rating")
    public RatingDto rateMovie(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RateMovieRequest request) {
        return ratingService.rateMovie(principal.getId(), id, request.score());
    }
}
