"""Tests for evaluation/evaluate_models.py: real-logic tests for rank_svd/rank_content/evaluate
(cheap, no DB/model training involved -- a fake algo and plain dicts are enough), plus an
orchestration test for run() mocking every collaborator, matching the training jobs' own style.
"""

from types import SimpleNamespace
from unittest.mock import MagicMock

import evaluation.evaluate_models as evaluate_models
from evaluation.evaluate_models import evaluate, evaluate_blend_grid, rank_content, rank_svd, top_n_scores


class FakeAlgo:
    """Minimal stand-in for a Surprise algo: a fixed predicted score per (uid, movie_id) pair,
    0.0 for anything not given (mirrors Surprise's own fallback-to-mean behavior closely enough
    for these tests, which only care about relative ordering)."""

    def __init__(self, scores: dict[tuple[str, int], float]):
        self._scores = scores

    def predict(self, uid, movie_id):
        return SimpleNamespace(est=self._scores.get((uid, movie_id), 0.0))


class TestRankSvd:
    def test_orders_candidates_by_predicted_score_descending(self):
        algo = FakeAlgo({("pg:1", 10): 3.0, ("pg:1", 11): 4.5})

        assert rank_svd(algo, 1, [10, 11]) == [11, 10]


class TestRankContent:
    def test_fills_zero_for_uncovered_candidates_and_sorts(self):
        top_k_by_movie = {100: [(10, 0.9)]}

        result = rank_content(liked_movies={100}, rated_movies=set(), top_k_by_movie=top_k_by_movie, candidates=[10, 11])

        assert result == [10, 11]  # 10 scored 0.9, 11 filled with 0.0


class TestTopNScores:
    def test_keeps_only_the_highest_n(self):
        assert top_n_scores({1: 0.5, 2: 0.9, 3: 0.1}, n=2) == {2: 0.9, 1: 0.5}


class TestEvaluateBlendGrid:
    def test_selects_ndcg_per_grid_point_using_training_set_counts_only(self, monkeypatch):
        # A single grid point, small enough to hand-verify: user has 1 training rating (movie 10,
        # liked), so user_alpha = 1/N0; movie 20 (the held-out item) has 1 training-set rating
        # (from a second user), so item_confidence = 1/M0. With N0=M0=1, effective_alpha=1.0 ->
        # pure SVD, and the fake algo ranks the held-out movie 20 first.
        monkeypatch.setattr(evaluate_models, "N0_GRID", [1])
        monkeypatch.setattr(evaluate_models, "M0_GRID", [1])
        algo = FakeAlgo({("pg:1", 20): 5.0, ("pg:1", 21): 1.0})
        top_k_by_movie = {}
        all_movie_ids = [20, 21]
        training_ratings = [(1, 10, 5), (2, 20, 5)]  # user 1 liked movie 10; user 2 rated movie 20
        holdout_by_user = {1: (20, 5)}  # relevant (>= 4.0)
        rated_movies_by_user = {1: {10}}

        result = evaluate_blend_grid(
            algo, top_k_by_movie, all_movie_ids, training_ratings, holdout_by_user, rated_movies_by_user
        )

        assert result == {(1, 1): 1.0}

    def test_none_when_no_grid_point_has_evaluable_users(self):
        holdout_by_user = {1: (20, 2)}  # rating 2 -- below RELEVANT_RATING_THRESHOLD (4.0)

        result = evaluate_blend_grid(FakeAlgo({}), {}, [20, 21], [], holdout_by_user, {1: set()})

        assert all(value is None for value in result.values())


