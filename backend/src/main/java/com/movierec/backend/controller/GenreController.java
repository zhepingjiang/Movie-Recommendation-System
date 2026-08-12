package com.movierec.backend.controller;

import com.movierec.backend.service.GenreService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the list of active movie genres.
 */
@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    /**
     * Returns the names of all active genres, sorted alphabetically.
     *
     * @return list of active genre names
     */
    @GetMapping
    public List<String> getGenres() {
        return genreService.getActiveGenreNames();
    }
}
