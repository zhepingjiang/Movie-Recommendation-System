-- Every offline job (content_based_training.py, svd_training.py, recommendation_blending.py)
-- does a full "DELETE FROM recommendation_cache WHERE model_version = %s" replace-not-upsert
-- write, and the nearline Flink job will do the same for model_version='nearline_v1'. The
-- existing idx_recommendation_cache_user_model_score index leads with user_id, so none of those
-- deletes can use it -- they fall back to a full sequential scan that gets slower as the table
-- grows. This index lets a bare "WHERE model_version = %s" use an index scan instead.
CREATE INDEX idx_recommendation_cache_model_version
    ON recommendation_cache (model_version);
