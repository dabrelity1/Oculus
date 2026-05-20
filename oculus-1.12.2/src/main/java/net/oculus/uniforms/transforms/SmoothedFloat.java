package net.oculus.uniforms.transforms;

import net.oculus.uniforms.FloatSupplier;
import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.SystemTimeUniforms;

/**
 * Basic exponential smoothing helper mirrored from the modern Iris pipeline.
 */
public final class SmoothedFloat implements FloatSupplier {
    private final FloatSupplier unsmoothed;
    private float accumulator;
    private boolean hasInitialValue;
    private float decayConstantUp;
    private float decayConstantDown;

    public SmoothedFloat(float halfLifeUp, float halfLifeDown, FloatSupplier unsmoothed,
                         FrameUpdateNotifier notifier) {
        this.unsmoothed = unsmoothed;
        configureHalfLives(halfLifeUp, halfLifeDown);

        if (notifier != null) {
            notifier.addListener(this::update);
        }
    }

    public void configureHalfLives(float halfLifeUp, float halfLifeDown) {
        this.decayConstantUp = computeDecay(halfLifeUp * 0.1F);
        this.decayConstantDown = computeDecay(halfLifeDown * 0.1F);
        this.accumulator = 0.0F;
        this.hasInitialValue = false;
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
        return ExponentialSmoothing.decayFromHalfLifeSeconds(halfLifeSeconds);
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
