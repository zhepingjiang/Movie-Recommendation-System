from fastapi.testclient import TestClient

import routes.recommendations as recommendations_route
from main import app

client = TestClient(app)


def test_ping():
    response = client.get("/ping")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_cold_start_returns_service_result(monkeypatch):
    fake_result = [{"id": 1, "title": "Stub Movie", "match_score": 1.0}]
    calls = []

    def fake_get_cold_start_recommendations(user_id, limit):
        calls.append((user_id, limit))
        return fake_result

    monkeypatch.setattr(recommendations_route, "get_cold_start_recommendations", fake_get_cold_start_recommendations)

    response = client.get("/recommendations/7/cold-start")

    assert response.status_code == 200
    assert response.json() == fake_result
    assert calls == [(7, 10)]  # default limit


def test_cold_start_passes_through_limit_query_param(monkeypatch):
    calls = []

    def fake_get_cold_start_recommendations(user_id, limit):
        calls.append((user_id, limit))
        return []

    monkeypatch.setattr(recommendations_route, "get_cold_start_recommendations", fake_get_cold_start_recommendations)

    response = client.get("/recommendations/7/cold-start?limit=3")

    assert response.status_code == 200
    assert calls == [(7, 3)]


def test_cold_start_rejects_non_integer_user_id():
    response = client.get("/recommendations/not-a-number/cold-start")

    assert response.status_code == 422
