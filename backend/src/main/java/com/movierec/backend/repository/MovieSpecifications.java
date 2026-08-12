package com.movierec.backend.repository;

import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Movie;
import jakarta.persistence.criteria.Join;
import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;

/**
 * Factory of {@link Specification} predicates for {@link Movie}, composed by
 * {@link com.movierec.backend.service.MovieService} to build dynamic search filters
 * (title, genre, minimum rating) that can be combined with {@code and()}.
 */
public final class MovieSpecifications {

    private MovieSpecifications() {}

    /** Case-insensitive substring match on the movie title. */
    public static Specification<Movie> titleContains(String query) {
        return (root, cq, cb) -> cb.like(cb.lower(root.get("title")), "%" + query.toLowerCase() + "%");
    }

    /**
     * Matches movies associated with the given genre name (case-insensitive).
     * Marks the query distinct since joining the many-to-many {@code genres}
     * collection can otherwise duplicate movie rows.
     */
    public static Specification<Movie> hasGenre(String genreName) {
        return (root, cq, cb) -> {
            cq.distinct(true);
            Join<Movie, Genre> genres = root.join("genres");
            return cb.equal(cb.lower(genres.get("name")), genreName.toLowerCase());
        };
    }

    /** Matches movies with an average rating greater than or equal to the given value. */
    public static Specification<Movie> minRating(BigDecimal minRating) {
        return (root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("averageRating"), minRating);
    }
}
