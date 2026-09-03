"""Tests for models/offline_pipeline.py: verifies the three stages run in the one valid order
(SVD -> content-based -> blending) and that a failure in an earlier stage stops the pipeline
before any later stage runs, rather than continuing on with stale/partial data.
"""

from unittest.mock import MagicMock

import models.offline_pipeline as offline_pipeline


def _patch_stages(monkeypatch):
    calls = []
    mocks = {
        "svd_training": MagicMock(run=MagicMock(side_effect=lambda: calls.append("svd"))),
        "content_based_training": MagicMock(run=MagicMock(side_effect=lambda: calls.append("content"))),
        "recommendation_blending": MagicMock(run=MagicMock(side_effect=lambda: calls.append("blend"))),
    }
    for name, mock in mocks.items():
        monkeypatch.setattr(offline_pipeline, name, mock)
    return mocks, calls


def test_runs_all_three_stages_in_order(monkeypatch):
    mocks, calls = _patch_stages(monkeypatch)

    offline_pipeline.run()

    assert calls == ["svd", "content", "blend"]
    mocks["svd_training"].run.assert_called_once()
    mocks["content_based_training"].run.assert_called_once()
    mocks["recommendation_blending"].run.assert_called_once()


def test_stops_before_content_based_if_svd_training_fails(monkeypatch):
    mocks, calls = _patch_stages(monkeypatch)
    mocks["svd_training"].run.side_effect = RuntimeError("boom")

    try:
        offline_pipeline.run()
        assert False, "expected RuntimeError to propagate"
    except RuntimeError:
        pass

    assert calls == []
    mocks["content_based_training"].run.assert_not_called()
    mocks["recommendation_blending"].run.assert_not_called()


def test_stops_before_blending_if_content_based_fails(monkeypatch):
    mocks, calls = _patch_stages(monkeypatch)
    mocks["content_based_training"].run.side_effect = RuntimeError("boom")

    try:
        offline_pipeline.run()
        assert False, "expected RuntimeError to propagate"
    except RuntimeError:
        pass

    assert calls == ["svd"]
    mocks["recommendation_blending"].run.assert_not_called()
