package com.movierec.backend.repository;

import com.movierec.backend.entity.Movie;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link Movie}. Extends {@link JpaSpecificationExecutor}
 * so callers can compose dynamic filters via {@link MovieSpecifications}.
 */
public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {

    /**
     * Loads the given movies with their {@code genres} collection eagerly fetched in one query,
     * avoiding N+1 lazy-loading when mapping a page of movies to {@link com.movierec.backend.dto.MovieSummaryDto}.
     * {@code DISTINCT} is required because the join fetch multiplies rows per genre.
     */
    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.genres WHERE m.id IN :ids")
    List<Movie> findAllByIdInWithGenres(@Param("ids") List<Long> ids);

    /** Loads a single movie with its {@code genres} collection eagerly fetched. */
    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.genres WHERE m.id = :id")
    Optional<Movie> findByIdWithGenres(@Param("id") Long id);
}
