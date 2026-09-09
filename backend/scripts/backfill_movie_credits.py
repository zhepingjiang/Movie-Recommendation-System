"""
One-off backfill: for every movie already in postgres with a tmdb_id, fetches
/movie/{tmdb_id}/credits from the TMDb API and writes a Flyway migration that
inserts the director + top-billed cast (movie_people, movie_cast, movies.director_id).

Requires TMDB_API_KEY in the environment and the V12__add_movie_credits.sql
migration (movie_people / movie_cast / movies.director_id) already applied.
Not run by the app; re-run manually if new movies are added.
"""

import concurrent.futures
import os
import subprocess
import sys
import time

import requests

MIGRATION_DIR = os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "db", "migration"
)
OUTPUT_SQL = os.path.join(MIGRATION_DIR, "V13__backfill_movie_credits.sql")

PG_CONTAINER = "movie-rec-system-postgres-1"
PG_USER = "movierec"
PG_DB = "movierec"

TMDB_API_KEY = os.environ.get("TMDB_API_KEY")
TMDB_BASE = "https://api.themoviedb.org/3/movie/{}/credits"
MAX_WORKERS = 20
REQUEST_TIMEOUT = 10
TOP_BILLED_CAST_COUNT = 5


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def psql_query(sql: str) -> list[str]:
    result = subprocess.run(
        ["docker", "exec", PG_CONTAINER, "psql", "-U", PG_USER, "-d", PG_DB, "-t", "-A", "-c", sql],
        capture_output=True,
        text=True,
        check=True,
    )
    return [line for line in result.stdout.splitlines() if line.strip()]


def load_movie_tmdb_ids() -> dict[str, str]:
    """Returns {tmdb_id: movie_id} for every movie already in postgres with a tmdb_id."""
    rows = psql_query("SELECT tmdb_id, id FROM movies WHERE tmdb_id IS NOT NULL")
    mapping = {}
    for row in rows:
        tmdb_id, movie_id = row.split("|")
        mapping[tmdb_id] = movie_id
    return mapping


def fetch_credits(tmdb_id: str, session: requests.Session) -> dict | None:
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

    tmdb_to_movie_id = load_movie_tmdb_ids()
    print(f"Movies in postgres with a tmdb_id: {len(tmdb_to_movie_id)}")

    fetched: dict[str, dict] = {}
    not_found = 0
    failed = 0
    done = 0
    start = time.time()

    session = requests.Session()
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        future_to_tmdb_id = {
            pool.submit(fetch_credits, tmdb_id, session): tmdb_id for tmdb_id in tmdb_to_movie_id
        }
        for future in concurrent.futures.as_completed(future_to_tmdb_id):
            tmdb_id = future_to_tmdb_id[future]
            data = future.result()
            done += 1
            if data is None:
                not_found += 1
            elif "cast" not in data or "crew" not in data:
                failed += 1
            else:
                fetched[tmdb_id] = data
            if done % 500 == 0 or done == len(tmdb_to_movie_id):
                elapsed = time.time() - start
                print(
                    f"  {done}/{len(tmdb_to_movie_id)} done ({elapsed:.0f}s) - "
                    f"fetched={len(fetched)} not_found={not_found} failed={failed}"
                )

    print(f"Fetched credits for {len(fetched)} movies (not_found={not_found}, failed={failed})")

    # Collect every distinct TMDb person (across directors and top-billed cast), keyed by
    # their tmdb person id, so each one gets a single movie_people row regardless of how
    # many movies they appear in.
    people_by_tmdb_id: dict[int, str] = {}
    directors_by_tmdb_movie_id: dict[str, int] = {}
    cast_by_tmdb_movie_id: dict[str, list[tuple[int, int]]] = {}

    for tmdb_id, credits_data in fetched.items():
        director = next(
            (member for member in credits_data["crew"] if member.get("job") == "Director"),
            None,
        )
        if director:
            people_by_tmdb_id[director["id"]] = director["name"]
            directors_by_tmdb_movie_id[tmdb_id] = director["id"]

        top_cast = sorted(credits_data["cast"], key=lambda member: member.get("order", 999))[
            :TOP_BILLED_CAST_COUNT
        ]
        cast_entries = []
        for cast_member in top_cast:
            people_by_tmdb_id[cast_member["id"]] = cast_member["name"]
            cast_entries.append((cast_member["id"], cast_member.get("order", 999)))
        if cast_entries:
            cast_by_tmdb_movie_id[tmdb_id] = cast_entries

    lines = []
    lines.append("-- ============================================================")
    lines.append("-- Backfill: director + top-billed cast for existing movies,")
    lines.append("-- fetched from the TMDb credits API.")
    lines.append("-- Generated by backend/scripts/backfill_movie_credits.py.")
    lines.append("-- Do not hand-edit; regenerate instead.")
    lines.append("-- ============================================================")
    lines.append("")

    lines.append("-- People (actors & directors), deduped by tmdb_person_id")
    for tmdb_person_id, name in sorted(people_by_tmdb_id.items()):
        lines.append(
            "INSERT INTO movie_people (tmdb_person_id, name) "
            f"VALUES ({tmdb_person_id}, {sql_str(name)}) "
            "ON CONFLICT (tmdb_person_id) DO NOTHING;"
        )
    lines.append("")

    lines.append("-- Directors")
    for tmdb_id, director_tmdb_person_id in sorted(directors_by_tmdb_movie_id.items(), key=lambda kv: int(kv[0])):
        lines.append(
            "UPDATE movies SET director_id = "
            f"(SELECT id FROM movie_people WHERE tmdb_person_id = {director_tmdb_person_id}) "
            f"WHERE tmdb_id = {tmdb_id};"
        )
    lines.append("")

    lines.append("-- Top-billed cast")
    for tmdb_id, cast_entries in sorted(cast_by_tmdb_movie_id.items(), key=lambda kv: int(kv[0])):
        for cast_tmdb_person_id, cast_order in cast_entries:
            lines.append(
                "INSERT INTO movie_cast (movie_id, person_id, cast_order) "
                f"SELECT mv.id, mp.id, {cast_order} FROM movies mv, movie_people mp "
                f"WHERE mv.tmdb_id = {tmdb_id} AND mp.tmdb_person_id = {cast_tmdb_person_id} "
                "ON CONFLICT (movie_id, person_id) DO NOTHING;"
            )

    os.makedirs(MIGRATION_DIR, exist_ok=True)
    with open(OUTPUT_SQL, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print(
        f"Wrote {len(people_by_tmdb_id)} people, {len(directors_by_tmdb_movie_id)} directors, "
        f"{sum(len(v) for v in cast_by_tmdb_movie_id.values())} cast links -> {OUTPUT_SQL}"
    )


if __name__ == "__main__":
    main()
