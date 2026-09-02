"""Offline content-based similarity job -- two outputs from one feature space.

Builds one feature vector per movie from its own metadata -- TF-IDF over `description`, plus a
weighted multi-hot over `genres` -- and computes each movie's top-K nearest neighbors by cosine
similarity. That item-item result is used two ways:

1. Persisted as-is to movie_similarity_cache, keyed by (movie_id, similar_movie_id, model_version)
   -- powers the "similar to this movie" feature on a movie's own page, independent of any user.
   The backend reads it directly (see ContentBasedRecommendationService) -- no request-time
   computation, no separate serving cache.
2. Aggregated per real user (their liked movies' neighbor lists, averaged) into a per-user
   candidate list, persisted to recommendation_cache under model_version=MODEL_VERSION -- the
   exact same table/shape svd_training.py writes 'svd_v1' rows to. This is what lets
   PersonalizedRecommendationService serve a pure-content personalized list with zero new backend
   code (just point it at a different model_version), and it's what the future hybrid blending job
   reads alongside 'svd_v1' -- no new backend stack needed for either.

Unlike SVD, item-item similarity needs no rating data at all -- a movie gets neighbors the moment
it has a description/genres, independent of whether anyone has ever rated it. That's what makes
it usable where SVD structurally can't be: brand-new movies (no rating history for matrix
factorization to have learned anything from). The per-user aggregation *does* need ratings, same
as SVD -- but unlike SVD it needs only a handful (enough to know what a user likes), not enough
for a matrix factorization to have converged, which is what makes it useful for a user with few
ratings the same way item-item similarity is useful for a movie with few ratings.

Not part of the request path -- triggered manually via recommendation/scripts/train_content_based.py.
"""

import io
import os
import pickle
from collections import defaultdict
from datetime import datetime, timezone

import pandas as pd
from psycopg2.extras import execute_values
from scipy.sparse import hstack
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import MultiLabelBinarizer

from db import get_cursor
from minio_client import ensure_bucket, get_minio_client

MINIO_CONTENT_BUCKET = os.environ.get("MINIO_CONTENT_BUCKET", "content-artifacts")

MODEL_VERSION = "content_v1"

# How many neighbors to compute/persist per movie -- mirrors svd_training.PERSIST_N's role as the
# ceiling on how many "similar movies" a single model_version can ever serve.
PERSIST_N = 20

# Genres are a clean, curated signal; `description` is noisy free-text prose of wildly varying
# length and quality. Boosting genres' contribution to the combined vector keeps two movies that
# share every genre from being out-cosine-similarity'd by two movies that merely share a few
# overview words in common (e.g. "his", "family", "life").
GENRE_WEIGHT = 2.0

TFIDF_MAX_FEATURES = 5000

# How many per-user candidates to compute/persist -- matches svd_training.PERSIST_N so a future
# hybrid blend has comparable candidate depth from both models to draw from.
USER_PERSIST_N = 50

# A movie only seeds a user's aggregated recommendations if they rated it this highly or above
# (1-5 scale) -- aggregating from every rated movie, including ones the user disliked, would
# recommend more of what they already said they don't want. Revisit with signed weighting
# (rating - midpoint, so a disliked movie actively suppresses its neighbors) once there's a way to
# validate that against held-out data rather than just asserting it seems reasonable.
LIKED_RATING_THRESHOLD = 4

_MOVIES_SQL = "SELECT id, description FROM movies"
_MOVIE_GENRES_SQL = """
    SELECT mg.movie_id AS movie_id, g.name AS name
    FROM movie_genres mg
    JOIN genres g ON g.id = mg.genre_id
"""
_RATINGS_SQL = "SELECT user_id, movie_id, score FROM ratings"
_DELETE_MOVIE_SIMILARITY_CACHE_SQL = "DELETE FROM movie_similarity_cache WHERE model_version = %s"
_INSERT_MOVIE_SIMILARITY_CACHE_SQL = """
    INSERT INTO movie_similarity_cache (movie_id, similar_movie_id, model_version, score, generated_at)
    VALUES %s
"""
_DELETE_RECOMMENDATION_CACHE_SQL = "DELETE FROM recommendation_cache WHERE model_version = %s"
_INSERT_RECOMMENDATION_CACHE_SQL = """
    INSERT INTO recommendation_cache (user_id, movie_id, model_version, score, generated_at)
    VALUES %s
"""


def load_movies() -> list[tuple[int, str]]:
    """Returns (movie_id, description) pairs, in a stable id order that every other step in this
    module reuses as its row order. A null description becomes "" -- TF-IDF handles an empty
    document fine (all-zero row), it just contributes nothing beyond the movie's genres."""
    with get_cursor() as cursor:
        cursor.execute(_MOVIES_SQL)
        rows = cursor.fetchall()
    return [(r["id"], r["description"] or "") for r in sorted(rows, key=lambda r: r["id"])]


