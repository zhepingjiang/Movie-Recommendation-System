"""Manual entry point for the recommendation-blending job. See models/recommendation_blending.py
for the actual logic. Run from the recommendation/ directory so imports resolve (matches
pytest.ini's pythonpath = .), after both scripts/train_svd.py and scripts/train_content_based.py
have already been run:

    venv\\Scripts\\python.exe scripts\\blend_recommendations.py

Requires the postgres container from docker-compose.yml to be up. Reads only from Postgres
(recommendation_cache + ratings) -- no MinIO, no retraining of either model.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from models.recommendation_blending import run

if __name__ == "__main__":
    run()
