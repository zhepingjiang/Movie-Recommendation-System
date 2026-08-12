package com.movierec.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for {@link RecommendationCache}: a (user, movie, model version)
 * triple, allowing multiple model versions' scores to be cached side by side.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCacheId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "model_version", length = 30)
    private String modelVersion;
}
