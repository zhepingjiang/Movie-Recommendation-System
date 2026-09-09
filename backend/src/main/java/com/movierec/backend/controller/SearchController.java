package com.movierec.backend.controller;

import com.movierec.backend.dto.MovieSummaryDto;
import com.movierec.backend.dto.PagedResponse;
import com.movierec.backend.service.MovieSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for full-text movie search, backed by Elasticsearch (see
 * {@link MovieSearchService}) rather than the substring filter {@link MovieController} uses.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final MovieSearchService movieSearchService;

    /**
     * Searches movies by title, director, cast, genres and overview, with fuzzy matching for
     * typos. Results are ranked by relevance score, highest first.
     *
     * @param q free-text search query
     * @param pageable pagination parameters; defaults to 20 items per page
     * @return a page of matching movies; empty (not 404/error) if nothing matches
     */
    @GetMapping
    public PagedResponse<MovieSummaryDto> search(
            @RequestParam String q, @PageableDefault(size = 20) Pageable pageable) {
        return movieSearchService.search(q, pageable.getPageNumber(), pageable.getPageSize());
    }
}
