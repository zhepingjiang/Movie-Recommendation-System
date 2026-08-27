"""Manual entry point for the one-time MovieLens ingest job. See models/movielens_ingest.py for
the actual logic. Run from the recommendation/ directory so imports resolve:

    venv\\Scripts\\python.exe scripts\\build_movielens_dataset.py

Requires the postgres and minio containers from docker-compose.yml to be up, and ML_DATASET_DIR
(default: C:\\Users\\zhepi\\Downloads\\ml-latest-small\\ml-latest-small) to point at
ml-latest-small's ratings.csv and links.csv. Rerun whenever those source files change, or the
postgres movies table is re-seeded in a way that changes ids.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from models.movielens_ingest import run

if __name__ == "__main__":
    run()
