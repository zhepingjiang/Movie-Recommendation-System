"""Tests for models/recommendation_blending.py: unit tests for the blending math (cheap, no DB
involved) plus orchestration tests for run() mocking every collaborator, matching the other two
training jobs' style.
"""

from contextlib import contextmanager
from datetime import datetime, timezone
from unittest.mock import MagicMock

import pytest
from psycopg2.errors import DeadlockDetected

import models.recommendation_blending as recommendation_blending
from models.recommendation_blending import (
    blend_all_users,
    blend_scores,
    delete_stale_nearline_recommendations,
    effective_alpha,
    item_confidence,
    load_cached_scores,
    load_movie_rating_counts,
    load_user_rating_counts,
    min_max_normalize,
    user_alpha,
)


class FakeCursor:
    def __init__(self, results, rowcount=0):
        self._results = results
        self._index = -1
        self.rowcount = rowcount
        self.executed = []

    def execute(self, query, params=None):
        self._index += 1
        self.executed.append((query, params))

    def fetchall(self):
        return self._results[self._index]


def _patch_cursor(monkeypatch, results, rowcount=0):
    fake_cursor = FakeCursor(results, rowcount)

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

    def test_logs_the_weight_assigned_to_each_model_per_user(self, caplog):
        with caplog.at_level("INFO", logger="models.recommendation_blending"):
            blend_all_users(
                svd_scores_by_user={1: {10: 1.0}},
                content_scores_by_user={1: {10: 0.0}},
                user_rating_counts={1: 10},  # user_alpha = 10/10 = 1.0
                movie_rating_counts={10: 20},  # item_confidence = 20/20 = 1.0
                n0=10,
                m0=20,
            )

        [record] = caplog.records
        message = record.getMessage()
        assert "user 1" in message
        assert "svd_weight=1.00" in message

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


STALE_BEFORE = datetime(2026, 9, 23, 10, 0, tzinfo=timezone.utc)


def test_delete_stale_nearline_recommendations_only_deletes_nearline_rows_older_than_cutoff(monkeypatch):
    fake_cursor = _patch_cursor(monkeypatch, [], rowcount=7)

    assert delete_stale_nearline_recommendations(STALE_BEFORE) == 7
    assert fake_cursor.executed == [
        (
            "DELETE FROM recommendation_cache WHERE model_version = %s AND generated_at < %s",
            ("nearline_v1", STALE_BEFORE),
        )
    ]


def _patch_cursor_failing_with_deadlock(monkeypatch, deadlocked_attempt_count, rowcount=0, jitter_multiplier=1.0):
    """Like _patch_cursor, but the first deadlocked_attempt_count get_cursor() transactions raise
    DeadlockDetected from execute(), the way psycopg2 surfaces Postgres aborting our side."""
    attempted_transaction_count = 0
    sleep_durations = []

    @contextmanager
    def fake_get_cursor():
        nonlocal attempted_transaction_count
        attempted_transaction_count += 1
        fake_cursor = FakeCursor([], rowcount)
        if attempted_transaction_count <= deadlocked_attempt_count:
            fake_cursor.execute = MagicMock(side_effect=DeadlockDetected())
        yield fake_cursor

    monkeypatch.setattr(recommendation_blending, "get_cursor", fake_get_cursor)
    monkeypatch.setattr(recommendation_blending.time, "sleep", sleep_durations.append)
    monkeypatch.setattr(recommendation_blending.random, "uniform", lambda low, high: jitter_multiplier)
    return lambda: attempted_transaction_count, sleep_durations


def test_delete_stale_nearline_recommendations_retries_after_deadlock_with_exponential_backoff(monkeypatch):
    get_attempted_transaction_count, sleep_durations = _patch_cursor_failing_with_deadlock(
        monkeypatch, deadlocked_attempt_count=4, rowcount=4
    )

    assert delete_stale_nearline_recommendations(STALE_BEFORE) == 4
    assert get_attempted_transaction_count() == 5
    assert sleep_durations == [1.0, 2.0, 4.0, 8.0]