def load_movie_genres() -> dict[int, list[str]]:
    with get_cursor() as cursor:
        cursor.execute(_MOVIE_GENRES_SQL)
        rows = cursor.fetchall()
    genres_by_movie = defaultdict(list)
    for r in rows:
        genres_by_movie[r["movie_id"]].append(r["name"])
    return genres_by_movie


def build_feature_matrix(movies: list[tuple[int, str]], genres_by_movie: dict[int, list[str]]):
    """Builds the combined [TF-IDF | weighted genre one-hot] sparse feature matrix, row-aligned to
    `movies`. Returns (matrix, fitted vectorizer, fitted binarizer) -- the fitted transformers are
    archived to MinIO so a future run (or an ad-hoc similarity lookup) can reproduce the same
    feature space without retraining.
    """
    movie_ids = [m[0] for m in movies]
    descriptions = [m[1] for m in movies]

    vectorizer = TfidfVectorizer(stop_words="english", max_features=TFIDF_MAX_FEATURES)
    description_matrix = vectorizer.fit_transform(descriptions)

    binarizer = MultiLabelBinarizer()
    genre_matrix = binarizer.fit_transform([genres_by_movie.get(mid, []) for mid in movie_ids])

    combined = hstack([description_matrix, genre_matrix * GENRE_WEIGHT]).tocsr()
    return combined, vectorizer, binarizer


def compute_top_k_neighbors(
    feature_matrix, movie_ids: list[int], k: int = PERSIST_N
) -> dict[int, list[tuple[int, float]]]:
    """For each movie, its k nearest neighbors by cosine similarity, excluding itself. Fetches
    k+1 neighbors per row (a movie is always its own closest match, distance 0) and filters that
    one out by id rather than by position, since ties at distance 0 aren't guaranteed to put the
    movie itself first.
    """
    n_neighbors = min(k + 1, feature_matrix.shape[0])
    model = NearestNeighbors(metric="cosine", algorithm="brute", n_neighbors=n_neighbors)
    model.fit(feature_matrix)
    distances, indices = model.kneighbors(feature_matrix)

    results = {}
    for row, movie_id in enumerate(movie_ids):
        neighbors = [
            (movie_ids[col], float(1 - dist))
            for col, dist in zip(indices[row], distances[row])
            if movie_ids[col] != movie_id
        ]
        results[movie_id] = neighbors[:k]
    return results


def save_model_to_minio(vectorizer: TfidfVectorizer, binarizer: MultiLabelBinarizer, run_id: str) -> None:
    """Archives the fitted vectorizer + genre binarizer under both a timestamped key
    (history/rollback) and a fixed "latest" key, matching svd_training.save_model_to_minio."""
    payload = pickle.dumps({"vectorizer": vectorizer, "binarizer": binarizer})
    client = get_minio_client()
    ensure_bucket(client, MINIO_CONTENT_BUCKET)
    for key in (f"models/{run_id}/model.pkl", "models/latest/model.pkl"):
        client.put_object(
            MINIO_CONTENT_BUCKET,
            key,
            data=io.BytesIO(payload),
            length=len(payload),
            content_type="application/octet-stream",
        )


def save_similarities_to_minio(top_k_by_movie: dict[int, list[tuple[int, float]]], run_id: str) -> None:
    """Archives this run's full per-movie neighbor list -- the historical record Postgres
    intentionally doesn't keep, since Postgres only holds the latest run per model_version."""
    rows = [
        (movie_id, similar_movie_id, score)
        for movie_id, neighbors in top_k_by_movie.items()
        for similar_movie_id, score in neighbors
    ]
    df = pd.DataFrame(rows, columns=["movie_id", "similar_movie_id", "score"])
    buffer = io.BytesIO()
    df.to_parquet(buffer, engine="pyarrow", index=False)
    size = buffer.tell()
    buffer.seek(0)

    client = get_minio_client()
    ensure_bucket(client, MINIO_CONTENT_BUCKET)
    client.put_object(
        MINIO_CONTENT_BUCKET,
        f"similarities/{run_id}/topk.parquet",
        data=buffer,
        length=size,
        content_type="application/octet-stream",
    )


def write_similarities_to_postgres(
    top_k_by_movie: dict[int, list[tuple[int, float]]], model_version: str, generated_at: datetime
) -> int:
    """Replaces every row for this model_version with the current run's top-K per movie -- same
    replace-not-upsert rationale as svd_training.write_recommendations_to_postgres: a neighbor
    that fell out of this run's top-K would otherwise linger forever from a previous run."""
    rows = [
        (movie_id, similar_movie_id, model_version, score, generated_at)
        for movie_id, neighbors in top_k_by_movie.items()
        for similar_movie_id, score in neighbors
    ]
    with get_cursor() as cursor:
        cursor.execute(_DELETE_MOVIE_SIMILARITY_CACHE_SQL, (model_version,))
        if rows:
            execute_values(cursor, _INSERT_MOVIE_SIMILARITY_CACHE_SQL, rows)
    return len(rows)


