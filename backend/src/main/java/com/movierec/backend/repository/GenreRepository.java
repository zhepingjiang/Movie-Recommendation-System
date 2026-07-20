package com.movierec.backend.repository;

import com.movierec.backend.entity.Genre;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    List<Genre> findByIsActiveTrue();
}
