"""Tests for models/content_based_training.py: orchestration tests for run() (mocking every
collaborator, matching test_svd_training.py's style) plus unit tests for the pieces that do real
work -- neighbor computation and Postgres row loading -- since unlike svd_training's SVD fit,
these are plain, cheap, and worth exercising for real rather than mocking.
"""

from contextlib import contextmanager
from datetime import datetime
from unittest.mock import MagicMock

import numpy as np
from scipy.sparse import csr_matrix

import models.content_based_training as content_based_training
from models.content_based_training import (
    aggregate_user_scores,
    compute_top_k_neighbors,
    load_movie_genres,
    load_movies,
    load_ratings,
)


class FakeCursor:
    """Stands in for the psycopg2 dict-cursor: returns canned rows per execute() call, in order."""

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

    monkeypatch.setattr(content_based_training, "get_cursor", fake_get_cursor)
    return fake_cursor


class TestLoadMovies:
    def test_sorts_by_id_and_nulls_description_to_empty_string(self, monkeypatch):
        _patch_cursor(
            monkeypatch,
            [[{"id": 3, "description": "third"}, {"id": 1, "description": None}, {"id": 2, "description": "second"}]],
        )

        assert load_movies() == [(1, ""), (2, "second"), (3, "third")]


class TestLoadMovieGenres:
    def test_groups_genre_names_by_movie_id(self, monkeypatch):
        _patch_cursor(
            monkeypatch,
            [[{"movie_id": 1, "name": "Action"}, {"movie_id": 1, "name": "Comedy"}, {"movie_id": 2, "name": "Drama"}]],
        )

        assert load_movie_genres() == {1: ["Action", "Comedy"], 2: ["Drama"]}


class TestLoadRatings:
    def test_returns_plain_user_movie_score_triples(self, monkeypatch):
        _patch_cursor(monkeypatch, [[{"user_id": 1, "movie_id": 10, "score": 5}]])

        assert load_ratings() == [(1, 10, 5)]


class TestAggregateUserScores:
    def test_scores_neighbors_of_liked_movies_by_mean_similarity(self):
        ratings = [(1, 10, 5)]  # user 1 liked movie 10
        top_k_by_movie = {10: [(20, 0.9), (21, 0.5)]}

        result = aggregate_user_scores(ratings, top_k_by_movie)

        assert result == {1: [(20, 0.9), (21, 0.5)]}

    def test_averages_similarity_across_multiple_liked_movies(self):
        ratings = [(1, 10, 5), (1, 11, 4)]
        top_k_by_movie = {10: [(20, 1.0)], 11: [(20, 0.6)]}

        result = aggregate_user_scores(ratings, top_k_by_movie)

        assert result == {1: [(20, 0.8)]}

    def test_excludes_movies_the_user_already_rated(self):
        ratings = [(1, 10, 5), (1, 20, 3)]  # already rated (and disliked) movie 20
        top_k_by_movie = {10: [(20, 0.9), (21, 0.5)]}

        result = aggregate_user_scores(ratings, top_k_by_movie)

        assert result == {1: [(21, 0.5)]}

    def test_ignores_movies_rated_below_liked_threshold(self):
        ratings = [(1, 10, 3)]  # below LIKED_RATING_THRESHOLD (4)
        top_k_by_movie = {10: [(20, 0.9)]}

        assert aggregate_user_scores(ratings, top_k_by_movie) == {}

    def test_user_absent_entirely_when_no_candidates_survive(self):
        ratings = [(1, 10, 5)]
        top_k_by_movie = {10: []}

        assert aggregate_user_scores(ratings, top_k_by_movie) == {}

    def test_respects_n(self):
        ratings = [(1, 10, 5)]
        top_k_by_movie = {10: [(20, 0.9), (21, 0.8), (22, 0.7)]}

        result = aggregate_user_scores(ratings, top_k_by_movie, n=2)

        assert result == {1: [(20, 0.9), (21, 0.8)]}


class TestComputeTopKNeighbors:
    def test_excludes_self_and_orders_by_score_descending(self):
        # Movie 1 and 2 are identical (similarity 1.0); movie 3 is orthogonal to both.
        matrix = csr_matrix(np.array([[1, 0], [1, 0], [0, 1]], dtype=float))

        result = compute_top_k_neighbors(matrix, [1, 2, 3], k=5)

        assert [mid for mid, _ in result[1]] == [2, 3]
        assert result[1][0][1] == 1.0
        assert result[1][1][1] == 0.0

    def test_respects_k(self):
        matrix = csr_matrix(np.eye(4, dtype=float))  # every movie orthogonal to every other

        result = compute_top_k_neighbors(matrix, [1, 2, 3, 4], k=2)

        assert len(result[1]) == 2


