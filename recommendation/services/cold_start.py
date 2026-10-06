"""Cold-start recommendations for users with little or no interaction history yet.

Scores each movie by Jaccard overlap between the genres the user picked at onboarding
and the movie's genres, then breaks ties by average rating. Users with no genre picks
fall back to a plain top-rated list.
"""

from db import get_cursor

_USER_GENRES_SQL = """
    SELECT g.name AS name
    FROM user_genres ug
    JOIN genres g ON g.id = ug.genre_id
    WHERE ug.user_id = %s
"""

# Scores, ranks and cuts to `limit` inside Postgres, so only the rows that will be returned leave
# the database. match_score is the Jaccard overlap: matching genres / (user's genres + movie's
# genres - matching genres). Only movies sharing at least one genre with the user are candidates.
# The trailing m.id makes the order (and so which movies survive the LIMIT) deterministic on ties.
_TOP_GENRE_MATCHES_SQL = """
    WITH movie_genre_counts AS (
        SELECT
            mg.movie_id,
            COUNT(*) AS genre_count,
            COUNT(*) FILTER (WHERE g.name = ANY(%(user_genres)s::text[])) AS matching_genre_count
        FROM movie_genres mg
        JOIN genres g ON g.id = mg.genre_id
        GROUP BY mg.movie_id
    ),
    top_matches AS (
        SELECT
            m.id,
            m.title,
            m.poster_url,
            m.average_rating,
            m.rating_count,
            c.matching_genre_count::float8
                / (%(user_genre_count)s + c.genre_count - c.matching_genre_count) AS match_score
        FROM movie_genre_counts c
        JOIN movies m ON m.id = c.movie_id
        WHERE c.matching_genre_count > 0
        ORDER BY match_score DESC, m.average_rating DESC, m.id
        LIMIT %(limit)s
    )
    SELECT
        t.*,
        ARRAY(
            SELECT g.name
            FROM movie_genres mg
            JOIN genres g ON g.id = mg.genre_id
            WHERE mg.movie_id = t.id
        ) AS genres
    FROM top_matches t
    ORDER BY t.match_score DESC, t.average_rating DESC, t.id
"""

# Used both when the user picked no genres at all, and to pad results when their picks matched
# fewer than `limit` movies. Excluded ids are whatever's already been selected, so anything this
# returns is guaranteed to have zero genre overlap with the user's picks (match_score 0.0).
_TOP_RATED_EXCLUDING_SQL = """
    SELECT
        m.id,
        m.title,
        m.poster_url,
        m.average_rating,
        m.rating_count,
        COALESCE(array_agg(g.name) FILTER (WHERE g.name IS NOT NULL), '{}') AS genres
    FROM movies m
    LEFT JOIN movie_genres mg ON mg.movie_id = m.id
    LEFT JOIN genres g ON g.id = mg.genre_id
    WHERE NOT (m.id = ANY(%s::bigint[]))
    GROUP BY m.id
    ORDER BY m.average_rating DESC
    LIMIT %s
"""


def _to_scored(movie: dict, match_score: float) -> dict:
    return {
        "id": movie["id"],
        "title": movie["title"],
        "poster_url": movie["poster_url"],
        "average_rating": float(movie["average_rating"]),
        "genres": sorted(movie["genres"]),
        "match_score": round(match_score, 4),
    }


# TODO: rating_count is fetched but not used in scoring. It's meant to eventually support
# Bayesian-smoothed ranking (so a movie with 1 rating of 9.0 can't outrank one with 5,000
# ratings averaging 8.2), but every movie's rating_count is currently 0 -- nothing in this repo
# writes to it yet -- so applying that smoothing today would collapse every movie to the same
# score instead of fixing anything. Revisit once real in-app ratings start populating it.
def get_cold_start_recommendations(user_id: int, limit: int = 10) -> list[dict]:
    with get_cursor() as cursor:
        cursor.execute(_USER_GENRES_SQL, (user_id,))
        user_genres = {row["name"] for row in cursor.fetchall()}

        top = []
        if user_genres:
            # FIX: every movie sharing a genre with the user (5,949 of 9,730 for Action + Drama)
            # used to be fetched with its genres and scored and sorted here in Python, just to keep
            # `limit` of them -- most of the request's latency. The query now does the scoring,
            # ordering and LIMIT itself and returns only the top `limit` rows.
            cursor.execute(
                _TOP_GENRE_MATCHES_SQL,
                {"user_genres": sorted(user_genres), "user_genre_count": len(user_genres), "limit": limit},
            )
            top = [_to_scored(movie, movie["match_score"]) for movie in cursor.fetchall()]

        remaining = limit - len(top)
        if remaining > 0:
            exclude_ids = [m["id"] for m in top]
            cursor.execute(_TOP_RATED_EXCLUDING_SQL, (exclude_ids, remaining))
            top.extend(_to_scored(m, 0.0) for m in cursor.fetchall())

    return top
