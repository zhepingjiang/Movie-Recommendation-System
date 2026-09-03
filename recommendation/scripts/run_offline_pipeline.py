"""Manual entry point for the full offline pipeline (SVD training -> content-based training ->
recommendation blending), in that order. See models/offline_pipeline.py for why the order matters.
This is also the intended single entry point for a Kubernetes CronJob, once one exists -- run from
the recommendation/ directory so imports resolve (matches pytest.ini's pythonpath = .):

    venv\\Scripts\\python.exe scripts\\run_offline_pipeline.py

Requires the postgres and minio containers from docker-compose.yml to be up, and ML_DATASET_DIR
(see scripts/train_svd.py) for the MovieLens data SVD trains alongside real ratings.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from models.offline_pipeline import run

if __name__ == "__main__":
    run()
