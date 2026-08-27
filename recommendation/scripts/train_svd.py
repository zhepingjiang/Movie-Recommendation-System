"""Manual entry point for the offline SVD training job. See models/svd_training.py for the
actual logic. Run from the recommendation/ directory so imports resolve (matches pytest.ini's
pythonpath = .):

    venv\\Scripts\\python.exe scripts\\train_svd.py

Requires the postgres and redis containers from docker-compose.yml to be up, and
ML_DATASET_DIR (default: C:\\Users\\zhepi\\Downloads\\ml-latest-small\\ml-latest-small) to point
at ml-latest-small's ratings.csv and links.csv.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from models.svd_training import run

if __name__ == "__main__":
    run()
