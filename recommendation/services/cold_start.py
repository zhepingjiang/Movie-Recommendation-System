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

_CANDIDATE_MOVIE_IDS_SQL = """
    SELECT DISTINCT mg.movie_id AS movie_id
    FROM movie_genres mg
    JOIN genres g ON g.id = mg.genre_id
    WHERE g.name = ANY(%s::text[])
"""

_MOVIES_WITH_GENRES_BY_IDS_SQL = """
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
    WHERE m.id = ANY(%s::bigint[])
    GROUP BY m.id
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


def _jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    intersection = len(a & b)
    union = len(a | b)
    return intersection / union if union else 0.0


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

        candidate_movies = []
        if user_genres:
            cursor.execute(_CANDIDATE_MOVIE_IDS_SQL, (list(user_genres),))
            candidate_ids = [row["movie_id"] for row in cursor.fetchall()]
            if candidate_ids:
                cursor.execute(_MOVIES_WITH_GENRES_BY_IDS_SQL, (candidate_ids,))
                candidate_movies = cursor.fetchall()

        scored = [_to_scored(m, _jaccard(user_genres, set(m["genres"]))) for m in candidate_movies]
        scored.sort(key=lambda m: (m["match_score"], m["average_rating"]), reverse=True)
        top = scored[:limit]

        remaining = limit - len(top)
        if remaining > 0:
            exclude_ids = [m["id"] for m in top]
            cursor.execute(_TOP_RATED_EXCLUDING_SQL, (exclude_ids, remaining))
            top.extend(_to_scored(m, 0.0) for m in cursor.fetchall())

    return top
