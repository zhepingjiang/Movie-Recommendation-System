package com.movierec.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapping to the {@code movie_people} table: real-world cast/crew members
 * (actors, directors), sourced from the TMDb credits API and deduped by
 * {@code tmdb_person_id}. Distinct from {@link User}, which is an app account.
 */
@Entity
@Table(name = "movie_people")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoviePerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tmdb_person_id", nullable = false, unique = true)
    private Integer tmdbPersonId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
