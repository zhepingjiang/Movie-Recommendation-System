"""
One-off generator: reads the raw TMDb export at repo root (movies.csv) and
writes a Flyway seed migration (V3__seed_movies.sql). Not run by the app;
re-run manually and commit the output if the source CSV changes.
"""

import csv
import os

REPO_ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
SOURCE_CSV = os.path.join(REPO_ROOT, "movies.csv")
OUTPUT_SQL = os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "db", "migration", "V3__seed_movies.sql"
)


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def sql_str_or_null(value: str) -> str:
    value = value.strip()
    return "NULL" if not value else sql_str(value)


def main():
    seen_tmdb_ids = set()
    movies = []
    genre_set = set()

    with open(SOURCE_CSV, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            tmdb_id = row["tmdb_id"].strip()
            overview = row["overview"].strip()
            release_date = row["release_date"].strip()
            genres_raw = row["genres"].strip()

            if not overview and not release_date and not genres_raw:
                continue
            if tmdb_id in seen_tmdb_ids:
                continue
            seen_tmdb_ids.add(tmdb_id)

            genres = [g.strip() for g in genres_raw.split("|") if g.strip()]
            genre_set.update(genres)

            movies.append(
                {
                    "tmdb_id": tmdb_id,
                    "title": row["title"].strip(),
                    "overview": overview,
                    "release_date": release_date,
                    "vote_average": row["vote_average"].strip() or "0",
                    "poster_url": row["poster_url"].strip(),
                    "genres": genres,
                }
            )

    lines = []
    lines.append("-- ============================================================")
    lines.append("-- Seed data generated from movies.csv (TMDb export) via")
    lines.append("-- backend/scripts/generate_movie_seed.py. Do not hand-edit;")
    lines.append("-- regenerate instead.")
    lines.append("-- ============================================================")
    lines.append("")

    lines.append("-- Genres")
    for genre in sorted(genre_set):
        lines.append(
            f"INSERT INTO genres (name, is_active) VALUES ({sql_str(genre)}, true) "
            "ON CONFLICT (name) DO NOTHING;"
        )
    lines.append("")

    lines.append("-- Movies")
    for m in movies:
        lines.append(
            "INSERT INTO movies (tmdb_id, title, description, release_date, average_rating, poster_url) "
            f"VALUES ({m['tmdb_id']}, {sql_str(m['title'])}, {sql_str_or_null(m['overview'])}, "
            f"{sql_str_or_null(m['release_date'])}, {m['vote_average']}, {sql_str_or_null(m['poster_url'])}) "
            "ON CONFLICT (tmdb_id) DO NOTHING;"
        )
    lines.append("")

    lines.append("-- Movie <-> Genre links")
    for m in movies:
        for genre in m["genres"]:
            lines.append(
                "INSERT INTO movie_genres (movie_id, genre_id) "
                f"SELECT mv.id, g.id FROM movies mv, genres g "
                f"WHERE mv.tmdb_id = {m['tmdb_id']} AND g.name = {sql_str(genre)} "
                "ON CONFLICT (movie_id, genre_id) DO NOTHING;"
            )

    os.makedirs(os.path.dirname(OUTPUT_SQL), exist_ok=True)
    with open(OUTPUT_SQL, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print(f"movies={len(movies)} genres={len(genre_set)} -> {OUTPUT_SQL}")


if __name__ == "__main__":
    main()
