"""Tests for models/recommendation_blending.py: unit tests for the blending math (cheap, no DB
involved) plus orchestration tests for run() mocking every collaborator, matching the other two
training jobs' style.
"""

from contextlib import contextmanager
from datetime import datetime
from unittest.mock import MagicMock

import models.recommendation_blending as recommendation_blending
from models.recommendation_blending import (
    blend_all_users,
    blend_scores,
    effective_alpha,
    item_confidence,
    load_cached_scores,
    load_movie_rating_counts,
    load_user_rating_counts,
    min_max_normalize,
    user_alpha,
)


class FakeCursor:
    def __init__(self, results):
        self._results = results
        self._index = -1

    def execute(self, query, params=None):
        self._index += 1

    def fetchall(self):
        return self._results[self._index]


def _patch_cursor(monkeypatch, results):
    fake_cursor = FakeCursor(results)

    @contextmanager
    def fake_get_cursor():
        yield fake_cursor

    monkeypatch.setattr(recommendation_blending, "get_cursor", fake_get_cursor)
    return fake_cursor


class TestMinMaxNormalize:
    def test_empty_input(self):
        assert min_max_normalize({}) == {}

    def test_scales_to_zero_one_range(self):
        assert min_max_normalize({1: 2.0, 2: 4.0, 3: 6.0}) == {1: 0.0, 2: 0.5, 3: 1.0}

    def test_tied_scores_normalize_to_one_rather_than_dividing_by_zero(self):
        assert min_max_normalize({1: 5.0, 2: 5.0}) == {1: 1.0, 2: 1.0}


class TestConfidenceCurves:
    def test_user_alpha_scales_linearly_up_to_n0(self):
        assert user_alpha(5, n0=10) == 0.5

    def test_user_alpha_caps_at_one(self):
        assert user_alpha(100, n0=10) == 1.0

    def test_item_confidence_scales_linearly_up_to_m0(self):
        assert item_confidence(10, m0=20) == 0.5

    def test_effective_alpha_is_the_product_not_a_hard_cutoff(self):
        # 50% user confidence x 50% item confidence = 25%, not 0 and not 50%.
        assert effective_alpha(user_rating_count=5, movie_rating_count=10, n0=10, m0=20) == 0.25


class TestBlendScores:
    def test_weights_by_effective_alpha(self):
        # user_alpha=1.0 (10/10), item_confidence=1.0 (20/20) -> effective_alpha=1.0 -> pure SVD.
        result = blend_scores(
            svd_scores={1: 1.0}, content_scores={1: 0.0}, user_rating_count=10,
            movie_rating_counts={1: 20}, n0=10, m0=20,
        )

        assert result == {1: 1.0}

    def test_zero_fills_candidates_only_one_model_covers(self):
        result = blend_scores(
            svd_scores={1: 1.0}, content_scores={2: 1.0}, user_rating_count=0,
            movie_rating_counts={}, n0=10, m0=20,
        )

        # effective_alpha=0 (no user history) -> pure content-based; movie 1 has no content score.
        assert result == {1: 0.0, 2: 1.0}

    def test_missing_movie_rating_count_defaults_to_zero_confidence(self):
        # Two content candidates so normalization is meaningful (a single-candidate set always
        # normalizes to 1.0, regardless of its raw value -- see TestMinMaxNormalize).
        result = blend_scores(
            svd_scores={1: 1.0}, content_scores={1: 0.0, 2: 1.0}, user_rating_count=100,
            movie_rating_counts={}, n0=10, m0=20,
        )

        # user_alpha=1.0 but item_confidence=0.0 (movie 1 absent from movie_rating_counts) -> pure
        # content for movie 1, which normalizes to 0.0 (the lower of the two content scores).
        assert result[1] == 0.0


class TestBlendAllUsers:
    def test_ranks_each_users_blend_descending(self):
        result = blend_all_users(
            svd_scores_by_user={1: {10: 1.0, 11: 0.0}},
            content_scores_by_user={1: {10: 0.0, 11: 1.0}},
            user_rating_counts={1: 100},
            movie_rating_counts={10: 100, 11: 100},
            n0=10,
            m0=20,
        )

        assert [movie_id for movie_id, _ in result[1]] == [10, 11]

    def test_includes_users_present_in_only_one_model(self):
        result = blend_all_users(
            svd_scores_by_user={1: {10: 1.0}},
            content_scores_by_user={2: {20: 1.0}},
            user_rating_counts={},
            movie_rating_counts={},
            n0=10,
            m0=20,
        )

        assert set(result) == {1, 2}


class TestLoaders:
    def test_load_cached_scores_groups_by_user(self, monkeypatch):
        _patch_cursor(monkeypatch, [[{"user_id": 1, "movie_id": 10, "score": "0.9"}]])

        assert load_cached_scores("svd_v1") == {1: {10: 0.9}}

    def test_load_user_rating_counts(self, monkeypatch):
        _patch_cursor(monkeypatch, [[{"user_id": 1, "cnt": 5}]])

        assert load_user_rating_counts() == {1: 5}

    def test_load_movie_rating_counts(self, monkeypatch):
        _patch_cursor(monkeypatch, [[{"movie_id": 10, "cnt": 3}]])

        assert load_movie_rating_counts() == {10: 3}


def _patch_run_dependencies(monkeypatch, *, svd_scores_by_user, content_scores_by_user):
    mocks = {
        "load_cached_scores": MagicMock(side_effect=lambda model_version: (
            svd_scores_by_user if model_version == recommendation_blending.SVD_MODEL_VERSION else content_scores_by_user
        )),
        "load_user_rating_counts": MagicMock(return_value={}),
        "load_movie_rating_counts": MagicMock(return_value={}),
        "blend_all_users": MagicMock(return_value={}),
        "write_blended_scores_to_postgres": MagicMock(return_value=0),
    }
    for name, mock in mocks.items():
        monkeypatch.setattr(recommendation_blending, name, mock)
    return mocks


def test_run_returns_early_when_neither_model_has_cached_scores(monkeypatch, capsys):
    mocks = _patch_run_dependencies(monkeypatch, svd_scores_by_user={}, content_scores_by_user={})

    recommendation_blending.run()

    mocks["blend_all_users"].assert_not_called()
    mocks["write_blended_scores_to_postgres"].assert_not_called()
    assert "nothing to blend" in capsys.readouterr().out


def test_run_full_happy_path_wires_everything(monkeypatch):
    svd_scores_by_user = {1: {10: 4.5}}
    content_scores_by_user = {1: {11: 0.8}}
    mocks = _patch_run_dependencies(
        monkeypatch, svd_scores_by_user=svd_scores_by_user, content_scores_by_user=content_scores_by_user
    )
    mocks["load_user_rating_counts"].return_value = {1: 5}
    mocks["load_movie_rating_counts"].return_value = {10: 3, 11: 3}
    blended = {1: [(10, 0.9), (11, 0.4)]}
    mocks["blend_all_users"].return_value = blended
    mocks["write_blended_scores_to_postgres"].return_value = 2

    recommendation_blending.run()

    mocks["blend_all_users"].assert_called_once_with(
        svd_scores_by_user, content_scores_by_user, {1: 5}, {10: 3, 11: 3}
    )

    pg_args = mocks["write_blended_scores_to_postgres"].call_args.args
    assert pg_args[0] == blended
    assert pg_args[1] == recommendation_blending.MODEL_VERSION
    assert isinstance(pg_args[2], datetime)
