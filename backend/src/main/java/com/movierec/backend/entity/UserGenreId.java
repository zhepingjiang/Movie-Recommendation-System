package com.movierec.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for {@link UserGenre}: a (user, genre) pair.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGenreId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "genre_id")
    private Long genreId;
}
