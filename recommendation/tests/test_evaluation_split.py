"""Tests for evaluation/split.py: the shared holdout split every model evaluator reads."""

from contextlib import contextmanager

import evaluation.split as split_module
from evaluation.split import MIN_RATINGS_TO_EVALUATE, build_holdout_split, load_ratings_with_timestamps


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

    monkeypatch.setattr(split_module, "get_cursor", fake_get_cursor)
    return fake_cursor


class TestLoadRatingsWithTimestamps:
    def test_returns_plain_tuples(self, monkeypatch):
        _patch_cursor(
            monkeypatch, [[{"user_id": 1, "movie_id": 10, "score": 5, "created_at": "t1"}]]
        )

        assert load_ratings_with_timestamps() == [(1, 10, 5, "t1")]


class TestBuildHoldoutSplit:
    def test_holds_out_the_single_most_recent_rating(self):
        ratings = [(1, 10, 5, 1), (1, 11, 4, 2), (1, 12, 3, 3)]  # created_at as a sortable int

        training_ratings, holdout_by_user, rated_movies_by_user = build_holdout_split(ratings)

        assert holdout_by_user == {1: (12, 3)}
        assert set(training_ratings) == {(1, 10, 5), (1, 11, 4)}
        assert rated_movies_by_user == {1: {10, 11}}

    def test_excludes_users_below_min_ratings_from_evaluation_but_keeps_their_training_data(self):
        assert MIN_RATINGS_TO_EVALUATE == 2
        ratings = [(1, 10, 5, 1)]  # only 1 rating -- not eligible

        training_ratings, holdout_by_user, rated_movies_by_user = build_holdout_split(ratings)

        assert holdout_by_user == {}
        assert rated_movies_by_user == {}
        assert training_ratings == [(1, 10, 5)]

    def test_each_user_evaluated_independently(self):
        ratings = [
            (1, 10, 5, 1), (1, 11, 4, 2),  # user 1: eligible
            (2, 20, 3, 1),  # user 2: only 1 rating, not eligible
        ]

        training_ratings, holdout_by_user, rated_movies_by_user = build_holdout_split(ratings)

        assert holdout_by_user == {1: (11, 4)}
        assert rated_movies_by_user == {1: {10}}
        assert set(training_ratings) == {(1, 10, 5), (2, 20, 3)}
