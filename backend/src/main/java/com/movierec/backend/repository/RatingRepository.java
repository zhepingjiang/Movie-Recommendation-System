package com.movierec.backend.repository;

import com.movierec.backend.entity.Rating;
import com.movierec.backend.entity.RatingId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Rating}, keyed by the composite {@link RatingId}.
 */
public interface RatingRepository extends JpaRepository<Rating, RatingId> {
}
