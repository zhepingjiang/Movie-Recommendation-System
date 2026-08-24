package com.movierec.backend.repository;

import com.movierec.backend.entity.Genre;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Genre}.
 */
public interface GenreRepository extends JpaRepository<Genre, Long> {

    /** Returns all genres flagged active, i.e. eligible to be shown/filtered on. */
    List<Genre> findByIsActiveTrue();

    /** Returns the active genres among the given names; unknown/inactive names are dropped. */
    List<Genre> findByNameInAndIsActiveTrue(Collection<String> names);
}
