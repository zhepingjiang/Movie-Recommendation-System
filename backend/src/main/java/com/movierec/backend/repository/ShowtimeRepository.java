package com.movierec.backend.repository;

import com.movierec.backend.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Showtime}.
 */
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
}
