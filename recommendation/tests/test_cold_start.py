from contextlib import contextmanager

import pytest

from services.cold_start import _jaccard, get_cold_start_recommendations


class FakeCursor:
    """Stands in for the psycopg2 dict-cursor: returns canned rows per execute() call, in order."""

    def __init__(self, results):
        self._results = results
        self._calls = []
        self._index = -1

    def execute(self, query, params=None):
        self._calls.append(params)
        self._index += 1

    def fetchall(self):
        return self._results[self._index]


def _patch_cursor(monkeypatch, results):
    fake_cursor = FakeCursor(results)

    @contextmanager
    def fake_get_cursor():
        yield fake_cursor

    monkeypatch.setattr("services.cold_start.get_cursor", fake_get_cursor)
    return fake_cursor


class TestJaccard:
    def test_both_empty(self):
        assert _jaccard(set(), set()) == 0.0

    def test_one_empty(self):
        assert _jaccard({"Action"}, set()) == 0.0
        assert _jaccard(set(), {"Action"}) == 0.0

    def test_disjoint_sets(self):
        assert _jaccard({"Action"}, {"Romance"}) == 0.0

    def test_full_overlap(self):
        assert _jaccard({"Action", "Adventure"}, {"Action", "Adventure"}) == 1.0

    def test_partial_overlap(self):
        # intersection={Adventure} (1), union={Action,Adventure,Comedy} (3)
        assert _jaccard({"Action", "Adventure"}, {"Adventure", "Comedy"}) == pytest.approx(1 / 3)


class TestGetColdStartRecommendations:
    def test_sorts_by_match_score_then_rating(self, monkeypatch):
        user_genres = [{"name": "Action"}, {"name": "Sci-Fi"}]
        movies = [
            {
                "id": 1,
                "title": "Partial Match High Rated",
                "poster_url": "p1",
                "average_rating": 9.0,
                "rating_count": 0,
                "genres": ["Action"],
            },
            {
                "id": 2,
                "title": "Full Match Lower Rated",
                "poster_url": "p2",
                "average_rating": 7.0,
                "rating_count": 0,
                "genres": ["Action", "Sci-Fi"],
            },
            {
                "id": 3,
                "title": "No Match",
                "poster_url": "p3",
                "average_rating": 9.9,
                "rating_count": 0,
                "genres": ["Romance"],
            },
        ]
        _patch_cursor(monkeypatch, [user_genres, movies])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        # full-overlap movie ranks first despite the lower rating; genre match beats popularity
        assert [m["id"] for m in result] == [2, 1, 3]
        assert result[0]["match_score"] == 1.0
        assert result[1]["match_score"] == pytest.approx(0.5)
        assert result[2]["match_score"] == 0.0

    def test_respects_limit(self, monkeypatch):
        user_genres = [{"name": "Action"}]
        movies = [
            {"id": i, "title": f"Movie {i}", "poster_url": None, "average_rating": float(i), "rating_count": 0, "genres": ["Action"]}
            for i in range(1, 6)
        ]
        _patch_cursor(monkeypatch, [user_genres, movies])

        result = get_cold_start_recommendations(user_id=1, limit=2)

        assert len(result) == 2
        # highest-rated Action movies first
        assert [m["id"] for m in result] == [5, 4]

    def test_no_genre_picks_falls_back_to_top_rated(self, monkeypatch):
        movies = [
            {"id": 1, "title": "Low", "poster_url": None, "average_rating": 5.0, "rating_count": 0, "genres": ["Horror"]},
            {"id": 2, "title": "High", "poster_url": None, "average_rating": 9.0, "rating_count": 0, "genres": ["Drama"]},
        ]
        _patch_cursor(monkeypatch, [[], movies])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        assert all(m["match_score"] == 0.0 for m in result)
        assert [m["id"] for m in result] == [2, 1]

    def test_movie_with_no_genres_scores_zero(self, monkeypatch):
        user_genres = [{"name": "Action"}]
        movies = [
            {"id": 1, "title": "Genreless", "poster_url": None, "average_rating": 8.0, "rating_count": 0, "genres": []},
        ]
        _patch_cursor(monkeypatch, [user_genres, movies])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        assert result[0]["match_score"] == 0.0
        assert result[0]["genres"] == []

    def test_genres_returned_sorted(self, monkeypatch):
        user_genres = [{"name": "Action"}]
        movies = [
            {
                "id": 1,
                "title": "Movie",
                "poster_url": None,
                "average_rating": 8.0,
                "rating_count": 0,
                "genres": ["Sci-Fi", "Action", "Comedy"],
            },
        ]
        _patch_cursor(monkeypatch, [user_genres, movies])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        assert result[0]["genres"] == ["Action", "Comedy", "Sci-Fi"]

    def test_queries_scoped_to_requested_user(self, monkeypatch):
        fake_cursor = _patch_cursor(monkeypatch, [[], []])

        get_cold_start_recommendations(user_id=42, limit=10)

        assert fake_cursor._calls[0] == (42,)
