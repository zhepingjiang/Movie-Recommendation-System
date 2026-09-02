package com.movierec.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapping to the {@code movie_similarity_cache} table. Stores precomputed
 * content-based (item-item) similarity scores per (movie, similar movie, model version),
 * produced offline by the recommendation service (see models/content_based_training.py) and
 * read here to avoid recomputing on every request.
 */
@Entity
@Table(name = "movie_similarity_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieSimilarityCache {

    @EmbeddedId
    private MovieSimilarityCacheId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("movieId")
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("similarMovieId")
    @JoinColumn(name = "similar_movie_id")
    private Movie similarMovie;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal score;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
