"""Manual entry point for comparing SVD and content-based on the shared holdout split. See
evaluation/evaluate_models.py for the actual logic. Run from the recommendation/ directory so
imports resolve (matches pytest.ini's pythonpath = .):

    venv\\Scripts\\python.exe scripts\\evaluate_models.py

Requires the postgres and minio containers from docker-compose.yml to be up (same as the two
training jobs this evaluates) -- MovieLens's ratings are read pre-ingested from MinIO (see
models/movielens_ingest.py), not the raw CSVs, so no ML_DATASET_DIR is needed here.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from evaluation.evaluate_models import run

if __name__ == "__main__":
    run()
