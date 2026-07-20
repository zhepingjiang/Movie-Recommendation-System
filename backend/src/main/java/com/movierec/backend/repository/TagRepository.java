package com.movierec.backend.repository;

import com.movierec.backend.entity.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByIsActiveTrue();
}
