package com.movierec.backend.repository;

import com.movierec.backend.entity.UserInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {
}
