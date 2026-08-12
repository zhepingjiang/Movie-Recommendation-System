package com.movierec.backend.repository;

import com.movierec.backend.entity.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Tag}.
 */
public interface TagRepository extends JpaRepository<Tag, Long> {

    /** Returns all tags flagged active. */
    List<Tag> findByIsActiveTrue();
}
