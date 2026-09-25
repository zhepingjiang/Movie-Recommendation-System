package com.movierec.streaming.watermark;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkOutput;

/**
 * Bounded-out-of-orderness watermarks (same as {@code WatermarkStrategy.forBoundedOutOfOrderness})
 * that also keep advancing on wall-clock time once no event has arrived for {@code idleTimeout}.
 *
 * <p>Plain bounded-out-of-orderness only moves the watermark when a newer event arrives, so after
 * the last view before a quiet period, every window containing it stays open until someone views
 * something again -- no nearline output at all during low traffic. Flink's own {@code
 * withIdleness(...)} doesn't cover this: it marks an idle split as idle so it stops holding back
 * the <i>other</i> splits' watermarks, but once every split is idle the downstream watermark
 * still doesn't move.
 *
 * <p>Tradeoff: once the watermark has been pushed forward by wall-clock time, an event arriving
 * afterwards with a timestamp older than {@code now - maxOutOfOrderness} is late and dropped by
 * the window. The backend stamps events at request time and produces them synchronously, so that
 * requires a delay/clock skew beyond {@code maxOutOfOrderness} -- the same bound the non-idle path
 * already relies on. Replay after recovery is unaffected in practice: replayed events arrive
 * back-to-back, so the idle branch never kicks in mid-replay.
 */
public class IdleAdvancingWatermarkGenerator<T> implements WatermarkGenerator<T> {

    private final long maxOutOfOrdernessMillis;
    private final long idleTimeoutMillis;
    private final LongSupplier wallClockMillis;

    // Start low enough that "maxEventTimestampMillis - maxOutOfOrdernessMillis - 1" can't
    // underflow before the first event -- same trick as Flink's BoundedOutOfOrdernessWatermarks.
    private long maxEventTimestampMillis;
    private long lastEventWallClockMillis;
    private long lastEmittedWatermarkMillis = Long.MIN_VALUE;

    public IdleAdvancingWatermarkGenerator(
            Duration maxOutOfOrderness, Duration idleTimeout, LongSupplier wallClockMillis) {
        this.maxOutOfOrdernessMillis = maxOutOfOrderness.toMillis();
        this.idleTimeoutMillis = idleTimeout.toMillis();
        this.wallClockMillis = wallClockMillis;
        this.maxEventTimestampMillis = Long.MIN_VALUE + maxOutOfOrdernessMillis + 1;
        // Counts "idle" from when this generator is created (job start/restore), not from the
        // epoch, so a freshly (re)started job gets a full idleTimeout for replayed events to
        // start flowing before wall-clock advancing kicks in.
        this.lastEventWallClockMillis = wallClockMillis.getAsLong();
    }

    @Override
    public void onEvent(T event, long eventTimestampMillis, WatermarkOutput output) {
        maxEventTimestampMillis = Math.max(maxEventTimestampMillis, eventTimestampMillis);
        lastEventWallClockMillis = wallClockMillis.getAsLong();
    }

    @Override
    public void onPeriodicEmit(WatermarkOutput output) {
        long watermarkMillis = maxEventTimestampMillis - maxOutOfOrdernessMillis - 1;

        long nowMillis = wallClockMillis.getAsLong();
        if (nowMillis - lastEventWallClockMillis >= idleTimeoutMillis) {
            // No events for a while, so anything arriving next will be stamped around "now" --
            // treat now (minus the usual out-of-orderness allowance) as the event-time
            // high-water mark, which lets windows ending before it fire.
            watermarkMillis = Math.max(watermarkMillis, nowMillis - maxOutOfOrdernessMillis - 1);
        }

        // Watermarks must never go backwards -- e.g. a burst of events stamped slightly behind
        // a wall-clock-advanced watermark would otherwise pull it back.
        if (watermarkMillis > lastEmittedWatermarkMillis) {
            lastEmittedWatermarkMillis = watermarkMillis;
            output.emitWatermark(new Watermark(watermarkMillis));
        }
    }
}
