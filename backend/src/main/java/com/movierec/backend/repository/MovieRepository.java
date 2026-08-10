package com.movierec.backend.repository;

import com.movierec.backend.entity.Movie;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.genres WHERE m.id IN :ids")
    List<Movie> findAllByIdInWithGenres(@Param("ids") List<Long> ids);
}
