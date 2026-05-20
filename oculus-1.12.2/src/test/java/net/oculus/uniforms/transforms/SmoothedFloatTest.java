package net.oculus.uniforms.transforms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.SystemTimeUniforms;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SmoothedFloatTest {
    @Before
    public void resetTimerBefore() {
        SystemTimeUniforms.TIMER.reset();
    }

    @After
    public void resetTimerAfter() {
        SystemTimeUniforms.TIMER.reset();
    }

    @Test
    public void zeroHalfLifeProducesInstantConvergenceLikeIris() {
        float[] value = {0.0F};
        FrameUpdateNotifier notifier = new FrameUpdateNotifier();
        SmoothedFloat smoothed = new SmoothedFloat(0.0F, 1.0F, () -> value[0], notifier);

        SystemTimeUniforms.TIMER.beginFrame(0L);
        notifier.onNewFrame();
        assertEquals(0.0F, smoothed.getAsFloat(), 0.0F);

        value[0] = 1.0F;
        SystemTimeUniforms.TIMER.beginFrame(100_000_000L);
        notifier.onNewFrame();

        assertEquals(1.0F, smoothed.getAsFloat(), 0.0F);
        assertTrue(Float.isInfinite(ExponentialSmoothing.decayFromHalfLifeSeconds(0.0F)));
    }

    @Test
    public void finiteHalfLifeUsesReferenceDecayUnits() {
        float[] value = {0.0F};
        FrameUpdateNotifier notifier = new FrameUpdateNotifier();
        SmoothedFloat smoothed = new SmoothedFloat(1.0F, 1.0F, () -> value[0], notifier);

        SystemTimeUniforms.TIMER.beginFrame(0L);
        notifier.onNewFrame();

        value[0] = 1.0F;
        SystemTimeUniforms.TIMER.beginFrame(100_000_000L);
        notifier.onNewFrame();

        assertEquals(0.5F, smoothed.getAsFloat(), 0.0001F);
    }

    @Test
    public void reconfiguringHalfLivesResetsAccumulatorLikeNewReferenceSmoother() {
        float[] value = {0.0F};
        FrameUpdateNotifier notifier = new FrameUpdateNotifier();
        SmoothedFloat smoothed = new SmoothedFloat(600.0F, 200.0F, () -> value[0], notifier);

        SystemTimeUniforms.TIMER.beginFrame(0L);
        notifier.onNewFrame();

        value[0] = 1.0F;
        SystemTimeUniforms.TIMER.beginFrame(100_000_000L);
        notifier.onNewFrame();
        assertTrue(smoothed.getAsFloat() < 1.0F);

        smoothed.configureHalfLives(0.0F, 0.0F);
        SystemTimeUniforms.TIMER.beginFrame(200_000_000L);
        notifier.onNewFrame();

        assertEquals(1.0F, smoothed.getAsFloat(), 0.0F);
    }
}
