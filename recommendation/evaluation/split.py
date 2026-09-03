"""Shared holdout split for comparing recommendation models on equal footing.

Both SVD's and content-based's evaluation read this exact split, never their own ad-hoc one --
a model can't look better just because it was scored against an easier holdout. For each real
user with at least MIN_RATINGS_TO_EVALUATE ratings, holds out their single most recent rating (by
`created_at`) as the thing each model has to rank highly against the full candidate catalog;
everything else they rated stays as training/aggregation signal for both models. Users with fewer
ratings than that are excluded from evaluation (there's nothing meaningful to hold out), but their
ratings still count as training signal -- excluding a user from *evaluation* isn't a reason to
throw away their *training* data, especially for SVD's collaborative signal.

Only ever looks at real Postgres users -- MovieLens's synthetic ratings are mixed into SVD's
training set elsewhere (see models.svd_training), never into this split, since content-based's
per-user aggregation is never computed for `ml:`-namespaced users either (see
models.content_based_training) and a fair comparison needs a population both models can serve.
"""

from collections import defaultdict

from db import get_cursor

MIN_RATINGS_TO_EVALUATE = 2

_RATINGS_WITH_TIMESTAMP_SQL = "SELECT user_id, movie_id, score, created_at FROM ratings ORDER BY created_at"


def load_ratings_with_timestamps() -> list[tuple[int, int, int, object]]:
    with get_cursor() as cursor:
        cursor.execute(_RATINGS_WITH_TIMESTAMP_SQL)
        rows = cursor.fetchall()
    return [(r["user_id"], r["movie_id"], int(r["score"]), r["created_at"]) for r in rows]


def build_holdout_split(
    ratings_with_timestamps: list[tuple[int, int, int, object]],
) -> tuple[list[tuple[int, int, int]], dict[int, tuple[int, int]], dict[int, set[int]]]:
    """Returns (training_ratings, holdout_by_user, rated_movies_by_user):
    - training_ratings: list[(user_id, movie_id, score)] -- every rating except each eligible
      user's held-out one, ready to feed into either model's training/aggregation step unchanged.
    - holdout_by_user: dict[user_id, (movie_id, score)] -- the single held-out rating per
      eligible user.
    - rated_movies_by_user: dict[user_id, set[movie_id]] -- an eligible user's *training* movie
      ids (excludes the held-out one), for building each user's candidate catalog.
    """
    by_user = defaultdict(list)
    for user_id, movie_id, score, created_at in ratings_with_timestamps:
        by_user[user_id].append((movie_id, score, created_at))

    training_ratings = []
    holdout_by_user = {}
    rated_movies_by_user = {}
    for user_id, user_ratings in by_user.items():
        if len(user_ratings) < MIN_RATINGS_TO_EVALUATE:
            for movie_id, score, _ in user_ratings:
                training_ratings.append((user_id, movie_id, score))
            continue

        user_ratings.sort(key=lambda r: r[2])  # oldest first
        *train, held_out = user_ratings
        holdout_by_user[user_id] = (held_out[0], held_out[1])
        rated_movies_by_user[user_id] = {movie_id for movie_id, _, _ in train}
        for movie_id, score, _ in train:
            training_ratings.append((user_id, movie_id, score))

    return training_ratings, holdout_by_user, rated_movies_by_user
