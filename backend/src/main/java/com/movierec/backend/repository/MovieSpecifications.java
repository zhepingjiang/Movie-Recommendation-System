package com.movierec.backend.repository;

import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Movie;
import jakarta.persistence.criteria.Join;
import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;

public final class MovieSpecifications {

    private MovieSpecifications() {}

    public static Specification<Movie> titleContains(String query) {
        return (root, cq, cb) -> cb.like(cb.lower(root.get("title")), "%" + query.toLowerCase() + "%");
    }

    public static Specification<Movie> hasGenre(String genreName) {
        return (root, cq, cb) -> {
            cq.distinct(true);
            Join<Movie, Genre> genres = root.join("genres");
            return cb.equal(cb.lower(genres.get("name")), genreName.toLowerCase());
        };
    }

    public static Specification<Movie> minRating(BigDecimal minRating) {
        return (root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("averageRating"), minRating);
    }
}
