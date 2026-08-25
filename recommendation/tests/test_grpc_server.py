import grpc
import pytest

import grpc_server
from generated import recommendation_pb2


class _Aborted(Exception):
    pass


class _FakeContext:
    def __init__(self):
        self.code = None
        self.details = None

    def abort(self, code, details):
        self.code = code
        self.details = details
        raise _Aborted()


def test_get_cold_start_recommendations_returns_mapped_results(monkeypatch):
    fake_movies = [
        {
            "id": 1,
            "title": "Stub Movie",
            "poster_url": "http://example.com/p.jpg",
            "average_rating": 8.5,
            "genres": ["Action", "Comedy"],
            "match_score": 0.75,
        }
    ]
    calls = []

    def fake_get_cold_start_recommendations(user_id, limit):
        calls.append((user_id, limit))
        return fake_movies

    monkeypatch.setattr(grpc_server, "get_cold_start_recommendations", fake_get_cold_start_recommendations)

    servicer = grpc_server.RecommendationServicer()
    request = recommendation_pb2.ColdStartRequest(user_id=7, limit=5)

    response = servicer.GetColdStartRecommendations(request, _FakeContext())

    assert calls == [(7, 5)]
    assert len(response.recommendations) == 1
    rec = response.recommendations[0]
    assert rec.id == 1
    assert rec.title == "Stub Movie"
    assert rec.poster_url == "http://example.com/p.jpg"
    assert rec.average_rating == 8.5
    assert list(rec.genres) == ["Action", "Comedy"]
    assert rec.match_score == 0.75


@pytest.mark.parametrize("limit", [0, 51])
def test_rejects_limit_outside_one_to_fifty(limit):
    servicer = grpc_server.RecommendationServicer()
    request = recommendation_pb2.ColdStartRequest(user_id=7, limit=limit)
    context = _FakeContext()

    with pytest.raises(_Aborted):
        servicer.GetColdStartRecommendations(request, context)

    assert context.code == grpc.StatusCode.INVALID_ARGUMENT