class TestEvaluate:
    def test_scores_relevant_holdout_ranked_first_by_both_models(self, monkeypatch):
        monkeypatch.setattr(evaluate_models, "EVAL_KS", [1])
        algo = FakeAlgo({("pg:1", 20): 5.0, ("pg:1", 21): 1.0})
        top_k_by_movie = {10: [(20, 0.9), (21, 0.1)]}
        all_movie_ids = [20, 21]
        training_ratings = [(1, 10, 5)]  # user 1 liked movie 10
        holdout_by_user = {1: (20, 5)}  # held out movie 20, rating 5 -- relevant (>= 4.0)
        rated_movies_by_user = {1: {10}}

        result = evaluate(algo, top_k_by_movie, all_movie_ids, training_ratings, holdout_by_user, rated_movies_by_user)

        for model in ("svd", "content"):
            assert result[model][1]["precision"] == 1.0
            assert result[model][1]["recall"] == 1.0
            assert result[model][1]["ndcg"] == 1.0
            assert result[model][1]["coverage"] == 0.5  # only movie 20 in the lone user's top-1

    def test_excludes_users_with_non_relevant_holdout_from_the_mean(self, monkeypatch):
        monkeypatch.setattr(evaluate_models, "EVAL_KS", [1])
        algo = FakeAlgo({})
        holdout_by_user = {1: (20, 2)}  # rating 2 -- below RELEVANT_RATING_THRESHOLD (4.0)

        result = evaluate(algo, {}, [20, 21], [], holdout_by_user, {1: set()})

        assert result["svd"][1]["precision"] is None
        assert result["content"][1]["recall"] is None


def _patch_run_dependencies(monkeypatch, *, holdout_by_user):
    mocks = {
        "load_ratings_with_timestamps": MagicMock(return_value=[]),
        "build_holdout_split": MagicMock(return_value=([], holdout_by_user, {})),
        "train_leakage_free_svd": MagicMock(return_value=MagicMock(name="algo")),
        "evaluate": MagicMock(return_value={"svd": {}, "content": {}}),
        "print_results": MagicMock(return_value=None),
        "evaluate_blend_grid": MagicMock(return_value={}),
        "print_blend_grid_results": MagicMock(return_value=None),
    }
    for name, mock in mocks.items():
        monkeypatch.setattr(evaluate_models, name, mock)

    cbt_mocks = {
        "load_movies": MagicMock(return_value=[(1, "d")]),
        "load_movie_genres": MagicMock(return_value={}),
        "build_feature_matrix": MagicMock(return_value=(MagicMock(name="matrix"), None, None)),
        "compute_top_k_neighbors": MagicMock(return_value={}),
    }
    for name, mock in cbt_mocks.items():
        monkeypatch.setattr(evaluate_models.content_based_training, name, mock)

    svd_mocks = {"load_all_movie_ids": MagicMock(return_value=[1, 2])}
    for name, mock in svd_mocks.items():
        monkeypatch.setattr(evaluate_models.svd_training, name, mock)

    return mocks, cbt_mocks, svd_mocks


def test_run_returns_early_when_no_eligible_users(monkeypatch, capsys):
    mocks, cbt_mocks, _ = _patch_run_dependencies(monkeypatch, holdout_by_user={})

    evaluate_models.run()

    mocks["train_leakage_free_svd"].assert_not_called()
    cbt_mocks["load_movies"].assert_not_called()
    mocks["evaluate"].assert_not_called()
    mocks["print_results"].assert_not_called()
    mocks["evaluate_blend_grid"].assert_not_called()
    mocks["print_blend_grid_results"].assert_not_called()
    assert "nothing to evaluate" in capsys.readouterr().out


def test_run_full_happy_path_wires_everything(monkeypatch):
    holdout_by_user = {1: (20, 5)}
    mocks, cbt_mocks, svd_mocks = _patch_run_dependencies(monkeypatch, holdout_by_user=holdout_by_user)
    training_ratings = [(1, 10, 5)]
    rated_movies_by_user = {1: {10}}
    mocks["build_holdout_split"].return_value = (training_ratings, holdout_by_user, rated_movies_by_user)
    fake_results = {"svd": {}, "content": {}}
    mocks["evaluate"].return_value = fake_results

    evaluate_models.run()

    mocks["train_leakage_free_svd"].assert_called_once_with(training_ratings)
    cbt_mocks["load_movies"].assert_called_once()
    cbt_mocks["compute_top_k_neighbors"].assert_called_once()
    svd_mocks["load_all_movie_ids"].assert_called_once()

    eval_args = mocks["evaluate"].call_args.args
    assert eval_args[3] == training_ratings
    assert eval_args[4] == holdout_by_user
    assert eval_args[5] == rated_movies_by_user
    mocks["print_results"].assert_called_once_with(fake_results, 1)

    grid_args = mocks["evaluate_blend_grid"].call_args.args
    assert grid_args[3] == training_ratings
    assert grid_args[4] == holdout_by_user
    assert grid_args[5] == rated_movies_by_user
    mocks["print_blend_grid_results"].assert_called_once_with(mocks["evaluate_blend_grid"].return_value)
