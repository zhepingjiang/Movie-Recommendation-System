package com.movierec.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for {@link MovieSimilarityCache}: a (movie, similar movie, model
 * version) triple, allowing multiple model versions' similarity scores to be cached side by side.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieSimilarityCacheId implements Serializable {

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "similar_movie_id")
    private Long similarMovieId;

    @Column(name = "model_version", length = 30)
    private String modelVersion;
}
