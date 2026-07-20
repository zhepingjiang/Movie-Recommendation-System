package com.movierec.backend.repository;

import com.movierec.backend.entity.RecommendationCache;
import com.movierec.backend.entity.RecommendationCacheId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationCacheRepository
        extends JpaRepository<RecommendationCache, RecommendationCacheId> {

    List<RecommendationCache> findByIdUserIdAndIdModelVersionOrderByScoreDesc(
            Long userId, String modelVersion, Pageable pageable);
}
