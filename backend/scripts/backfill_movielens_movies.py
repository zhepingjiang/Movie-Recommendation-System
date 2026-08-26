"""
One-off backfill: reads MovieLens ml-latest-small's movies.csv + links.csv,
fetches full metadata for each movie from the TMDb API (by tmdbId), and
writes a Flyway migration that inserts any movies not already in the
`movies` table (matched by tmdb_id). Genres are taken from TMDb's own
per-movie genre list (same source the original V3 seed used), not from
MovieLens's genre column.

Requires TMDB_API_KEY in the environment. Not run by the app; re-run
manually if the source CSVs change.
"""

import concurrent.futures
import csv
import os
import subprocess
import sys
import time

import requests

ML_DIR = r"C:\Users\zhepi\Downloads\ml-latest-small\ml-latest-small"
MOVIES_CSV = os.path.join(ML_DIR, "movies.csv")
LINKS_CSV = os.path.join(ML_DIR, "links.csv")

MIGRATION_DIR = os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "db", "migration"
)
OUTPUT_SQL = os.path.join(MIGRATION_DIR, "V10__backfill_movielens_movies.sql")

PG_CONTAINER = "movie-rec-system-postgres-1"
PG_USER = "movierec"
PG_DB = "movierec"

TMDB_API_KEY = os.environ.get("TMDB_API_KEY")
TMDB_BASE = "https://api.themoviedb.org/3/movie/{}"
MAX_WORKERS = 20
REQUEST_TIMEOUT = 10


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def sql_str_or_null(value):
    if value is None:
        return "NULL"
    value = str(value).strip()
    return "NULL" if not value else sql_str(value)


def psql_query(sql: str) -> list[str]:
    result = subprocess.run(
        ["docker", "exec", PG_CONTAINER, "psql", "-U", PG_USER, "-d", PG_DB, "-t", "-A", "-c", sql],
        capture_output=True,
        text=True,
        check=True,
    )
    return [line for line in result.stdout.splitlines() if line.strip()]


def load_existing_tmdb_ids() -> set[str]:
    return set(psql_query("SELECT tmdb_id FROM movies WHERE tmdb_id IS NOT NULL"))


def load_existing_genres() -> set[str]:
    return set(psql_query("SELECT name FROM genres"))


def load_ml_tmdb_ids() -> dict[str, str]:
    tmdb_by_movie_id = {}
    with open(LINKS_CSV, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_id = row["tmdbId"].strip()
            if tmdb_id:
                tmdb_by_movie_id[row["movieId"]] = tmdb_id
    return tmdb_by_movie_id


def fetch_movie(tmdb_id: str, session: requests.Session) -> dict | None:
    url = TMDB_BASE.format(tmdb_id)
    params = {"api_key": TMDB_API_KEY, "language": "en-US"}
    for attempt in range(4):
        try:
            resp = session.get(url, params=params, timeout=REQUEST_TIMEOUT)
        except requests.RequestException:
            time.sleep(1 + attempt)
            continue

        if resp.status_code == 200:
            return resp.json()
        if resp.status_code == 404:
            return None
        if resp.status_code == 429:
            wait = float(resp.headers.get("Retry-After", 1))
            time.sleep(wait)
            continue
        time.sleep(1 + attempt)

    return None


def main():
    if not TMDB_API_KEY:
        print("ERROR: set TMDB_API_KEY in the environment before running.", file=sys.stderr)
        sys.exit(1)

    existing_tmdb_ids = load_existing_tmdb_ids()
    existing_genres = load_existing_genres()
    print(f"Already in postgres: {len(existing_tmdb_ids)} movies, {len(existing_genres)} genres")

    ml_tmdb_ids = load_ml_tmdb_ids()
    to_fetch = sorted(set(ml_tmdb_ids.values()) - existing_tmdb_ids, key=int)
    print(f"MovieLens movies with a tmdbId: {len(ml_tmdb_ids)}")
    print(f"To fetch from TMDb (not already in postgres): {len(to_fetch)}")

    fetched = []
    not_found = 0
    failed = 0
    done = 0
    start = time.time()

    session = requests.Session()
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        future_to_id = {pool.submit(fetch_movie, tid, session): tid for tid in to_fetch}
        for future in concurrent.futures.as_completed(future_to_id):
            tid = future_to_id[future]
            data = future.result()
            done += 1
            if data is None:
                not_found += 1
            elif "title" not in data:
                failed += 1
            else:
                fetched.append(data)
            if done % 500 == 0 or done == len(to_fetch):
                elapsed = time.time() - start
                print(f"  {done}/{len(to_fetch)} done ({elapsed:.0f}s) - fetched={len(fetched)} not_found={not_found} failed={failed}")

    print(f"Fetched {len(fetched)} movies from TMDb (not_found={not_found}, failed={failed})")

    new_genre_names = set()
    for m in fetched:
        for g in m.get("genres", []):
            name = g.get("name", "").strip()
            if name and name not in existing_genres:
                new_genre_names.add(name)

    lines = []
    lines.append("-- ============================================================")
    lines.append("-- Backfill: MovieLens ml-latest-small movies, matched to postgres")
    lines.append("-- via tmdb_id (see links.csv), metadata fetched from the TMDb API.")
    lines.append("-- Generated by backend/scripts/backfill_movielens_movies.py.")
    lines.append("-- Do not hand-edit; regenerate instead.")
    lines.append("-- ============================================================")
    lines.append("")

    if new_genre_names:
        lines.append("-- New genres (from TMDb's own classification, not seen in the existing seed)")
        for genre in sorted(new_genre_names):
            lines.append(
                f"INSERT INTO genres (name, is_active) VALUES ({sql_str(genre)}, true) "
                "ON CONFLICT (name) DO NOTHING;"
            )
        lines.append("")

    lines.append("-- Movies")
    for m in fetched:
        tmdb_id = m["id"]
        title = m.get("title") or m.get("original_title") or ""
        if not title:
            continue
        overview = m.get("overview") or ""
        release_date = m.get("release_date") or ""
        vote_average = m.get("vote_average")
        vote_average_sql = f"{vote_average:.2f}" if isinstance(vote_average, (int, float)) else "0"
        poster_path = m.get("poster_path")
        poster_url = f"https://image.tmdb.org/t/p/w500{poster_path}" if poster_path else None

        lines.append(
            "INSERT INTO movies (tmdb_id, title, description, release_date, average_rating, poster_url) "
            f"VALUES ({tmdb_id}, {sql_str(title)}, {sql_str_or_null(overview)}, "
            f"{sql_str_or_null(release_date)}, {vote_average_sql}, {sql_str_or_null(poster_url)}) "
            "ON CONFLICT (tmdb_id) DO NOTHING;"
        )
    lines.append("")

    lines.append("-- Movie <-> Genre links")
    for m in fetched:
        tmdb_id = m["id"]
        for g in m.get("genres", []):
            name = g.get("name", "").strip()
            if not name:
                continue
            lines.append(
                "INSERT INTO movie_genres (movie_id, genre_id) "
                f"SELECT mv.id, ge.id FROM movies mv, genres ge "
                f"WHERE mv.tmdb_id = {tmdb_id} AND ge.name = {sql_str(name)} "
                "ON CONFLICT (movie_id, genre_id) DO NOTHING;"
            )

    os.makedirs(MIGRATION_DIR, exist_ok=True)
    with open(OUTPUT_SQL, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print(f"Wrote {len(fetched)} movies, {len(new_genre_names)} new genres -> {OUTPUT_SQL}")


if __name__ == "__main__":
    main()
