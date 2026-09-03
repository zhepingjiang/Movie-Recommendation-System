"""Compares SVD and content-based on evaluation.split's shared holdout -- same users, same
candidate catalog, same relevance definition (RELEVANT_RATING_THRESHOLD) -- so the resulting
precision@k/recall@k/ndcg@k/coverage numbers are a fair head-to-head rather than each model's own
best case. Not part of the request path -- triggered manually via
recommendation/scripts/evaluate_models.py, same as the two training jobs it evaluates.

SVD is retrained here specifically on the leakage-free split (every real rating except each
evaluated user's held-out one, still mixed with MovieLens's ratings as usual) -- reusing the
production model trained on *all* ratings would let it see the very rating it's being asked to
predict, for every other user's held-out point it happened to also be trained on. Content-based's
item-item similarity needs no such retrain (it never used ratings to begin with); only the
per-user aggregation step is rebuilt from the leakage-free ratings.

Ranks the *full* candidate catalog per user for both models, not just each model's persisted
top-N cache -- the cache is a serving-time size limit, not a valid stand-in for "where did the
held-out item rank" when it might have ranked outside either model's top-N entirely.

Also grid-searches recommendation_blending's (N0, M0) confidence-curve constants against this same
holdout -- see evaluate_blend_grid's docstring for why that one *does* truncate to each model's
production top-N, unlike evaluate() above.
"""

from collections import defaultdict

from evaluation.metrics import coverage, mean_metric, ndcg_at_k, precision_at_k, recall_at_k
from evaluation.split import MIN_RATINGS_TO_EVALUATE, build_holdout_split, load_ratings_with_timestamps
from models import content_based_training, recommendation_blending, svd_training

# Ranking depth(s) to report at. Two values (a tight and a looser cutoff) rather than one, since a
# model can win at one depth and lose at another -- an unvalidated first-guess pair, same spirit
# as GENRE_WEIGHT/LIKED_RATING_THRESHOLD elsewhere.
EVAL_KS = [5, 10]

# A held-out rating only counts as something a model should have surfaced if the user actually
# liked it -- same threshold as content_based_training.LIKED_RATING_THRESHOLD, so "relevant" means
# the same thing everywhere in this system rather than two different definitions in two places.
RELEVANT_RATING_THRESHOLD = 4.0

# Grid searched for recommendation_blending.N0/M0. Small and coarse on purpose -- this is a
# manually-triggered offline comparison, not a hot path, and the real data volume today is too
# small to justify a finer grid.
N0_GRID = [3, 5, 10, 20]
M0_GRID = [5, 10, 20, 50]

# The blend grid picks one (N0, M0) pair, so it needs one scalar per grid point rather than a
# value per EVAL_KS depth -- NDCG@10 as the "final serving list" size.
BLEND_SELECTION_K = 10


def train_leakage_free_svd(training_ratings: list[tuple[int, int, int]]):
    """Trains SVD on the shared holdout's training_ratings (real users, leakage-free) mixed with
    MovieLens's ratings, using Surprise's default hyperparameters -- unlike svd_training.run(),
    this deliberately skips the grid search: retuning per evaluation run adds real cost for a
    number that's only used to rank models against each other, not to pick what gets served.
    """
    pg_rows = [(svd_training.pg_uid(user_id), movie_id, score) for user_id, movie_id, score in training_ratings]
    ml_rows = svd_training.load_movielens_ratings()
    data = svd_training.build_dataset(pg_rows + ml_rows)
    return svd_training.train_model(data.build_full_trainset(), {})


def score_svd_candidates(algo, user_id: int, candidates: list[int]) -> dict[int, float]:
    return {movie_id: algo.predict(svd_training.pg_uid(user_id), movie_id).est for movie_id in candidates}


def top_n_scores(scores: dict[int, float], n: int) -> dict[int, float]:
    return dict(sorted(scores.items(), key=lambda p: p[1], reverse=True)[:n])


def rank_svd(algo, user_id: int, candidates: list[int]) -> list[int]:
    scores = score_svd_candidates(algo, user_id, candidates)
    return sorted(scores, key=scores.get, reverse=True)


def rank_content(
    liked_movies: set[int],
    rated_movies: set[int],
    top_k_by_movie: dict[int, list[tuple[int, float]]],
    candidates: list[int],
) -> list[int]:
    """Scores every candidate, filling in 0.0 for anything score_candidates has no opinion on --
    see score_candidates' own docstring for why that's the caller's job, not its own."""
    sparse_scores = content_based_training.score_candidates(liked_movies, rated_movies, top_k_by_movie)
    scored = [(movie_id, sparse_scores.get(movie_id, 0.0)) for movie_id in candidates]
    scored.sort(key=lambda p: p[1], reverse=True)
    return [movie_id for movie_id, _ in scored]


