"""Manual entry point for the offline content-based similarity job. See
models/content_based_training.py for the actual logic. Run from the recommendation/ directory so
imports resolve (matches pytest.ini's pythonpath = .):

    venv\\Scripts\\python.exe scripts\\train_content_based.py

Requires the postgres container from docker-compose.yml to be up. Unlike train_svd.py, this needs
no ML_DATASET_DIR / MinIO-hosted ratings -- it only reads movies/genres, already in Postgres.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from models.content_based_training import run

if __name__ == "__main__":
    run()
