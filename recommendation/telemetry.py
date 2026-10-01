"""OpenTelemetry tracing setup, shared by every entry point that wants traces. Off by default: unless
OTEL_TRACING_ENABLED=true, configure_tracing() does nothing, so docker-compose's plain
recommendation service (and tests, and the offline scripts) behave exactly as before.

When turned on, it traces the two things the serving path does: the gRPC server span for each
backend call (continuing the backend's trace from the traceparent in the gRPC metadata) and one
span per psycopg2 query. Spans go to the OTel Collector over OTLP/HTTP; the exporter reads the
standard OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_SERVICE_NAME env vars itself.
"""

import os

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.grpc import GrpcInstrumentorServer
from opentelemetry.instrumentation.psycopg2 import Psycopg2Instrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

TRACING_ENABLED = os.environ.get("OTEL_TRACING_ENABLED", "false").lower() == "true"


def configure_tracing() -> TracerProvider | None:
    """Installs the global tracer provider and the gRPC server / psycopg2 instrumentation.

    Must run before the gRPC server is created (grpc_server.create_server), since the server
    instrumentation hooks grpc.server() itself.

    Returns:
        The installed provider, so the caller can flush and shut it down on exit; None when
        tracing is off.
    """
    if not TRACING_ENABLED:
        return None

    # Resource.create() also reads OTEL_SERVICE_NAME / OTEL_RESOURCE_ATTRIBUTES; the default here
    # only applies when OTEL_SERVICE_NAME isn't set.
    resource = Resource.create({"service.name": os.environ.get("OTEL_SERVICE_NAME", "recommendation")})
    tracer_provider = TracerProvider(resource=resource)
    # Batches spans off the request thread; a down Collector only costs dropped spans and a log
    # line, never a failed request.
    tracer_provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
    trace.set_tracer_provider(tracer_provider)

    GrpcInstrumentorServer().instrument()
    Psycopg2Instrumentor().instrument()
    return tracer_provider
