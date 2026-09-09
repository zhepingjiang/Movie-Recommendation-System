package com.movierec.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for {@link MovieCast}: a (movie, person) pair.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieCastId implements Serializable {

    @Column(name = "movie_id")
    private Long movieId;

    @Column(name = "person_id")
    private Long personId;
}
