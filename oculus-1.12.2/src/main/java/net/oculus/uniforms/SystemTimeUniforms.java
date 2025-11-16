package net.oculus.uniforms;

import java.util.OptionalLong;
import java.util.function.IntSupplier;

/**
 * Tracks frame-based timing data required by many shader packs. The implementation mirrors the
 * Iris helpers so that packs relying on {@code frameCounter}, {@code frameTime}, and related
 * uniforms behave consistently.
 */
public final class SystemTimeUniforms {
    public static final FrameCounter COUNTER = new FrameCounter();
    public static final Timer TIMER = new Timer();

    private SystemTimeUniforms() {
    }

    /**
     * Simple frame counter that wraps every 720720 frames, matching the Iris behavior.
     */
    public static final class FrameCounter implements IntSupplier {
        private static final int WRAP_VALUE = 720_720;

        private int count;

        @Override
        public int getAsInt() {
            return count;
        }

        public void beginFrame() {
            count = (count + 1) % WRAP_VALUE;
        }

        public void reset() {
            count = 0;
        }
    }

    /**
     * Tracks how long the last frame took and keeps a running counter (in seconds) since the
     * shader pipeline started. Values are kept bounded to prevent floating point drift.
     */
    public static final class Timer {
        private static final float MAX_COUNTER_SECONDS = 3600.0F; // one hour

        private float frameTimeCounter;
        private float lastFrameTime;
        private OptionalLong lastStartTime = OptionalLong.empty();

        public void beginFrame(long frameStartTime) {
            long previousStart = lastStartTime.orElse(frameStartTime);
            long diffNanos = frameStartTime - previousStart;
            long diffMillis = (diffNanos / 1_000_000L);

            lastFrameTime = diffMillis / 1000.0F;
            frameTimeCounter += lastFrameTime;

            if (frameTimeCounter >= MAX_COUNTER_SECONDS) {
                frameTimeCounter = 0.0F;
            }

            lastStartTime = OptionalLong.of(frameStartTime);
        }

        public float getFrameTimeCounter() {
            return frameTimeCounter;
        }

        public float getLastFrameTime() {
            return lastFrameTime;
        }

        public void reset() {
            frameTimeCounter = 0.0F;
            lastFrameTime = 0.0F;
            lastStartTime = OptionalLong.empty();
        }
    }
}
