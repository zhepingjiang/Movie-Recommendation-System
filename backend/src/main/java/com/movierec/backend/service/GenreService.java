package com.movierec.backend.service;

import com.movierec.backend.entity.Genre;
import com.movierec.backend.repository.GenreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for genre lookups, backing {@link com.movierec.backend.controller.GenreController}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenreService {

    private final GenreRepository genreRepository;

    /** @return the names of all active genres, sorted alphabetically. */
    public List<String> getActiveGenreNames() {
        return genreRepository.findByIsActiveTrue().stream().map(Genre::getName).sorted().toList();
    }
}
