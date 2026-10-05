"""OpenTelemetry tracing setup, shared by every entry point that wants traces. Off by default: unless
OTEL_TRACING_ENABLED=true, configure_tracing() does nothing, so docker-compose's plain
recommendation service (and tests, and the offline scripts) behave exactly as before.

When turned on, it traces the two things the serving path does: the gRPC server span for each
backend call (continuing the backend's trace from the traceparent in the gRPC metadata) and one
span per psycopg2 query. Spans go to the OTel Collector over OTLP/HTTP; the exporter reads the
standard OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_SERVICE_NAME env vars itself.

configure_logging() sets up the application's log output either way; with tracing on, every line
also carries the trace and span id of the request it was logged in, so a trace found in Tempo can
be matched to its log lines (and the other way round).
"""

import logging
import os

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.grpc import GrpcInstrumentorServer
from opentelemetry.instrumentation.psycopg2 import Psycopg2Instrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

TRACING_ENABLED = os.environ.get("OTEL_TRACING_ENABLED", "false").lower() == "true"

LOG_FORMAT = "%(asctime)s %(levelname)s %(name)s: %(message)s"
# Same "[trace id,span id]" shape as the backend's LOGGING_PATTERN_CORRELATION (k8s/backend.yaml).
TRACED_LOG_FORMAT = "%(asctime)s %(levelname)s [%(trace_id)s,%(span_id)s] %(name)s: %(message)s"


class TraceContextLogFilter(logging.Filter):
    """Stamps each log record with the trace and span id of the span it was logged in."""

    def filter(self, record: logging.LogRecord) -> bool:
        """Adds trace_id and span_id attributes to the record, for TRACED_LOG_FORMAT to print.

        Args:
            record: The log record about to be formatted; modified in place.

        Returns:
            Always True: this filter only annotates records, it never drops one.
        """
        span_context = trace.get_current_span().get_span_context()
        if span_context.is_valid:
            # Lowercase hex, the form Tempo and the traceparent header use.
            record.trace_id = format(span_context.trace_id, "032x")
            record.span_id = format(span_context.span_id, "016x")
        else:
            # Logged outside any request (startup, shutdown): empty, so the format still applies.
            record.trace_id = ""
            record.span_id = ""
        return True


def configure_logging() -> None:
    """Sends the application's own log lines (INFO and above) to stderr.

    Uvicorn only configures its own "uvicorn.*" loggers, so without this the root logger has no
    handler and INFO lines from this codebase are dropped. Uvicorn's access and error lines keep
    their own format. Call once per process: every call adds another handler.
    """
    log_handler = logging.StreamHandler()
    if TRACING_ENABLED:
        # On the handler, not the root logger: logger-level filters don't run for records that
        # propagate up from child loggers.
        log_handler.addFilter(TraceContextLogFilter())
        log_handler.setFormatter(logging.Formatter(TRACED_LOG_FORMAT))
    else:
        log_handler.setFormatter(logging.Formatter(LOG_FORMAT))

    root_logger = logging.getLogger()
    root_logger.addHandler(log_handler)
    root_logger.setLevel(logging.INFO)


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
