import grpc
import pytest
from fastapi.testclient import TestClient

from main import app


def test_ping():
    with TestClient(app) as client:
        response = client.get("/ping")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_lifespan_starts_and_stops_the_grpc_server():
    # TestClient only runs FastAPI's lifespan (startup/shutdown) when used as a context
    # manager -- a bare `TestClient(app)` does not, which is why this needs its own test
    # rather than piggybacking on test_ping.
    with TestClient(app):
        grpc.channel_ready_future(grpc.insecure_channel("localhost:50051")).result(timeout=2)

    with pytest.raises(grpc.FutureTimeoutError):
        grpc.channel_ready_future(grpc.insecure_channel("localhost:50051")).result(timeout=1)
