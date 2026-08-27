"""Offline SVD collaborative-filtering training job.

Combines our own explicit ratings (small: this app has under 30 users) with the MovieLens
ml-latest-small dataset so matrix factorization has enough signal to be meaningful, trains a
Surprise SVD model, and writes each real user's top candidates to Redis.

The MovieLens side is read pre-joined from MinIO (see models/movielens_ingest.py) rather than
from the raw CSVs directly -- that's a one-time ingest job, run separately, so this script never
touches the filesystem and can run on any machine/container that can reach postgres/minio/redis.

Not part of the request path -- triggered manually via recommendation/scripts/train_svd.py.
user_events (implicit signals) are intentionally not used yet; Surprise's SVD wants an explicit
1-5 rating, and view/click/watchlist counts don't have a natural value on that scale. Revisit with
a separate implicit-feedback pass later.
"""

import os
from collections import defaultdict

import redis
from surprise import SVD, Dataset, Reader
from surprise.model_selection import GridSearchCV, cross_validate

from db import get_cursor
from models.movielens_ingest import RATING_SCALE, fetch_ratings_dataframe

REDIS_HOST = os.environ.get("REDIS_HOST", "localhost")
REDIS_PORT = int(os.environ.get("REDIS_PORT", "6379"))

TOP_N = 10
REDIS_STORE_N = 6
CV_FOLDS = 5
# Small grid on purpose -- this is a manually-triggered offline job (not on the request path),
# so we keep grid search (8 combos x CV_FOLDS fits) fast rather than exhaustive.
SVD_PARAM_GRID = {
    "n_factors": [50, 100],
    "n_epochs": [20, 30],
    "reg_all": [0.02, 0.1],
}

_PG_PREFIX = "pg:"
_ML_PREFIX = "ml:"

_PG_RATINGS_SQL = "SELECT user_id, movie_id, score FROM ratings"
_ALL_MOVIE_IDS_SQL = "SELECT id FROM movies"


def pg_uid(user_id) -> str:
    """Namespaces a real postgres user id so it can never collide with a MovieLens raw id --
    both id spaces are small integers and would otherwise overlap (e.g. postgres user 5 and
    MovieLens's anonymous user 5 are unrelated people)."""
    return f"{_PG_PREFIX}{user_id}"


def ml_uid(user_id) -> str:
    return f"{_ML_PREFIX}{user_id}"


def load_postgres_ratings() -> list[tuple[str, int, int]]:
    with get_cursor() as cursor:
        cursor.execute(_PG_RATINGS_SQL)
        rows = cursor.fetchall()
    return [(pg_uid(r["user_id"]), r["movie_id"], int(r["score"])) for r in rows]


def load_all_movie_ids() -> list[int]:
    with get_cursor() as cursor:
        cursor.execute(_ALL_MOVIE_IDS_SQL)
        rows = cursor.fetchall()
    return [r["id"] for r in rows]


def load_movielens_ratings() -> list[tuple[str, int, int]]:
    """Reads the pre-joined (movieId -> postgres movie id already resolved, ratings already
    rounded to our 1-5 scale) dataset built by models/movielens_ingest.py."""
    df = fetch_ratings_dataframe()
    return [
        (ml_uid(int(row.ml_user_id)), int(row.movie_id), int(row.rating))
        for row in df.itertuples(index=False)
    ]


def build_dataset(rows: list[tuple[str, int, int]]) -> Dataset:
    import pandas as pd

    reader = Reader(rating_scale=RATING_SCALE)
    df = pd.DataFrame(rows, columns=["user", "item", "rating"])
    return Dataset.load_from_df(df, reader)


def evaluate_model(data: Dataset, params: dict | None = None, cv: int = CV_FOLDS) -> dict[str, float]:
    """K-fold CV estimate of generalization error for the given SVD hyperparameters (Surprise's
    defaults if none given). Observability only -- doesn't gate training or Redis writes."""
    results = cross_validate(SVD(**(params or {})), data, measures=["RMSE", "MAE"], cv=cv, verbose=False)
    return {
        "rmse": float(results["test_rmse"].mean()),
        "mae": float(results["test_mae"].mean()),
    }


