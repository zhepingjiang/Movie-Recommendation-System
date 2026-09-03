"""Offline recommendation-blending job -- combines SVD's and content-based's already-persisted
per-user candidate lists (model_version='svd_v1' and 'content_v1' in recommendation_cache) into a
single blended list, written back to the same table under model_version=MODEL_VERSION. Retrains
neither model and reads no rating data beyond aggregate counts -- Option 3's whole point is that
blending is a cheap read-and-combine step over two models that already ran on their own schedule,
not a third model in its own right. Run after both svd_training.py and content_based_training.py.

Weighting is confidence-based on two independent axes, not a single rating-count threshold:
- user_alpha: how much history does *this user* have -- a brand-new user's SVD score is nearly
  meaningless (no factorization signal to lean on), so weight toward content-based instead.
- item_confidence: how much history does *this candidate movie* have -- SVD's score for a
  barely-rated movie is close to the global mean regardless of who's asking, so weight toward
  content-based for that candidate specifically, independent of the requesting user's own tier.
effective_alpha is their product, not a hard cutoff on either axis -- both degrade smoothly rather
than one dimension vetoing the other outright (a movie at 4 ratings and one at 5 ratings shouldn't
get wildly different treatment).

N0/M0 are chosen by evaluation/evaluate_models.py's grid search against the shared holdout
(NDCG@10) -- not hand-picked the way GENRE_WEIGHT/LIKED_RATING_THRESHOLD were. Re-run that grid
search periodically as more real rating data accumulates; the defaults below reflect what it found
against today's (still very small) real dataset, and are noisy for exactly that reason.

Not part of the request path -- triggered manually via recommendation/scripts/blend_recommendations.py.

blend_all_users logs each user's user_alpha (before item_confidence) and mean effective_alpha
(after) at INFO -- this is the one place in the whole pipeline where the resulting weight split
between SVD and content-based is otherwise invisible, so it's worth surfacing in job logs even
though nothing else in this codebase uses `logging` (everywhere else just prints).
"""

import logging
from collections import defaultdict
from datetime import datetime, timezone

from psycopg2.extras import execute_values

from db import get_cursor

logger = logging.getLogger(__name__)

MODEL_VERSION = "blended_v1"
SVD_MODEL_VERSION = "svd_v1"
CONTENT_MODEL_VERSION = "content_v1"

# First-cut defaults -- see evaluation/evaluate_models.py's grid search for how these are chosen.
N0 = 10
M0 = 20

_CACHED_SCORES_SQL = "SELECT user_id, movie_id, score FROM recommendation_cache WHERE model_version = %s"
_USER_RATING_COUNTS_SQL = "SELECT user_id, count(*) AS cnt FROM ratings GROUP BY user_id"
_MOVIE_RATING_COUNTS_SQL = "SELECT movie_id, count(*) AS cnt FROM ratings GROUP BY movie_id"
_DELETE_RECOMMENDATION_CACHE_SQL = "DELETE FROM recommendation_cache WHERE model_version = %s"
_INSERT_RECOMMENDATION_CACHE_SQL = """
    INSERT INTO recommendation_cache (user_id, movie_id, model_version, score, generated_at)
    VALUES %s
"""


def min_max_normalize(scores: dict[int, float]) -> dict[int, float]:
    """Scales scores to [0, 1] within this one call's set -- used to bring SVD's ~1-5
    rating-space scores and content-based's ~0-1 cosine-similarity scores onto the same relative
    scale before blending. A tied input (including a single-candidate set) normalizes to 1.0
    across the board rather than dividing by zero -- there's no relative ordering to lose either
    way when everything is equal.
    """
    if not scores:
        return {}
    lo, hi = min(scores.values()), max(scores.values())
    if hi == lo:
        return {movie_id: 1.0 for movie_id in scores}
    return {movie_id: (score - lo) / (hi - lo) for movie_id, score in scores.items()}


def user_alpha(user_rating_count: int, n0: float) -> float:
    return min(1.0, user_rating_count / n0)


def item_confidence(movie_rating_count: int, m0: float) -> float:
    return min(1.0, movie_rating_count / m0)


def effective_alpha(user_rating_count: int, movie_rating_count: int, n0: float, m0: float) -> float:
    return user_alpha(user_rating_count, n0) * item_confidence(movie_rating_count, m0)


def blend_scores(
    svd_scores: dict[int, float],
    content_scores: dict[int, float],
    user_rating_count: int,
    movie_rating_counts: dict[int, int],
    n0: float,
    m0: float,
) -> dict[int, float]:
    """Blends normalized SVD/content scores over the union of both models' candidates, 0-filling
    whichever model has no opinion on a given movie -- same "caller fills the gap" contract as
    content_based_training.score_candidates. Each model is normalized over its own candidate set
    (not the union), since that's the only distribution either model actually produced.
    """
    norm_svd = min_max_normalize(svd_scores)
    norm_content = min_max_normalize(content_scores)
    candidates = set(svd_scores) | set(content_scores)

    blended = {}
    for movie_id in candidates:
        alpha = effective_alpha(user_rating_count, movie_rating_counts.get(movie_id, 0), n0, m0)
        blended[movie_id] = alpha * norm_svd.get(movie_id, 0.0) + (1 - alpha) * norm_content.get(movie_id, 0.0)
    return blended


