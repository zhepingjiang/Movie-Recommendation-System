"""Offline SVD collaborative-filtering training job.

Combines our own explicit ratings (small: this app has under 30 users) with the MovieLens
ml-latest-small dataset so matrix factorization has enough signal to be meaningful, trains a
Surprise SVD model, and writes each real user's top candidates to Redis.

Not part of the request path -- triggered manually via recommendation/scripts/train_svd.py.
user_events (implicit signals) are intentionally not used yet; Surprise's SVD wants an explicit
1-5 rating, and view/click/watchlist counts don't have a natural value on that scale. Revisit with
a separate implicit-feedback pass later.
"""

import csv
import os
from collections import defaultdict

import redis
from surprise import SVD, Dataset, Reader

from db import get_cursor

ML_DATASET_DIR = os.environ.get("ML_DATASET_DIR", r"C:\Users\zhepi\Downloads\ml-latest-small\ml-latest-small")
ML_RATINGS_CSV = os.path.join(ML_DATASET_DIR, "ratings.csv")
ML_LINKS_CSV = os.path.join(ML_DATASET_DIR, "links.csv")

REDIS_HOST = os.environ.get("REDIS_HOST", "localhost")
REDIS_PORT = int(os.environ.get("REDIS_PORT", "6379"))

RATING_SCALE = (1, 5)
TOP_N = 10
REDIS_STORE_N = 6

_PG_PREFIX = "pg:"
_ML_PREFIX = "ml:"

_PG_RATINGS_SQL = "SELECT user_id, movie_id, score FROM ratings"
_MOVIE_TMDB_IDS_SQL = "SELECT id, tmdb_id FROM movies WHERE tmdb_id IS NOT NULL"
_ALL_MOVIE_IDS_SQL = "SELECT id FROM movies"


def pg_uid(user_id) -> str:
    """Namespaces a real postgres user id so it can never collide with a MovieLens raw id --
    both id spaces are small integers and would otherwise overlap (e.g. postgres user 5 and
    MovieLens's anonymous user 5 are unrelated people)."""
    return f"{_PG_PREFIX}{user_id}"


def ml_uid(user_id) -> str:
    return f"{_ML_PREFIX}{user_id}"


def round_ml_rating(raw: float) -> int:
    """MovieLens ratings are 0.5-5.0 in half-point steps; our scale is integer 1-5. Python's
    round() is already half-even (banker's rounding) for exact .5 values, which is what we want
    for 1.5/2.5/3.5/4.5. The one edge case is 0.5, which rounds down to 0 and falls outside the
    scale -- clamped up to 1."""
    return max(round(raw), RATING_SCALE[0])


def load_postgres_ratings() -> list[tuple[str, int, int]]:
    with get_cursor() as cursor:
        cursor.execute(_PG_RATINGS_SQL)
        rows = cursor.fetchall()
    return [(pg_uid(r["user_id"]), r["movie_id"], int(r["score"])) for r in rows]


def load_tmdb_to_movie_id() -> dict[str, int]:
    with get_cursor() as cursor:
        cursor.execute(_MOVIE_TMDB_IDS_SQL)
        rows = cursor.fetchall()
    return {str(r["tmdb_id"]): r["id"] for r in rows}


def load_all_movie_ids() -> list[int]:
    with get_cursor() as cursor:
        cursor.execute(_ALL_MOVIE_IDS_SQL)
        rows = cursor.fetchall()
    return [r["id"] for r in rows]


def load_ml_movie_to_tmdb() -> dict[str, str]:
    mapping = {}
    with open(ML_LINKS_CSV, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_id = row["tmdbId"].strip()
            if tmdb_id:
                mapping[row["movieId"]] = tmdb_id
    return mapping


def load_movielens_ratings(
    ml_movie_to_tmdb: dict[str, str], tmdb_to_movie_id: dict[str, int]
) -> tuple[list[tuple[str, int, int]], int]:
    """Returns (ratings, skipped_count). Rows are skipped when the MovieLens movie has no tmdbId,
    or that tmdbId doesn't resolve to a postgres movie (dead/merged TMDb ids -- see the backfill
    script's not_found count)."""
    ratings = []
    skipped = 0
    with open(ML_RATINGS_CSV, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_id = ml_movie_to_tmdb.get(row["movieId"])
            movie_id = tmdb_to_movie_id.get(tmdb_id) if tmdb_id else None
            if movie_id is None:
                skipped += 1
                continue
            score = round_ml_rating(float(row["rating"]))
            ratings.append((ml_uid(row["userId"]), movie_id, score))
    return ratings, skipped


def build_trainset(rows: list[tuple[str, int, int]]):
    import pandas as pd

    reader = Reader(rating_scale=RATING_SCALE)
    df = pd.DataFrame(rows, columns=["user", "item", "rating"])
    data = Dataset.load_from_df(df, reader)
    return data.build_full_trainset()


def train_model(trainset) -> SVD:
    algo = SVD()
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

    print("Loading MovieLens ratings...")
    ml_movie_to_tmdb = load_ml_movie_to_tmdb()
    tmdb_to_movie_id = load_tmdb_to_movie_id()
    ml_ratings, skipped = load_movielens_ratings(ml_movie_to_tmdb, tmdb_to_movie_id)
    ml_users = {u for u, _, _ in ml_ratings}
    print(f"  {len(ml_ratings)} ratings from {len(ml_users)} MovieLens users ({skipped} rows skipped, no matching postgres movie)")

    all_rows = pg_ratings + ml_ratings
    if not all_rows:
        print("No ratings available from either source -- nothing to train on.")
        return

    print(f"Training SVD on {len(all_rows)} total ratings...")
    trainset = build_trainset(all_rows)
    algo = train_model(trainset)

    if not pg_ratings:
        print("No real users have any ratings yet -- nothing to generate recommendations for.")
        return

    rated_by_user = rated_movies_by_pg_user(pg_ratings)
    print(f"Generating top-{TOP_N} candidates for {len(rated_by_user)} real users...")
    all_movie_ids = load_all_movie_ids()
    top_n = generate_top_n(algo, rated_by_user, all_movie_ids)

    written = write_recommendations_to_redis(top_n)
    print(f"Wrote recommendations for {written} users to Redis (top {REDIS_STORE_N} each, key format user:{{id}}:recommendations).")
