"""Runs the full offline recommendation pipeline in the one order that's actually valid: SVD
training, then content-based training, then recommendation blending -- blending reads whatever
svd_v1/content_v1 rows are currently in recommendation_cache, so it has to run last, against
output the first two steps just wrote.

This exists so the three jobs (each independently useful and independently runnable via their own
scripts/train_svd.py / scripts/train_content_based.py / scripts/blend_recommendations.py) can also
be triggered as a single unit -- the intended use is one Kubernetes CronJob entry point instead of
three jobs that would otherwise need their own ordering/dependency logic between pods.

No error handling here on purpose: if any stage raises, the pipeline stops immediately rather than
continuing on to blend stale or partial data -- a k8s CronJob run should show as Failed (non-zero
exit) in that case, not silently succeed having only done part of the work.
"""

from models import content_based_training, recommendation_blending, svd_training


def run() -> None:
    print("=== Stage 1/3: SVD training ===")
    svd_training.run()

    print("\n=== Stage 2/3: Content-based training ===")
    content_based_training.run()

    print("\n=== Stage 3/3: Recommendation blending ===")
    recommendation_blending.run()

    print("\nOffline pipeline complete.")
