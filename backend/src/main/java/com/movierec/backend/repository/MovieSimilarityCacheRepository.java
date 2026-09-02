package com.movierec.backend.repository;

import com.movierec.backend.entity.MovieSimilarityCache;
import com.movierec.backend.entity.MovieSimilarityCacheId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link MovieSimilarityCache}, keyed by the composite
 * {@link MovieSimilarityCacheId}.
 */
public interface MovieSimilarityCacheRepository extends JpaRepository<MovieSimilarityCache, MovieSimilarityCacheId> {

    /**
     * Returns a movie's cached similar movies for a specific model version, highest score first.
     * The property path ({@code Id.movieId} / {@code Id.modelVersion}) reaches into the embedded
     * {@link MovieSimilarityCacheId}.
     */
    List<MovieSimilarityCache> findByIdMovieIdAndIdModelVersionOrderByScoreDesc(
            Long movieId, String modelVersion, Pageable pageable);
}
