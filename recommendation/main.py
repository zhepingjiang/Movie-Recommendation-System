from contextlib import asynccontextmanager

from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from grpc_server import create_server
from telemetry import configure_logging, configure_tracing

# At import, not in lifespan: it must run exactly once per process, and lifespan runs once per
# TestClient in the tests.
configure_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Before create_server(): the gRPC server instrumentation has to hook grpc.server() first.
    tracer_provider = configure_tracing()
    server = create_server()
    server.start()
    yield
    server.stop(grace=5).wait()
    if tracer_provider is not None:
        # Flushes spans still waiting in the batch processor.
        tracer_provider.shutdown()


# FastAPI 0.142+ has its own OpenTelemetry support: when the SDK is installed and
# OTEL_EXPORTER_OTLP_ENDPOINT is set, it installs global tracer/meter/logger providers before
# lifespan startup (so telemetry.py's provider gets rejected), exports metrics and logs the
# Collector has no pipeline for, and traces /ping and /metrics. Tracing here is configured by
# telemetry.py, and the HTTP routes are only health/metrics, so all of it is off.
app = FastAPI(
    title="recommendation",
    lifespan=lifespan,
    telemetry={"auto_configure": False, "tracing": False, "metrics": False, "logs": False},
)
Instrumentator(excluded_handlers=["/metrics"]).instrument(app).expose(app)


@app.get("/ping")
def ping():
    return {"status": "ok"}
