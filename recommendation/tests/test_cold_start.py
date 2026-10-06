from contextlib import contextmanager

import pytest

from services.cold_start import get_cold_start_recommendations


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


class TestGetColdStartRecommendations:
    """Query order per call: user_genres always; then, only if the user picked genres, the top
    genre matches (scored, ordered and limited by the SQL itself, so fixtures carry match_score
    and are already in rank order); then, only if fewer than `limit` results have been assembled
    so far, a top-rated-excluding-what-we-have fallback."""

    def test_keeps_sql_rank_order_and_match_scores(self, monkeypatch):
        user_genres = [{"name": "Action"}, {"name": "Sci-Fi"}]
        top_matches = [
            {
                "id": 2,
                "title": "Full Match Lower Rated",
                "poster_url": "p2",
                "average_rating": 7.0,
                "rating_count": 0,
                "match_score": 1.0,
                "genres": ["Action", "Sci-Fi"],
            },
            {
                "id": 1,
                "title": "Partial Match High Rated",
                "poster_url": "p1",
                "average_rating": 9.0,
                "rating_count": 0,
                "match_score": 0.5,
                "genres": ["Action"],
            },
        ]
        # No genre overlap with Action/Sci-Fi, so it only ever appears via the fallback query.
        fallback_movies = [
            {
                "id": 3,
                "title": "No Match",
                "poster_url": "p3",
                "average_rating": 9.9,
                "rating_count": 0,
                "genres": ["Romance"],
            },
        ]
        _patch_cursor(monkeypatch, [user_genres, top_matches, fallback_movies])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        # genre matches stay in the query's order, ahead of the zero-overlap fallback movie
        assert [m["id"] for m in result] == [2, 1, 3]
        assert result[0]["match_score"] == 1.0
        assert result[1]["match_score"] == pytest.approx(0.5)
        assert result[2]["match_score"] == 0.0

    def test_match_score_rounded_to_four_decimals(self, monkeypatch):
        user_genres = [{"name": "Action"}, {"name": "Adventure"}]
        top_matches = [
            {
                "id": 1,
                "title": "Movie",
                "poster_url": None,
                "average_rating": 8.0,
                "rating_count": 0,
                "match_score": 1 / 3,
                "genres": ["Adventure", "Comedy"],
            },
        ]
        _patch_cursor(monkeypatch, [user_genres, top_matches, []])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        assert result[0]["match_score"] == 0.3333

    def test_passes_genres_and_limit_to_the_scoring_query(self, monkeypatch):
        user_genres = [{"name": "Sci-Fi"}, {"name": "Action"}]
        top_matches = [
            {"id": i, "title": f"Movie {i}", "poster_url": None, "average_rating": float(i), "rating_count": 0, "match_score": 0.5, "genres": ["Action"]}
            for i in (5, 4)
        ]
        fake_cursor = _patch_cursor(monkeypatch, [user_genres, top_matches])

        result = get_cold_start_recommendations(user_id=1, limit=2)

        assert fake_cursor._calls[1] == {"user_genres": ["Action", "Sci-Fi"], "user_genre_count": 2, "limit": 2}
        # the query returned a full `limit` of matches, so no fallback query is needed
        assert len(fake_cursor._calls) == 2
        assert [m["id"] for m in result] == [5, 4]

    def test_no_genre_picks_falls_back_to_top_rated(self, monkeypatch):
        # SQL does the ORDER BY average_rating DESC for the fallback path, so the fixture is
        # already in that order.
        fallback_movies = [
            {"id": 2, "title": "High", "poster_url": None, "average_rating": 9.0, "rating_count": 0, "genres": ["Drama"]},
            {"id": 1, "title": "Low", "poster_url": None, "average_rating": 5.0, "rating_count": 0, "genres": ["Horror"]},
        ]
        _patch_cursor(monkeypatch, [[], fallback_movies])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        assert all(m["match_score"] == 0.0 for m in result)
        assert [m["id"] for m in result] == [2, 1]

    def test_movie_with_no_genres_scores_zero(self, monkeypatch):
        # A genreless movie can never be a genre-overlap match, so it only surfaces here via
        # the fallback path (the scoring query returned nothing).
        user_genres = [{"name": "Action"}]
        top_matches = []
        fallback_movies = [
            {"id": 1, "title": "Genreless", "poster_url": None, "average_rating": 8.0, "rating_count": 0, "genres": []},
        ]
        _patch_cursor(monkeypatch, [user_genres, top_matches, fallback_movies])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        assert result[0]["match_score"] == 0.0
        assert result[0]["genres"] == []

    def test_genres_returned_sorted(self, monkeypatch):
        user_genres = [{"name": "Action"}]
        top_matches = [
            {
                "id": 1,
                "title": "Movie",
                "poster_url": None,
                "average_rating": 8.0,
                "rating_count": 0,
                "match_score": 1 / 3,
                "genres": ["Sci-Fi", "Action", "Comedy"],
            },
        ]
        _patch_cursor(monkeypatch, [user_genres, top_matches, []])

        result = get_cold_start_recommendations(user_id=1, limit=10)

        assert result[0]["genres"] == ["Action", "Comedy", "Sci-Fi"]

    def test_queries_scoped_to_requested_user(self, monkeypatch):
        fake_cursor = _patch_cursor(monkeypatch, [[], []])

        get_cold_start_recommendations(user_id=42, limit=10)

        assert fake_cursor._calls[0] == (42,)

    def test_matches_padded_with_fallback_when_short(self, monkeypatch):
        # Only one Action movie exists, but limit is 3: the fallback query should be called to
        # pad the remaining 2 slots, excluding the match already selected.
        user_genres = [{"name": "Action"}]
        top_matches = [
            {"id": 1, "title": "Action Movie", "poster_url": None, "average_rating": 6.0, "rating_count": 0, "match_score": 1.0, "genres": ["Action"]},
        ]
        fallback_movies = [
            {"id": 2, "title": "Filler A", "poster_url": None, "average_rating": 9.0, "rating_count": 0, "genres": ["Drama"]},
            {"id": 3, "title": "Filler B", "poster_url": None, "average_rating": 8.0, "rating_count": 0, "genres": ["Comedy"]},
        ]
        fake_cursor = _patch_cursor(monkeypatch, [user_genres, top_matches, fallback_movies])

        result = get_cold_start_recommendations(user_id=1, limit=3)

        assert [m["id"] for m in result] == [1, 2, 3]
        assert fake_cursor._calls[-1] == ([1], 2)  # exclude the already-picked match, need 2 more
