package net.oculus.uniforms.transforms;

import net.oculus.uniforms.FloatSupplier;
import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.SystemTimeUniforms;

/**
 * Basic exponential smoothing helper mirrored from the modern Iris pipeline.
 */
public final class SmoothedFloat implements FloatSupplier {
    private static final double LN_OF_2 = Math.log(2.0);

    private final FloatSupplier unsmoothed;
    private float accumulator;
    private boolean hasInitialValue;
    private final float decayConstantUp;
    private final float decayConstantDown;

    public SmoothedFloat(float halfLifeUp, float halfLifeDown, FloatSupplier unsmoothed,
                         FrameUpdateNotifier notifier) {
        this.decayConstantUp = computeDecay(halfLifeUp * 0.1F);
        this.decayConstantDown = computeDecay(halfLifeDown * 0.1F);
        this.unsmoothed = unsmoothed;

        if (notifier != null) {
            notifier.addListener(this::update);
        }
    }

    private void update() {
        if (!hasInitialValue) {
            accumulator = unsmoothed.getAsFloat();
            hasInitialValue = true;
            return;
        }

        float newValue = unsmoothed.getAsFloat();
        float lastFrameTime = SystemTimeUniforms.TIMER.getLastFrameTime();
        float decay = newValue > accumulator ? decayConstantUp : decayConstantDown;
        float smoothingFactor = 1.0f - exponentialDecay(decay, lastFrameTime);
        accumulator = lerp(accumulator, newValue, smoothingFactor);
    }

    private static float computeDecay(float halfLifeSeconds) {
        if (halfLifeSeconds <= 0.0f) {
            return 1.0f;
        }
        return (float) (1.0f / (halfLifeSeconds / LN_OF_2));
    }

    private static float exponentialDecay(float k, float t) {
        return (float) Math.exp(-k * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public float getAsFloat() {
        if (!hasInitialValue) {
            return unsmoothed.getAsFloat();
        }
        return accumulator;
    }
}