def evaluate(
    algo,
    top_k_by_movie: dict[int, list[tuple[int, float]]],
    all_movie_ids: list[int],
    training_ratings: list[tuple[int, int, int]],
    holdout_by_user: dict[int, tuple[int, int]],
    rated_movies_by_user: dict[int, set[int]],
) -> dict[str, dict[int, dict[str, float | None]]]:
    liked_by_user = defaultdict(set)
    for user_id, movie_id, score in training_ratings:
        if score >= content_based_training.LIKED_RATING_THRESHOLD:
            liked_by_user[user_id].add(movie_id)

    svd_ranked_by_user, content_ranked_by_user = {}, {}
    svd_scores = {k: {"precision": [], "recall": [], "ndcg": []} for k in EVAL_KS}
    content_scores = {k: {"precision": [], "recall": [], "ndcg": []} for k in EVAL_KS}

    for user_id, (held_out_movie_id, held_out_score) in holdout_by_user.items():
        rated_movies = rated_movies_by_user[user_id]
        candidates = [m for m in all_movie_ids if m not in rated_movies]
        relevant = {held_out_movie_id} if held_out_score >= RELEVANT_RATING_THRESHOLD else set()

        svd_ranked = rank_svd(algo, user_id, candidates)
        content_ranked = rank_content(liked_by_user[user_id], rated_movies, top_k_by_movie, candidates)
        svd_ranked_by_user[user_id] = svd_ranked
        content_ranked_by_user[user_id] = content_ranked

        for k in EVAL_KS:
            svd_scores[k]["precision"].append(precision_at_k(svd_ranked, relevant, k))
            svd_scores[k]["recall"].append(recall_at_k(svd_ranked, relevant, k))
            svd_scores[k]["ndcg"].append(ndcg_at_k(svd_ranked, relevant, k))
            content_scores[k]["precision"].append(precision_at_k(content_ranked, relevant, k))
            content_scores[k]["recall"].append(recall_at_k(content_ranked, relevant, k))
            content_scores[k]["ndcg"].append(ndcg_at_k(content_ranked, relevant, k))

    catalog_size = len(all_movie_ids)
    return {
        "svd": {
            k: {
                "precision": mean_metric(svd_scores[k]["precision"]),
                "recall": mean_metric(svd_scores[k]["recall"]),
                "ndcg": mean_metric(svd_scores[k]["ndcg"]),
                "coverage": coverage(svd_ranked_by_user, catalog_size, k),
            }
            for k in EVAL_KS
        },
        "content": {
            k: {
                "precision": mean_metric(content_scores[k]["precision"]),
                "recall": mean_metric(content_scores[k]["recall"]),
                "ndcg": mean_metric(content_scores[k]["ndcg"]),
                "coverage": coverage(content_ranked_by_user, catalog_size, k),
            }
            for k in EVAL_KS
        },
    }


