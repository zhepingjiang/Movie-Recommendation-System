package com.movierec.backend.repository;

import com.movierec.backend.entity.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link UserEvent}.
 */
public interface UserEventRepository extends JpaRepository<UserEvent, Long> {
}
