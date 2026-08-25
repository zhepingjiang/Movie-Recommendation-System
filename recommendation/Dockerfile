# Build context is the repo root (see docker-compose.yml) so this can reach the shared
# proto/ directory -- the gRPC stubs under generated/ are derived from it at build time
# rather than committed (see scripts/generate_proto.sh).
FROM python:3.12-slim
WORKDIR /app
COPY recommendation/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt grpcio-tools
COPY proto ../proto
COPY recommendation .
RUN mkdir -p generated \
    && touch generated/__init__.py \
    && python -m grpc_tools.protoc \
         -I ../proto \
         --python_out=generated \
         --grpc_python_out=generated \
         --pyi_out=generated \
         ../proto/recommendation.proto \
    && sed -i 's/^import recommendation_pb2 as recommendation__pb2$/from . import recommendation_pb2 as recommendation__pb2/' \
         generated/recommendation_pb2_grpc.py \
    && pip uninstall --yes grpcio-tools
EXPOSE 8000 50051
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
