package com.movierec.backend.repository;

import com.movierec.backend.entity.UserGenre;
import com.movierec.backend.entity.UserGenreId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link UserGenre}.
 */
public interface UserGenreRepository extends JpaRepository<UserGenre, UserGenreId> {

    /** Returns the genre picks for the given user, in no particular order. */
    List<UserGenre> findByIdUserId(Long userId);
}