def load_ratings() -> list[tuple[int, int, int]]:
    """Real users' explicit ratings, as plain (user_id, movie_id, score) triples. Unlike
    svd_training.load_postgres_ratings, ids need no pg:/ml: namespacing here -- this module never
    mixes in MovieLens's synthetic ratings, since aggregation only needs to know what a specific
    real user liked, not extra training signal."""
    with get_cursor() as cursor:
        cursor.execute(_RATINGS_SQL)
        rows = cursor.fetchall()
    return [(r["user_id"], r["movie_id"], int(r["score"])) for r in rows]


def aggregate_user_scores(
    ratings: list[tuple[int, int, int]],
    top_k_by_movie: dict[int, list[tuple[int, float]]],
    n: int = USER_PERSIST_N,
) -> dict[int, list[tuple[int, float]]]:
    """For each user, scores every movie similar to something they rated >= LIKED_RATING_THRESHOLD
    by the mean cosine similarity across all of that user's liked movies it neighbors -- bounded to
    [0, 1], the same scale as the item-item scores it's built from. Movies the user already rated
    are excluded from their own candidate list. Users with no liked movies (no ratings, or every
    rating below the threshold) are simply absent from the result -- there's nothing to aggregate
    from, not a zero score.
    """
    rated_by_user = defaultdict(set)
    liked_by_user = defaultdict(set)
    for user_id, movie_id, score in ratings:
        rated_by_user[user_id].add(movie_id)
        if score >= LIKED_RATING_THRESHOLD:
            liked_by_user[user_id].add(movie_id)

    results = {}
    for user_id, liked_movies in liked_by_user.items():
        candidate_sims = defaultdict(list)
        for movie_id in liked_movies:
            for neighbor_id, sim in top_k_by_movie.get(movie_id, []):
                if neighbor_id not in rated_by_user[user_id]:
                    candidate_sims[neighbor_id].append(sim)

        scored = [(mid, sum(sims) / len(sims)) for mid, sims in candidate_sims.items()]
        scored.sort(key=lambda p: p[1], reverse=True)
        if scored:
            results[user_id] = scored[:n]
    return results


def write_user_scores_to_postgres(
    scores_by_user: dict[int, list[tuple[int, float]]], model_version: str, generated_at: datetime
) -> int:
    """Replaces every row for this model_version in recommendation_cache -- same
    replace-not-upsert rationale as write_similarities_to_postgres above and
    svd_training.write_recommendations_to_postgres: a candidate that fell out of this run's top-N
    would otherwise linger forever from a previous run."""
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
    print("Loading movies...")
    movies = load_movies()
    if not movies:
        print("No movies available -- nothing to train on.")
        return
    print(f"  {len(movies)} movies")

    print("Loading movie genres...")
    genres_by_movie = load_movie_genres()

    print("Building TF-IDF + genre feature matrix...")
    feature_matrix, vectorizer, binarizer = build_feature_matrix(movies, genres_by_movie)
    movie_ids = [m[0] for m in movies]

    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    print(f"Archiving vectorizer + binarizer to MinIO (run {run_id})...")
    save_model_to_minio(vectorizer, binarizer, run_id)

    print(f"Computing top-{PERSIST_N} nearest neighbors for {len(movie_ids)} movies...")
    top_k = compute_top_k_neighbors(feature_matrix, movie_ids, PERSIST_N)

    print(f"Archiving top-{PERSIST_N} similarity snapshot to MinIO...")
    save_similarities_to_minio(top_k, run_id)

    print(f"Persisting top-{PERSIST_N} similarities to Postgres (model_version={MODEL_VERSION})...")
    generated_at = datetime.now(timezone.utc)
    persisted = write_similarities_to_postgres(top_k, MODEL_VERSION, generated_at)
    print(f"  Persisted {persisted} rows.")

    print("Loading ratings to aggregate per-user content-based recommendations...")
    ratings = load_ratings()
    if not ratings:
        print("No ratings yet -- nothing to generate per-user recommendations for.")
        return

    user_scores = aggregate_user_scores(ratings, top_k)
    print(
        f"Persisting per-user content-based recommendations for {len(user_scores)} users "
        f"(model_version={MODEL_VERSION})..."
    )
    user_persisted = write_user_scores_to_postgres(user_scores, MODEL_VERSION, generated_at)
    print(f"  Persisted {user_persisted} rows.")
