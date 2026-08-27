"""One-time ingest: reads MovieLens ml-latest-small's ratings.csv + links.csv, translates
MovieLens movieId -> postgres movies.id (via tmdb_id), and writes the result as a single Parquet
object to MinIO. This is the only place that ever reads the raw CSV files -- everything else
(models/svd_training.py) reads the translated dataset back from MinIO.

Rerun this manually (recommendation/scripts/build_movielens_dataset.py) whenever the source CSVs
change, or if the postgres movies table is re-seeded in a way that changes ids.
"""

import csv
import io
import os

import pandas as pd
from minio import Minio

from db import get_cursor

ML_DATASET_DIR = os.environ.get("ML_DATASET_DIR", r"C:\Users\zhepi\Downloads\ml-latest-small\ml-latest-small")
ML_RATINGS_CSV = os.path.join(ML_DATASET_DIR, "ratings.csv")
ML_LINKS_CSV = os.path.join(ML_DATASET_DIR, "links.csv")

MINIO_ENDPOINT = os.environ.get("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.environ.get("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.environ.get("MINIO_SECRET_KEY", "minioadmin")
MINIO_SECURE = os.environ.get("MINIO_SECURE", "false").lower() == "true"
MINIO_ML_BUCKET = os.environ.get("MINIO_ML_BUCKET", "ml-training-data")
MINIO_ML_OBJECT_KEY = os.environ.get("MINIO_ML_OBJECT_KEY", "movielens/ml-latest-small/ratings.parquet")

RATING_SCALE = (1, 5)

_MOVIE_TMDB_IDS_SQL = "SELECT id, tmdb_id FROM movies WHERE tmdb_id IS NOT NULL"


def round_ml_rating(raw: float) -> int:
    """MovieLens ratings are 0.5-5.0 in half-point steps; our scale is integer 1-5. Python's
    round() is already half-even (banker's rounding) for exact .5 values, which is what we want
    for 1.5/2.5/3.5/4.5. The one edge case is 0.5, which rounds down to 0 and falls outside the
    scale -- clamped up to 1."""
    return max(round(raw), RATING_SCALE[0])


def load_tmdb_to_movie_id() -> dict[str, int]:
    with get_cursor() as cursor:
        cursor.execute(_MOVIE_TMDB_IDS_SQL)
        rows = cursor.fetchall()
    return {str(r["tmdb_id"]): r["id"] for r in rows}


def load_ml_movie_to_tmdb() -> dict[str, str]:
    mapping = {}
    with open(ML_LINKS_CSV, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_id = row["tmdbId"].strip()
            if tmdb_id:
                mapping[row["movieId"]] = tmdb_id
    return mapping


def build_translated_ratings(
    ml_movie_to_tmdb: dict[str, str], tmdb_to_movie_id: dict[str, int]
) -> tuple[pd.DataFrame, int]:
    """Returns (dataframe, skipped_count). Rows are skipped when the MovieLens movie has no
    tmdbId, or that tmdbId doesn't resolve to a postgres movie (dead/merged TMDb ids)."""
    rows = []
    skipped = 0
    with open(ML_RATINGS_CSV, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_id = ml_movie_to_tmdb.get(row["movieId"])
            movie_id = tmdb_to_movie_id.get(tmdb_id) if tmdb_id else None
            if movie_id is None:
                skipped += 1
                continue
            rows.append(
                (int(row["userId"]), movie_id, round_ml_rating(float(row["rating"])))
            )
    df = pd.DataFrame(rows, columns=["ml_user_id", "movie_id", "rating"])
    return df, skipped


def get_minio_client() -> Minio:
    return Minio(
        MINIO_ENDPOINT,
        access_key=MINIO_ACCESS_KEY,
        secret_key=MINIO_SECRET_KEY,
        secure=MINIO_SECURE,
    )


def ensure_bucket(client: Minio, bucket: str) -> None:
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)


def upload_dataframe(df: pd.DataFrame) -> None:
    buffer = io.BytesIO()
    df.to_parquet(buffer, engine="pyarrow", index=False)
    size = buffer.tell()
    buffer.seek(0)

    client = get_minio_client()
    ensure_bucket(client, MINIO_ML_BUCKET)
    client.put_object(
        MINIO_ML_BUCKET,
        MINIO_ML_OBJECT_KEY,
        data=buffer,
        length=size,
        content_type="application/octet-stream",
    )


def fetch_ratings_dataframe() -> pd.DataFrame:
    client = get_minio_client()
    response = client.get_object(MINIO_ML_BUCKET, MINIO_ML_OBJECT_KEY)
    try:
        buffer = io.BytesIO(response.read())
    finally:
        response.close()
        response.release_conn()
    return pd.read_parquet(buffer, engine="pyarrow")


def run() -> None:
    print("Loading postgres tmdb_id -> movie_id map...")
    tmdb_to_movie_id = load_tmdb_to_movie_id()
    print(f"  {len(tmdb_to_movie_id)} movies in postgres with a tmdb_id")

    print("Reading & translating MovieLens ratings...")
    ml_movie_to_tmdb = load_ml_movie_to_tmdb()
    df, skipped = build_translated_ratings(ml_movie_to_tmdb, tmdb_to_movie_id)
    print(f"  {len(df)} ratings translated, {skipped} skipped (no matching postgres movie)")

    print(f"Uploading to MinIO ({MINIO_ML_BUCKET}/{MINIO_ML_OBJECT_KEY})...")
    upload_dataframe(df)
    print("Done.")
