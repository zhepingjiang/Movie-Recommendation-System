package com.movierec.backend.repository;

import com.movierec.backend.entity.RecommendationCache;
import com.movierec.backend.entity.RecommendationCacheId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link RecommendationCache}, keyed by the composite
 * {@link RecommendationCacheId}.
 */
public interface RecommendationCacheRepository
        extends JpaRepository<RecommendationCache, RecommendationCacheId> {

    /**
     * Returns a user's cached recommendations for a specific model version,
     * highest score first. The property path ({@code Id.userId} / {@code Id.modelVersion})
     * reaches into the embedded {@link RecommendationCacheId}.
     */
    List<RecommendationCache> findByIdUserIdAndIdModelVersionOrderByScoreDesc(
            Long userId, String modelVersion, Pageable pageable);
}
