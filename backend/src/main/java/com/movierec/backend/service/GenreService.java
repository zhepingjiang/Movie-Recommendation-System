package com.movierec.backend.service;

import com.movierec.backend.entity.Genre;
import com.movierec.backend.repository.GenreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenreService {

    private final GenreRepository genreRepository;

    public List<String> getActiveGenreNames() {
        return genreRepository.findByIsActiveTrue().stream().map(Genre::getName).sorted().toList();
    }
}