_VECTORIZER = MagicMock(name="vectorizer")
_BINARIZER = MagicMock(name="binarizer")


def _patch_run_dependencies(monkeypatch, *, movies, genres_by_movie=None, ratings=None):
    mock_matrix = MagicMock(name="feature_matrix")

    mocks = {
        "load_movies": MagicMock(return_value=movies),
        "load_movie_genres": MagicMock(return_value=genres_by_movie or {}),
        "build_feature_matrix": MagicMock(return_value=(mock_matrix, _VECTORIZER, _BINARIZER)),
        "save_model_to_minio": MagicMock(return_value=None),
        "compute_top_k_neighbors": MagicMock(return_value={}),
        "save_similarities_to_minio": MagicMock(return_value=None),
        "write_similarities_to_postgres": MagicMock(return_value=0),
        "load_ratings": MagicMock(return_value=ratings or []),
        "aggregate_user_scores": MagicMock(return_value={}),
        "write_user_scores_to_postgres": MagicMock(return_value=0),
    }
    for name, mock in mocks.items():
        monkeypatch.setattr(content_based_training, name, mock)
    return mocks, mock_matrix


def test_run_returns_early_when_no_movies(monkeypatch, capsys):
    mocks, _ = _patch_run_dependencies(monkeypatch, movies=[])

    content_based_training.run()

    mocks["build_feature_matrix"].assert_not_called()
    mocks["save_model_to_minio"].assert_not_called()
    mocks["compute_top_k_neighbors"].assert_not_called()
    mocks["write_similarities_to_postgres"].assert_not_called()
    mocks["load_ratings"].assert_not_called()
    mocks["write_user_scores_to_postgres"].assert_not_called()
    assert "nothing to train on" in capsys.readouterr().out


def test_run_persists_item_similarities_but_skips_per_user_recs_when_no_ratings(monkeypatch, capsys):
    movies = [(1, "a movie"), (2, "another movie")]
    mocks, _ = _patch_run_dependencies(monkeypatch, movies=movies, ratings=[])
    mocks["write_similarities_to_postgres"].return_value = 2

    content_based_training.run()

    mocks["write_similarities_to_postgres"].assert_called_once()
    mocks["aggregate_user_scores"].assert_not_called()
    mocks["write_user_scores_to_postgres"].assert_not_called()
    assert "nothing to generate per-user recommendations for" in capsys.readouterr().out


def test_run_full_happy_path_wires_run_id_and_persists_everywhere(monkeypatch):
    movies = [(1, "a movie"), (2, "another movie")]
    top_k = {1: [(2, 0.8)], 2: [(1, 0.8)]}
    ratings = [(1, 1, 5)]
    user_scores = {1: [(2, 0.8)]}
    mocks, mock_matrix = _patch_run_dependencies(monkeypatch, movies=movies, ratings=ratings)
    mocks["compute_top_k_neighbors"].return_value = top_k
    mocks["write_similarities_to_postgres"].return_value = 2
    mocks["aggregate_user_scores"].return_value = user_scores
    mocks["write_user_scores_to_postgres"].return_value = 1

    content_based_training.run()

    mocks["build_feature_matrix"].assert_called_once_with(movies, mocks["load_movie_genres"].return_value)
    mocks["compute_top_k_neighbors"].assert_called_once_with(
        mock_matrix, [1, 2], content_based_training.PERSIST_N
    )

    save_model_args = mocks["save_model_to_minio"].call_args.args
    save_sims_args = mocks["save_similarities_to_minio"].call_args.args
    assert save_model_args == (_VECTORIZER, _BINARIZER, save_model_args[2])
    assert save_sims_args == (top_k, save_sims_args[1])
    assert save_model_args[2] == save_sims_args[1]  # same run_id used for both

    sims_pg_args = mocks["write_similarities_to_postgres"].call_args.args
    assert sims_pg_args[0] == top_k
    assert sims_pg_args[1] == content_based_training.MODEL_VERSION
    assert isinstance(sims_pg_args[2], datetime)

    mocks["aggregate_user_scores"].assert_called_once_with(ratings, top_k)

    user_pg_args = mocks["write_user_scores_to_postgres"].call_args.args
    assert user_pg_args[0] == user_scores
    assert user_pg_args[1] == content_based_training.MODEL_VERSION
    # Same generated_at timestamp used for both the item-similarity and per-user writes.
    assert user_pg_args[2] == sims_pg_args[2]
