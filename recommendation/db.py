"""Postgres connection helper, shared by every module that needs raw SQL access
to the same database the Spring Boot backend writes to."""

import os
from contextlib import contextmanager

import psycopg2
import psycopg2.extras

DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_PORT = os.environ.get("DB_PORT", "5432")
DB_NAME = os.environ.get("DB_NAME", "movierec")
DB_USER = os.environ.get("DB_USER", "movierec")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "movierec")


@contextmanager
def get_cursor():
    """Yields a dict-cursor on a short-lived connection, committing on success."""
    conn = psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD
    )
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
            yield cursor
        conn.commit()
    finally:
        conn.close()
