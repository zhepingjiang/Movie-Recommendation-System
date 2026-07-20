package com.movierec.backend.repository;

import com.movierec.backend.entity.Rating;
import com.movierec.backend.entity.RatingId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, RatingId> {
}