def load_cached_scores(model_version: str) -> dict[int, dict[int, float]]:
    """Returns {user_id: {movie_id: score}} for the given model_version's current
    recommendation_cache rows."""
    with get_cursor() as cursor:
        cursor.execute(_CACHED_SCORES_SQL, (model_version,))
        rows = cursor.fetchall()
    scores_by_user = defaultdict(dict)
    for r in rows:
        scores_by_user[r["user_id"]][r["movie_id"]] = float(r["score"])
    return dict(scores_by_user)


def load_user_rating_counts() -> dict[int, int]:
    with get_cursor() as cursor:
        cursor.execute(_USER_RATING_COUNTS_SQL)
        rows = cursor.fetchall()
    return {r["user_id"]: r["cnt"] for r in rows}


def load_movie_rating_counts() -> dict[int, int]:
    with get_cursor() as cursor:
        cursor.execute(_MOVIE_RATING_COUNTS_SQL)
        rows = cursor.fetchall()
    return {r["movie_id"]: r["cnt"] for r in rows}


def blend_all_users(
    svd_scores_by_user: dict[int, dict[int, float]],
    content_scores_by_user: dict[int, dict[int, float]],
    user_rating_counts: dict[int, int],
    movie_rating_counts: dict[int, int],
    n0: float = N0,
    m0: float = M0,
) -> dict[int, list[tuple[int, float]]]:
    """Blends every user present in either model's cache -- a user missing from one side (e.g. a
    brand-new user with no SVD row yet) still gets a blended list, just leaning entirely on
    whichever model actually has something for them."""
    results = {}
    for user_id in set(svd_scores_by_user) | set(content_scores_by_user):
        rating_count = user_rating_counts.get(user_id, 0)
        svd_scores = svd_scores_by_user.get(user_id, {})
        content_scores = content_scores_by_user.get(user_id, {})

        alpha_from_user_history = user_alpha(rating_count, n0)
        candidates = set(svd_scores) | set(content_scores)
        mean_effective_alpha = (
            sum(effective_alpha(rating_count, movie_rating_counts.get(m, 0), n0, m0) for m in candidates)
            / len(candidates)
            if candidates
            else alpha_from_user_history
        )
        logger.info(
            "user %s: rating_count=%d -> svd_weight=%.2f before item_confidence, "
            "%.2f mean after (content_weight=%.2f)",
            user_id,
            rating_count,
            alpha_from_user_history,
            mean_effective_alpha,
            1 - mean_effective_alpha,
        )

        blended = blend_scores(svd_scores, content_scores, rating_count, movie_rating_counts, n0, m0)
        if blended:
            results[user_id] = sorted(blended.items(), key=lambda p: p[1], reverse=True)
    return results


def write_blended_scores_to_postgres(
    scores_by_user: dict[int, list[tuple[int, float]]], model_version: str, generated_at: datetime
) -> int:
    """Replaces every row for this model_version -- same replace-not-upsert rationale as the two
    training jobs this blends: a candidate that fell out of this run's blend would otherwise
    linger forever from a previous run."""
    rows = [
        (user_id, movie_id, model_version, score, generated_at)
        for user_id, predictions in scores_by_user.items()
        for movie_id, score in predictions
    ]
    with get_cursor() as cursor:
        cursor.execute(_DELETE_RECOMMENDATION_CACHE_SQL, (model_version,))
        if rows:
            execute_values(cursor, _INSERT_RECOMMENDATION_CACHE_SQL, rows)
    return len(rows)


def run() -> None:
    # No logging is configured elsewhere in this codebase (every other job just prints) --
    # basicConfig is a no-op if something else already configured the root logger, so this is
    # safe to call here regardless of whether run() is invoked directly or via offline_pipeline.py.
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    print("Loading cached SVD and content-based recommendations...")
    svd_scores_by_user = load_cached_scores(SVD_MODEL_VERSION)
    content_scores_by_user = load_cached_scores(CONTENT_MODEL_VERSION)
    if not svd_scores_by_user and not content_scores_by_user:
        print("Neither model has any cached recommendations yet -- nothing to blend.")
        return
    print(
        f"  {len(svd_scores_by_user)} users with SVD recommendations, "
        f"{len(content_scores_by_user)} with content-based."
    )

    print("Loading rating counts for confidence weighting...")
    user_rating_counts = load_user_rating_counts()
    movie_rating_counts = load_movie_rating_counts()

    print(f"Blending (N0={N0}, M0={M0})...")
    blended = blend_all_users(svd_scores_by_user, content_scores_by_user, user_rating_counts, movie_rating_counts)

    print(f"Persisting blended recommendations to Postgres (model_version={MODEL_VERSION})...")
    generated_at = datetime.now(timezone.utc)
    persisted = write_blended_scores_to_postgres(blended, MODEL_VERSION, generated_at)
    print(f"  Persisted {persisted} rows.")