def test_delete_stale_nearline_recommendations_backoff_is_jittered_then_capped(monkeypatch):
    _, sleep_durations = _patch_cursor_failing_with_deadlock(
        monkeypatch, deadlocked_attempt_count=4, jitter_multiplier=1.5
    )

    delete_stale_nearline_recommendations(STALE_BEFORE)

    # 1.5x jitter on 1, 2, 4, 8 -- the last (12s) is capped at NEARLINE_DELETE_RETRY_MAX_BACKOFF_SECONDS.
    assert sleep_durations == [1.5, 3.0, 6.0, 10.0]


def test_nearline_delete_retry_jitter_stays_within_bounds():
    for failed_attempt_number in range(1, recommendation_blending.NEARLINE_DELETE_MAX_ATTEMPTS):
        exponential_backoff_seconds = 2 ** (failed_attempt_number - 1)
        for _ in range(100):
            backoff_seconds = recommendation_blending._nearline_delete_retry_backoff_seconds(failed_attempt_number)
            assert 0.5 * exponential_backoff_seconds <= backoff_seconds
            assert backoff_seconds <= min(10.0, 1.5 * exponential_backoff_seconds)


def test_delete_stale_nearline_recommendations_gives_up_after_max_attempts(monkeypatch):
    get_attempted_transaction_count, sleep_durations = _patch_cursor_failing_with_deadlock(
        monkeypatch, deadlocked_attempt_count=recommendation_blending.NEARLINE_DELETE_MAX_ATTEMPTS
    )

    with pytest.raises(DeadlockDetected):
        delete_stale_nearline_recommendations(STALE_BEFORE)
    assert get_attempted_transaction_count() == recommendation_blending.NEARLINE_DELETE_MAX_ATTEMPTS
    assert len(sleep_durations) == recommendation_blending.NEARLINE_DELETE_MAX_ATTEMPTS - 1


def _patch_run_dependencies(monkeypatch, *, svd_scores_by_user, content_scores_by_user):
    mocks = {
        "load_cached_scores": MagicMock(side_effect=lambda model_version: (
            svd_scores_by_user if model_version == recommendation_blending.SVD_MODEL_VERSION else content_scores_by_user
        )),
        "load_user_rating_counts": MagicMock(return_value={}),
        "load_movie_rating_counts": MagicMock(return_value={}),
        "blend_all_users": MagicMock(return_value={}),
        "write_blended_scores_to_postgres": MagicMock(return_value=0),
        "delete_stale_nearline_recommendations": MagicMock(return_value=0),
    }
    for name, mock in mocks.items():
        monkeypatch.setattr(recommendation_blending, name, mock)
    return mocks


def test_run_returns_early_when_neither_model_has_cached_scores(monkeypatch, capsys):
    mocks = _patch_run_dependencies(monkeypatch, svd_scores_by_user={}, content_scores_by_user={})

    recommendation_blending.run()

    mocks["blend_all_users"].assert_not_called()
    mocks["write_blended_scores_to_postgres"].assert_not_called()
    mocks["delete_stale_nearline_recommendations"].assert_not_called()
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


def test_run_clears_nearline_only_after_the_blend_is_persisted(monkeypatch):
    mocks = _patch_run_dependencies(monkeypatch, svd_scores_by_user={1: {10: 4.5}}, content_scores_by_user={})
    call_order = []
    mocks["write_blended_scores_to_postgres"].side_effect = lambda *args: call_order.append("write") or 1
    mocks["delete_stale_nearline_recommendations"].side_effect = (
        lambda stale_before: call_order.append("delete_nearline") or 3
    )

    recommendation_blending.run()

    assert call_order == ["write", "delete_nearline"]


def test_run_deletes_nearline_rows_older_than_max_age_relative_to_the_blend(monkeypatch):
    mocks = _patch_run_dependencies(monkeypatch, svd_scores_by_user={1: {10: 4.5}}, content_scores_by_user={})

    recommendation_blending.run()

    blend_generated_at = mocks["write_blended_scores_to_postgres"].call_args.args[2]
    mocks["delete_stale_nearline_recommendations"].assert_called_once_with(
        blend_generated_at - recommendation_blending.NEARLINE_MAX_AGE
    )
