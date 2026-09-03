"""Ranking metrics for comparing recommendation models against evaluation.split's shared holdout.

Each function takes one user's ranked candidate list (movie ids, highest score first) and the set
of relevant movie ids for that user. This module stays agnostic to what "relevant" means -- see
RELEVANT_RATING_THRESHOLD in evaluate_models.py -- it just consumes whatever set it's given, so it
works unchanged if that definition (or the holdout size) ever changes.
"""

import math


def precision_at_k(ranked_movie_ids: list[int], relevant_movie_ids: set[int], k: int) -> float | None:
    """None (not 0) when the user has no relevant items -- there's nothing to be precise about,
    and averaging that in as a 0 would understate every model equally, hiding the real signal."""
    if not relevant_movie_ids:
        return None
    hits = sum(1 for m in ranked_movie_ids[:k] if m in relevant_movie_ids)
    return hits / k


def recall_at_k(ranked_movie_ids: list[int], relevant_movie_ids: set[int], k: int) -> float | None:
    """None (not 0) for the same reason as precision_at_k -- recall is undefined (0/0), not 0,
    when there's no relevant item to have found."""
    if not relevant_movie_ids:
        return None
    hits = sum(1 for m in ranked_movie_ids[:k] if m in relevant_movie_ids)
    return hits / len(relevant_movie_ids)


def ndcg_at_k(ranked_movie_ids: list[int], relevant_movie_ids: set[int], k: int) -> float | None:
    if not relevant_movie_ids:
        return None
    dcg = sum(1 / math.log2(i + 2) for i, m in enumerate(ranked_movie_ids[:k]) if m in relevant_movie_ids)
    ideal_hits = min(len(relevant_movie_ids), k)
    idcg = sum(1 / math.log2(i + 2) for i in range(ideal_hits))
    return dcg / idcg if idcg > 0 else None


def mean_metric(values: list[float | None]) -> float | None:
    """Averages a metric across users, dropping the Nones that precision_at_k/recall_at_k/
    ndcg_at_k return for users with no relevant items -- those users are excluded from this
    model's score entirely rather than counted as a 0."""
    present = [v for v in values if v is not None]
    return sum(present) / len(present) if present else None


def coverage(ranked_lists_by_user: dict[int, list[int]], catalog_size: int, k: int) -> float:
    """Fraction of the full candidate catalog that appears in *any* user's top-k across the whole
    evaluation run -- a model that always recommends the same popular handful scores low here even
    if its precision/recall look good."""
    if not catalog_size:
        return 0.0
    recommended = set()
    for ranked_movie_ids in ranked_lists_by_user.values():
        recommended.update(ranked_movie_ids[:k])
    return len(recommended) / catalog_size
