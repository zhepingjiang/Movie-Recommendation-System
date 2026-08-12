package com.movierec.backend.repository;

import com.movierec.backend.entity.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Theater}.
 */
public interface TheaterRepository extends JpaRepository<Theater, Long> {
}
