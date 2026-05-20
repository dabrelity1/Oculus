package net.oculus.pipeline.vertex;

import net.oculus.pipeline.BlockRenderingSettings;

/**
 * Carries the active smooth block-model AO multipliers from
 * BlockModelRenderer.AmbientOcclusionFace into BufferBuilder.putColorMultiplier.
 */
public final class SeparateAoTracker {
    private static final ThreadLocal<float[]> CURRENT_MULTIPLIERS = new ThreadLocal<>();

    private SeparateAoTracker() {
    }

    public static void capture(float[] multipliers) {
        if (!BlockRenderingSettings.INSTANCE.shouldUseSeparateAo() || multipliers == null || multipliers.length < 4) {
            clear();
            return;
        }

        CURRENT_MULTIPLIERS.set(multipliers);
    }

    public static void captureFlat(float multiplier) {
        if (!BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()) {
            clear();
            return;
        }

        CURRENT_MULTIPLIERS.set(new float[] {multiplier, multiplier, multiplier, multiplier});
    }

    public static float consume(int vertexIndex) {
        float[] multipliers = CURRENT_MULTIPLIERS.get();
        if (multipliers == null || vertexIndex < 1 || vertexIndex > 4) {
            return Float.NaN;
        }

        int multiplierIndex = 4 - vertexIndex;
        float multiplier = multipliers[multiplierIndex];

        if (vertexIndex == 1) {
            clear();
        }

        return multiplier;
    }

    public static void clear() {
        CURRENT_MULTIPLIERS.remove();
    }
}
