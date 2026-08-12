package com.movierec.backend.repository;

import com.movierec.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link User}.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
