import importlib
from unittest.mock import MagicMock, call

import psycopg2.extras
import pytest

import db


def test_get_cursor_connects_with_configured_params(monkeypatch):
    mock_conn = MagicMock()
    mock_connect = MagicMock(return_value=mock_conn)
    monkeypatch.setattr(db.psycopg2, "connect", mock_connect)

    with db.get_cursor() as cursor:
        assert cursor is mock_conn.cursor.return_value.__enter__.return_value

    mock_connect.assert_called_once_with(
        host=db.DB_HOST, port=db.DB_PORT, dbname=db.DB_NAME, user=db.DB_USER, password=db.DB_PASSWORD
    )


def test_get_cursor_uses_real_dict_cursor(monkeypatch):
    mock_conn = MagicMock()
    monkeypatch.setattr(db.psycopg2, "connect", MagicMock(return_value=mock_conn))

    with db.get_cursor():
        pass

    assert mock_conn.cursor.call_args == call(cursor_factory=psycopg2.extras.RealDictCursor)


def test_get_cursor_commits_on_success(monkeypatch):
    mock_conn = MagicMock()
    monkeypatch.setattr(db.psycopg2, "connect", MagicMock(return_value=mock_conn))

    with db.get_cursor():
        pass

    mock_conn.commit.assert_called_once()
    mock_conn.close.assert_called_once()


def test_get_cursor_closes_without_committing_on_error(monkeypatch):
    mock_conn = MagicMock()
    monkeypatch.setattr(db.psycopg2, "connect", MagicMock(return_value=mock_conn))

    with pytest.raises(ValueError):
        with db.get_cursor():
            raise ValueError("boom")

    mock_conn.commit.assert_not_called()
    mock_conn.close.assert_called_once()


def test_defaults_are_read_from_environment(monkeypatch):
    monkeypatch.setenv("DB_HOST", "reco-test-host")
    monkeypatch.setenv("DB_PORT", "6543")
    monkeypatch.setenv("DB_NAME", "test_db")
    monkeypatch.setenv("DB_USER", "test_user")
    monkeypatch.setenv("DB_PASSWORD", "test_pass")

    reloaded = importlib.reload(db)
    try:
        assert reloaded.DB_HOST == "reco-test-host"
        assert reloaded.DB_PORT == "6543"
        assert reloaded.DB_NAME == "test_db"
        assert reloaded.DB_USER == "test_user"
        assert reloaded.DB_PASSWORD == "test_pass"
    finally:
        monkeypatch.undo()
        importlib.reload(db)
