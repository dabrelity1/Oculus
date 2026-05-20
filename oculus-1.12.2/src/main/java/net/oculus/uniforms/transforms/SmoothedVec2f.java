package net.oculus.uniforms.transforms;

import java.util.function.Supplier;

import net.oculus.uniforms.FrameUpdateNotifier;

/**
 * Simple two-component smoothing helper that mirrors the Iris implementation.
 */
public final class SmoothedVec2f implements Supplier<float[]> {
    private static final float[] ZERO = new float[] {0f, 0f};
    private final SmoothedFloat x;
    private final SmoothedFloat y;
    private final float[] buffer = new float[2];

    public SmoothedVec2f(float halfLifeUp, float halfLifeDown, Supplier<float[]> supplier,
                         FrameUpdateNotifier notifier) {
        Supplier<float[]> safeSupplier = supplier != null ? supplier : () -> ZERO;
        this.x = new SmoothedFloat(halfLifeUp, halfLifeDown, () -> component(safeSupplier.get(), 0), notifier);
        this.y = new SmoothedFloat(halfLifeUp, halfLifeDown, () -> component(safeSupplier.get(), 1), notifier);
    }

    public void configureHalfLives(float halfLifeUp, float halfLifeDown) {
        x.configureHalfLives(halfLifeUp, halfLifeDown);
        y.configureHalfLives(halfLifeUp, halfLifeDown);
    }

    private static float component(float[] values, int index) {
        if (values == null || index >= values.length) {
            return 0f;
        }
        return values[index];
    }

    @Override
    public float[] get() {
        buffer[0] = x.getAsFloat();
        buffer[1] = y.getAsFloat();
        return buffer;
    }
}
