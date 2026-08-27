"""Orchestration tests for models/svd_training.py's run().

These mock every collaborator run() calls (postgres/MinIO/Redis boundaries, and the SVD
build/evaluate/tune/train/generate steps) to verify the wiring -- branching on empty data,
and threading tuned hyperparameters and trainset/algo objects through in the right order --
without hitting real services or doing actual SVD training.
"""

from unittest.mock import MagicMock

import models.svd_training as svd_training

_BEST_PARAMS = {"n_factors": 50, "n_epochs": 20, "reg_all": 0.02}


def _patch_run_dependencies(monkeypatch, *, pg_ratings, ml_ratings, all_movie_ids=None):
    mock_data = MagicMock(name="dataset")
    mock_trainset = MagicMock(name="trainset")
    mock_data.build_full_trainset.return_value = mock_trainset
    mock_algo = MagicMock(name="algo")

    mocks = {
        "load_postgres_ratings": MagicMock(return_value=pg_ratings),
        "load_movielens_ratings": MagicMock(return_value=ml_ratings),
        "load_all_movie_ids": MagicMock(return_value=all_movie_ids or []),
        "build_dataset": MagicMock(return_value=mock_data),
        "evaluate_model": MagicMock(return_value={"rmse": 1.0, "mae": 0.8}),
        "tune_hyperparameters": MagicMock(return_value=(_BEST_PARAMS, {"rmse": 0.9, "mae": 0.7})),
        "train_model": MagicMock(return_value=mock_algo),
        "rated_movies_by_pg_user": MagicMock(return_value={}),
        "generate_top_n": MagicMock(return_value={}),
        "write_recommendations_to_redis": MagicMock(return_value=0),
    }
    for name, mock in mocks.items():
        monkeypatch.setattr(svd_training, name, mock)
    return mocks, mock_data, mock_trainset, mock_algo


def test_run_returns_early_when_no_ratings_from_either_source(monkeypatch, capsys):
    mocks, *_ = _patch_run_dependencies(monkeypatch, pg_ratings=[], ml_ratings=[])

    svd_training.run()

    mocks["build_dataset"].assert_not_called()
    mocks["evaluate_model"].assert_not_called()
    mocks["tune_hyperparameters"].assert_not_called()
    mocks["train_model"].assert_not_called()
    mocks["write_recommendations_to_redis"].assert_not_called()
    assert "nothing to train on" in capsys.readouterr().out


def test_run_trains_but_skips_recommendations_when_no_real_users(monkeypatch):
    ml_ratings = [("ml:1", 10, 5)]
    mocks, mock_data, mock_trainset, _ = _patch_run_dependencies(
        monkeypatch, pg_ratings=[], ml_ratings=ml_ratings
    )

    svd_training.run()

    mocks["build_dataset"].assert_called_once_with(ml_ratings)
    mocks["evaluate_model"].assert_called_once_with(mock_data)
    mocks["tune_hyperparameters"].assert_called_once_with(mock_data)
    mocks["train_model"].assert_called_once_with(mock_trainset, _BEST_PARAMS)
    mocks["rated_movies_by_pg_user"].assert_not_called()
    mocks["generate_top_n"].assert_not_called()
    mocks["write_recommendations_to_redis"].assert_not_called()


def test_run_full_happy_path_wires_tuned_params_and_writes_to_redis(monkeypatch):
    pg_ratings = [("pg:1", 10, 5), ("pg:2", 11, 4)]
    ml_ratings = [("ml:1", 12, 3)]
    mocks, mock_data, mock_trainset, mock_algo = _patch_run_dependencies(
        monkeypatch, pg_ratings=pg_ratings, ml_ratings=ml_ratings, all_movie_ids=[10, 11, 12, 13]
    )
    mocks["rated_movies_by_pg_user"].return_value = {"pg:1": {10}, "pg:2": {11}}
    mocks["generate_top_n"].return_value = {"pg:1": [(13, 4.2)], "pg:2": [(13, 3.9)]}
    mocks["write_recommendations_to_redis"].return_value = 2

    svd_training.run()

    mocks["build_dataset"].assert_called_once_with(pg_ratings + ml_ratings)
    mocks["train_model"].assert_called_once_with(mock_trainset, _BEST_PARAMS)
    mocks["rated_movies_by_pg_user"].assert_called_once_with(pg_ratings)
    mocks["generate_top_n"].assert_called_once_with(
        mock_algo, {"pg:1": {10}, "pg:2": {11}}, [10, 11, 12, 13]
    )
    mocks["write_recommendations_to_redis"].assert_called_once_with(
        {"pg:1": [(13, 4.2)], "pg:2": [(13, 3.9)]}
    )
