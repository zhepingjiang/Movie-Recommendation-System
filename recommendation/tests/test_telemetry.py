import logging

from opentelemetry.sdk.trace import TracerProvider

import telemetry


def _make_log_record() -> logging.LogRecord:
    return logging.LogRecord(
        name="grpc_server",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="Cold-start recommendations: user_id=%s",
        args=(7,),
        exc_info=None,
    )


def test_filter_stamps_the_current_span_ids_on_the_record():
    # A local provider, not the global one: the current span lives in the context either way.
    tracer = TracerProvider().get_tracer("test")
    log_record = _make_log_record()

    with tracer.start_as_current_span("request") as span:
        span_context = span.get_span_context()
        kept = telemetry.TraceContextLogFilter().filter(log_record)

    assert kept is True
    assert log_record.trace_id == format(span_context.trace_id, "032x")
    assert log_record.span_id == format(span_context.span_id, "016x")
    assert len(log_record.trace_id) == 32
    assert len(log_record.span_id) == 16


def test_filter_leaves_ids_empty_outside_a_span():
    log_record = _make_log_record()

    kept = telemetry.TraceContextLogFilter().filter(log_record)

    assert kept is True
    assert log_record.trace_id == ""
    assert log_record.span_id == ""


def test_traced_log_format_prints_the_ids_next_to_the_message():
    tracer = TracerProvider().get_tracer("test")
    log_record = _make_log_record()

    with tracer.start_as_current_span("request") as span:
        span_context = span.get_span_context()
        telemetry.TraceContextLogFilter().filter(log_record)
    formatted_line = logging.Formatter(telemetry.TRACED_LOG_FORMAT).format(log_record)

    expected_ids = f"[{format(span_context.trace_id, '032x')},{format(span_context.span_id, '016x')}]"
    assert f"INFO {expected_ids} grpc_server: Cold-start recommendations: user_id=7" in formatted_line
