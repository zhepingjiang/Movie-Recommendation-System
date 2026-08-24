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

_MOVIES_WITH_GENRES_SQL = """
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
    GROUP BY m.id
"""


def _jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    intersection = len(a & b)
    union = len(a | b)
    return intersection / union if union else 0.0


def get_cold_start_recommendations(user_id: int, limit: int = 10) -> list[dict]:
    with get_cursor() as cursor:
        cursor.execute(_USER_GENRES_SQL, (user_id,))
        user_genres = {row["name"] for row in cursor.fetchall()}

        cursor.execute(_MOVIES_WITH_GENRES_SQL)
        movies = cursor.fetchall()

    scored = []
    for movie in movies:
        movie_genres = set(movie["genres"])
        match_score = _jaccard(user_genres, movie_genres) if user_genres else 0.0
        scored.append(
            {
                "id": movie["id"],
                "title": movie["title"],
                "poster_url": movie["poster_url"],
                "average_rating": float(movie["average_rating"]),
                "genres": sorted(movie_genres),
                "match_score": round(match_score, 4),
            }
        )

    scored.sort(key=lambda m: (m["match_score"], m["average_rating"]), reverse=True)
    return scored[:limit]
