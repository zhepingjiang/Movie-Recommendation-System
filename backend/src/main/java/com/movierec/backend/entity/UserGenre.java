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
 * JPA entity mapping to the {@code user_genres} table: a genre a user picked at onboarding
 * (or later edited), with an affinity {@code weight}. The cold-start recommendation signal
 * before a user has enough interaction history for collaborative filtering.
 *
 * <p>Every row is currently written with {@code weight = 1.0} — the onboarding UI only supports
 * a flat select/deselect — but the column exists so a future intensity picker or behavior-driven
 * re-weighting doesn't require another migration.
 */
@Entity
@Table(name = "user_genres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGenre {

    @EmbeddedId
    private UserGenreId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("genreId")
    @JoinColumn(name = "genre_id")
    private Genre genre;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal weight;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
