#!/usr/bin/env bash
# Regenerates generated/recommendation_pb2.py and generated/recommendation_pb2_grpc.py from
# ../../proto/recommendation.proto. Run this from the recommendation/ directory (with the venv's
# grpcio-tools installed -- it's in requirements-dev.txt) whenever the .proto changes, or after a
# fresh checkout: generated/ is gitignored (it's derived from the .proto, not hand-written) so it
# won't exist yet. The Docker image runs this same generation step at build time.
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p generated
touch generated/__init__.py

python -m grpc_tools.protoc \
  -I ../proto \
  --python_out=generated \
  --grpc_python_out=generated \
  --pyi_out=generated \
  ../proto/recommendation.proto

# grpc_tools.protoc emits a plain top-level import ("import recommendation_pb2") that only
# resolves when generated/ is on sys.path directly, not when it's imported as the "generated"
# package. Rewrite it to a relative import so `from generated import recommendation_pb2_grpc`
# works the same way the rest of this codebase imports things.
sed -i 's/^import recommendation_pb2 as recommendation__pb2$/from . import recommendation_pb2 as recommendation__pb2/' \
  generated/recommendation_pb2_grpc.py