def evaluate_blend_grid(
    algo,
    top_k_by_movie: dict[int, list[tuple[int, float]]],
    all_movie_ids: list[int],
    training_ratings: list[tuple[int, int, int]],
    holdout_by_user: dict[int, tuple[int, int]],
    rated_movies_by_user: dict[int, set[int]],
) -> dict[tuple[float, float], float | None]:
    """Grid-searches recommendation_blending's (N0, M0) confidence weights, selecting by mean
    NDCG@BLEND_SELECTION_K on the same leakage-free holdout evaluate() uses. Both user and movie
    rating counts here come from training_ratings, never the live ratings table -- a movie's
    popularity count can't be allowed to include the very held-out rating being predicted, same
    leakage rule as everything else in this module.

    Unlike evaluate() above, this truncates each model's per-user scores to its own production
    top-N (svd_training.PERSIST_N / content_based_training.USER_PERSIST_N) before blending --
    that's what recommendation_blending.py actually has to work with in production, since it only
    ever reads each model's already-persisted top-N from recommendation_cache, never the full
    catalog. Skipping this truncation here would tune (N0, M0) against a version of the problem
    production doesn't actually face.
    """
    liked_by_user = defaultdict(set)
    user_rating_counts: dict[int, int] = defaultdict(int)
    movie_rating_counts: dict[int, int] = defaultdict(int)
    for user_id, movie_id, score in training_ratings:
        user_rating_counts[user_id] += 1
        movie_rating_counts[movie_id] += 1
        if score >= content_based_training.LIKED_RATING_THRESHOLD:
            liked_by_user[user_id].add(movie_id)

    # Precomputed once per user, reused across every (n0, m0) grid point below.
    per_user_scores = {}
    for user_id, (held_out_movie_id, held_out_score) in holdout_by_user.items():
        rated_movies = rated_movies_by_user[user_id]
        candidates = [m for m in all_movie_ids if m not in rated_movies]
        svd_scores = top_n_scores(score_svd_candidates(algo, user_id, candidates), svd_training.PERSIST_N)
        content_scores = top_n_scores(
            content_based_training.score_candidates(liked_by_user[user_id], rated_movies, top_k_by_movie),
            content_based_training.USER_PERSIST_N,
        )
        relevant = {held_out_movie_id} if held_out_score >= RELEVANT_RATING_THRESHOLD else set()
        per_user_scores[user_id] = (svd_scores, content_scores, relevant)

    results = {}
    for n0 in N0_GRID:
        for m0 in M0_GRID:
            ndcg_values = []
            for user_id, (svd_scores, content_scores, relevant) in per_user_scores.items():
                blended = recommendation_blending.blend_scores(
                    svd_scores, content_scores, user_rating_counts[user_id], movie_rating_counts, n0, m0
                )
                ranked = sorted(blended, key=blended.get, reverse=True)
                ndcg_values.append(ndcg_at_k(ranked, relevant, BLEND_SELECTION_K))
            results[(n0, m0)] = mean_metric(ndcg_values)
    return results


def _fmt(value: float | None) -> str:
    return f"{value:.4f}" if value is not None else "n/a"


def print_results(results: dict[str, dict[int, dict[str, float | None]]], evaluated_users: int) -> None:
    print(f"Evaluated {evaluated_users} users.")
    for model_name, by_k in results.items():
        print(f"\n{model_name}:")
        for k, metrics in by_k.items():
            print(
                f"  k={k}: precision={_fmt(metrics['precision'])} recall={_fmt(metrics['recall'])} "
                f"ndcg={_fmt(metrics['ndcg'])} coverage={_fmt(metrics['coverage'])}"
            )


def print_blend_grid_results(grid_results: dict[tuple[float, float], float | None]) -> None:
    print(f"\nblend grid search (selecting by mean NDCG@{BLEND_SELECTION_K}):")
    for (n0, m0), ndcg in grid_results.items():
        print(f"  N0={n0} M0={m0}: ndcg={_fmt(ndcg)}")

    scored = [(params, value) for params, value in grid_results.items() if value is not None]
    if not scored:
        print("  No grid point had any evaluable users -- can't recommend N0/M0 from this run.")
        return
    best_params, best_ndcg = max(scored, key=lambda p: p[1])
    print(f"  Best: N0={best_params[0]} M0={best_params[1]} (ndcg={_fmt(best_ndcg)})")


def run() -> None:
    print("Loading ratings and building the shared holdout split...")
    ratings_with_timestamps = load_ratings_with_timestamps()
    training_ratings, holdout_by_user, rated_movies_by_user = build_holdout_split(ratings_with_timestamps)
    if not holdout_by_user:
        print(f"No users with >= {MIN_RATINGS_TO_EVALUATE} ratings -- nothing to evaluate.")
        return
    print(f"  {len(holdout_by_user)} users eligible for evaluation, {len(training_ratings)} training ratings.")

    print("Training leakage-free SVD on the split...")
    algo = train_leakage_free_svd(training_ratings)

    print("Building content-based item-item similarity (leakage-free by construction -- uses no ratings)...")
    movies = content_based_training.load_movies()
    genres_by_movie = content_based_training.load_movie_genres()
    feature_matrix, _, _ = content_based_training.build_feature_matrix(movies, genres_by_movie)
    movie_ids = [m[0] for m in movies]
    top_k_by_movie = content_based_training.compute_top_k_neighbors(feature_matrix, movie_ids)

    print("Loading the shared candidate catalog...")
    all_movie_ids = svd_training.load_all_movie_ids()

    print("Scoring both models against the shared holdout...")
    results = evaluate(algo, top_k_by_movie, all_movie_ids, training_ratings, holdout_by_user, rated_movies_by_user)
    print_results(results, len(holdout_by_user))

    print("\nGrid-searching blend confidence weights (N0, M0)...")
    grid_results = evaluate_blend_grid(
        algo, top_k_by_movie, all_movie_ids, training_ratings, holdout_by_user, rated_movies_by_user
    )
    print_blend_grid_results(grid_results)
