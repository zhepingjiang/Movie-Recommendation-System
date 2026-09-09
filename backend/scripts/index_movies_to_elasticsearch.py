"""
One-off (re)index: reads every movie from postgres -- title, overview, director, cast,
genres, plus display-only fields -- and bulk-indexes them into Elasticsearch's `movies`
index, dropping and recreating the index first so this is safe to rerun from scratch.

Requires ELASTICSEARCH_URL in the environment (or defaults to localhost:9200) and the
`elasticsearch` Python package (see backend/scripts/requirements.txt). Not run by the app;
rerun manually whenever movie/cast/director/genre data changes in postgres.
"""

import json
import os
import subprocess
import sys
import time

from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk

PG_CONTAINER = "movie-rec-system-postgres-1"
PG_USER = "movierec"
PG_DB = "movierec"

ELASTICSEARCH_URL = os.environ.get("ELASTICSEARCH_URL", "http://localhost:9200")
MOVIES_INDEX = "movies"

# Field names match the query boosts the search endpoint will use: title^10, director^4,
# cast^3, genres^2, overview^1. releaseDate/averageRating/posterUrl are stored for display
# only (not searched), so results can be rendered straight from the ES hit with no
# postgres round-trip.
MOVIES_MAPPING = {
    "mappings": {
        "properties": {
            "movieId": {"type": "keyword"},
            "title": {"type": "text"},
            "overview": {"type": "text"},
            "director": {"type": "text"},
            "cast": {"type": "text"},
            "genres": {
                "type": "text",
                "fields": {"keyword": {"type": "keyword"}},
            },
            "releaseDate": {"type": "date", "format": "yyyy-MM-dd"},
            "averageRating": {"type": "float"},
            "posterUrl": {"type": "keyword", "index": False},
        }
    }
}

_MOVIES_WITH_CREDITS_SQL = """
SELECT json_agg(row_to_json(t)) FROM (
  SELECT
    m.id AS movie_id,
    m.title,
    m.description AS overview,
    m.release_date,
    m.average_rating,
    m.poster_url,
    dp.name AS director,
    COALESCE(
      (SELECT array_agg(p.name ORDER BY mc.cast_order)
       FROM movie_cast mc JOIN movie_people p ON p.id = mc.person_id
       WHERE mc.movie_id = m.id),
      ARRAY[]::text[]
    ) AS cast,
    COALESCE(
      (SELECT array_agg(g.name ORDER BY g.name)
       FROM movie_genres mg JOIN genres g ON g.id = mg.genre_id
       WHERE mg.movie_id = m.id),
      ARRAY[]::text[]
    ) AS genres
  FROM movies m
  LEFT JOIN movie_people dp ON dp.id = m.director_id
) t;
"""


def load_movies_from_postgres() -> list[dict]:
    result = subprocess.run(
        ["docker", "exec", PG_CONTAINER, "psql", "-U", PG_USER, "-d", PG_DB, "-t", "-A", "-c", _MOVIES_WITH_CREDITS_SQL],
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=True,
    )
    return json.loads(result.stdout.strip())


def to_es_document(movie_row: dict) -> dict:
    return {
        "movieId": movie_row["movie_id"],
        "title": movie_row["title"],
        "overview": movie_row["overview"],
        "director": movie_row["director"],
        "cast": movie_row["cast"],
        "genres": movie_row["genres"],
        "releaseDate": movie_row["release_date"],
        "averageRating": movie_row["average_rating"],
        "posterUrl": movie_row["poster_url"],
    }


def main():
    print("Reading movies (+ director, cast, genres) from postgres...")
    movie_rows = load_movies_from_postgres()
    print(f"  {len(movie_rows)} movies loaded")

    es = Elasticsearch(ELASTICSEARCH_URL)

    if es.indices.exists(index=MOVIES_INDEX):
        print(f"Deleting existing '{MOVIES_INDEX}' index...")
        es.indices.delete(index=MOVIES_INDEX)
    print(f"Creating '{MOVIES_INDEX}' index...")
    es.indices.create(index=MOVIES_INDEX, body=MOVIES_MAPPING)

    actions = (
        {"_index": MOVIES_INDEX, "_id": movie_row["movie_id"], "_source": to_es_document(movie_row)}
        for movie_row in movie_rows
    )

    print("Bulk indexing...")
    start = time.time()
    success_count, errors = bulk(es, actions, raise_on_error=False)
    elapsed = time.time() - start

    if errors:
        print(f"ERROR: {len(errors)} documents failed to index:", file=sys.stderr)
        for error in errors[:10]:
            print(f"  {error}", file=sys.stderr)

    print(f"Indexed {success_count}/{len(movie_rows)} movies in {elapsed:.1f}s")


if __name__ == "__main__":
    main()
