package com.movierec.streaming.watermark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkOutput;
import org.junit.jupiter.api.Test;

class IdleAdvancingWatermarkGeneratorTest {

    private static final Duration MAX_OUT_OF_ORDERNESS = Duration.ofSeconds(5);
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(30);
    private static final long START_WALL_CLOCK_MILLIS = 1_000_000L;

    @Test
    void tracksMaxEventTimestampMinusOutOfOrdernessWhileEventsAreFlowing() {
        FakeWallClock wallClock = new FakeWallClock(START_WALL_CLOCK_MILLIS);
        IdleAdvancingWatermarkGenerator<String> generator = createGenerator(wallClock);
        CapturingWatermarkOutput output = new CapturingWatermarkOutput();

        generator.onEvent("view", START_WALL_CLOCK_MILLIS, output);
        generator.onPeriodicEmit(output);

        assertEquals(List.of(START_WALL_CLOCK_MILLIS - MAX_OUT_OF_ORDERNESS.toMillis() - 1), output.emittedWatermarkMillis);
    }

    @Test
    void doesNotAdvanceOnWallClockBeforeIdleTimeout() {
        FakeWallClock wallClock = new FakeWallClock(START_WALL_CLOCK_MILLIS);
        IdleAdvancingWatermarkGenerator<String> generator = createGenerator(wallClock);
        CapturingWatermarkOutput output = new CapturingWatermarkOutput();

        generator.onEvent("view", START_WALL_CLOCK_MILLIS, output);
        generator.onPeriodicEmit(output);
        wallClock.advance(IDLE_TIMEOUT.minusMillis(1));
        generator.onPeriodicEmit(output);

        // Same watermark as before the quiet period, so nothing new is emitted.
        assertEquals(1, output.emittedWatermarkMillis.size());
    }

    @Test
    void advancesOnWallClockOnceIdleSoOpenWindowsCanFire() {
        FakeWallClock wallClock = new FakeWallClock(START_WALL_CLOCK_MILLIS);
        IdleAdvancingWatermarkGenerator<String> generator = createGenerator(wallClock);
        CapturingWatermarkOutput output = new CapturingWatermarkOutput();

        generator.onEvent("view", START_WALL_CLOCK_MILLIS, output);
        wallClock.advance(Duration.ofMinutes(2));
        generator.onPeriodicEmit(output);

        long expectedWatermarkMillis = wallClock.nowMillis - MAX_OUT_OF_ORDERNESS.toMillis() - 1;
        assertEquals(List.of(expectedWatermarkMillis), output.emittedWatermarkMillis);
    }

    @Test
    void neverEmitsAWatermarkLowerThanOneAlreadyEmitted() {
        FakeWallClock wallClock = new FakeWallClock(START_WALL_CLOCK_MILLIS);
        IdleAdvancingWatermarkGenerator<String> generator = createGenerator(wallClock);
        CapturingWatermarkOutput output = new CapturingWatermarkOutput();

        // Idle-advance first...
        wallClock.advance(Duration.ofMinutes(2));
        generator.onPeriodicEmit(output);
        long idleAdvancedWatermarkMillis = output.emittedWatermarkMillis.get(0);

        // ...then an event stamped behind that watermark arrives. Its own bound would be lower,
        // so the generator must hold rather than go backwards.
        generator.onEvent("late view", START_WALL_CLOCK_MILLIS, output);
        generator.onPeriodicEmit(output);

        assertEquals(1, output.emittedWatermarkMillis.size());
        assertTrue(idleAdvancedWatermarkMillis > START_WALL_CLOCK_MILLIS);
    }

    @Test
    void idleTimerCountsFromGeneratorCreationSoReplayGetsAGracePeriod() {
        FakeWallClock wallClock = new FakeWallClock(START_WALL_CLOCK_MILLIS);
        IdleAdvancingWatermarkGenerator<String> generator = createGenerator(wallClock);
        CapturingWatermarkOutput output = new CapturingWatermarkOutput();

        // No events yet (e.g. just restored, replay hasn't started), but still inside the idle
        // timeout -- must not jump ahead to wall-clock time and turn the replay into late data.
        wallClock.advance(IDLE_TIMEOUT.minusMillis(1));
        generator.onPeriodicEmit(output);

        assertTrue(output.emittedWatermarkMillis.stream().allMatch(watermarkMillis -> watermarkMillis < 0));
    }

    private static IdleAdvancingWatermarkGenerator<String> createGenerator(FakeWallClock wallClock) {
        return new IdleAdvancingWatermarkGenerator<>(MAX_OUT_OF_ORDERNESS, IDLE_TIMEOUT, () -> wallClock.nowMillis);
    }

    private static final class FakeWallClock {
        private long nowMillis;

        private FakeWallClock(long startMillis) {
            this.nowMillis = startMillis;
        }

        private void advance(Duration duration) {
            nowMillis += duration.toMillis();
        }
    }

    private static final class CapturingWatermarkOutput implements WatermarkOutput {
        private final List<Long> emittedWatermarkMillis = new ArrayList<>();

        @Override
        public void emitWatermark(Watermark watermark) {
            emittedWatermarkMillis.add(watermark.getTimestamp());
        }

        @Override
        public void markIdle() {
        }

        @Override
        public void markActive() {
        }
    }
}