# --- Hyperparameter tuning ---
# Same k-fold CV as evaluate_model, but instead of just reporting a metric, grid search picks the
# n_factors/n_epochs/reg_all combo that *minimizes* CV RMSE across SVD_PARAM_GRID. This is the
# step that acts on what CV reveals about overfitting (e.g. preferring stronger reg_all when it
# generalizes better) rather than just measuring it.
def tune_hyperparameters(data: Dataset, cv: int = CV_FOLDS) -> tuple[dict, dict[str, float]]:
    grid_search = GridSearchCV(SVD, SVD_PARAM_GRID, measures=["rmse", "mae"], cv=cv)
    grid_search.fit(data)
    best_params = grid_search.best_params["rmse"]
    best_score = {"rmse": float(grid_search.best_score["rmse"]), "mae": float(grid_search.best_score["mae"])}
    return best_params, best_score
# --- end hyperparameter tuning ---


def train_model(trainset, params: dict) -> SVD:
    algo = SVD(**params)
    algo.fit(trainset)
    return algo


def rated_movies_by_pg_user(pg_ratings: list[tuple[str, int, int]]) -> dict[str, set[int]]:
    rated = defaultdict(set)
    for uid, movie_id, _ in pg_ratings:
        rated[uid].add(movie_id)
    return rated


def generate_top_n(
    algo: SVD, rated_by_user: dict[str, set[int]], all_movie_ids: list[int], n: int = TOP_N
) -> dict[str, list[tuple[int, float]]]:
    """Only called for pg:-namespaced users (real accounts with at least one rating) -- MovieLens's
    synthetic users contribute to training but never get anything generated for them."""
    results = {}
    for uid, rated in rated_by_user.items():
        candidates = [m for m in all_movie_ids if m not in rated]
        predictions = [(movie_id, algo.predict(uid, movie_id).est) for movie_id in candidates]
        predictions.sort(key=lambda p: p[1], reverse=True)
        results[uid] = predictions[:n]
    return results


def write_recommendations_to_redis(
    top_n_by_user: dict[str, list[tuple[int, float]]], store_n: int = REDIS_STORE_N
) -> int:
    client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
    written = 0
    for uid, predictions in top_n_by_user.items():
        real_user_id = uid[len(_PG_PREFIX):]
        key = f"user:{real_user_id}:recommendations"
        movie_ids = [str(movie_id) for movie_id, _ in predictions[:store_n]]
        client.delete(key)
        if movie_ids:
            client.rpush(key, *movie_ids)
        written += 1
    return written


def run() -> None:
    print("Loading postgres ratings...")
    pg_ratings = load_postgres_ratings()
    pg_users = {u for u, _, _ in pg_ratings}
    print(f"  {len(pg_ratings)} ratings from {len(pg_users)} real users")

    print("Loading MovieLens ratings from MinIO...")
    ml_ratings = load_movielens_ratings()
    ml_users = {u for u, _, _ in ml_ratings}
    print(f"  {len(ml_ratings)} ratings from {len(ml_users)} MovieLens users")

    all_rows = pg_ratings + ml_ratings
    if not all_rows:
        print("No ratings available from either source -- nothing to train on.")
        return

    data = build_dataset(all_rows)

    print(f"Evaluating default SVD hyperparameters with {CV_FOLDS}-fold cross-validation...")
    baseline_metrics = evaluate_model(data)
    print(f"  Baseline CV RMSE: {baseline_metrics['rmse']:.4f}, MAE: {baseline_metrics['mae']:.4f}")

    # --- Hyperparameter tuning: grid search over SVD_PARAM_GRID, picks whatever minimizes CV RMSE ---
    print(f"Tuning SVD hyperparameters via grid search ({CV_FOLDS}-fold CV)...")
    best_params, tuned_metrics = tune_hyperparameters(data)
    print(f"  Best params: {best_params}")
    print(f"  Tuned CV RMSE: {tuned_metrics['rmse']:.4f}, MAE: {tuned_metrics['mae']:.4f}")
    # --- end hyperparameter tuning ---

    print(f"Training SVD on {len(all_rows)} total ratings with tuned hyperparameters...")
    algo = train_model(data.build_full_trainset(), best_params)

    if not pg_ratings:
        print("No real users have any ratings yet -- nothing to generate recommendations for.")
        return

    rated_by_user = rated_movies_by_pg_user(pg_ratings)
    print(f"Generating top-{TOP_N} candidates for {len(rated_by_user)} real users...")
    all_movie_ids = load_all_movie_ids()
    top_n = generate_top_n(algo, rated_by_user, all_movie_ids)

    written = write_recommendations_to_redis(top_n)
    print(f"Wrote recommendations for {written} users to Redis (top {REDIS_STORE_N} each, key format user:{{id}}:recommendations).")
