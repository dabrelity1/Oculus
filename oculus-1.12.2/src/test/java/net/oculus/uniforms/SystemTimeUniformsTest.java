package net.oculus.uniforms;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SystemTimeUniformsTest {
    @Test
    public void frameCounterWrapsAtReferenceInterval() {
        SystemTimeUniforms.FrameCounter counter = new SystemTimeUniforms.FrameCounter();

        for (int i = 0; i < 720719; i++) {
            counter.beginFrame();
        }

        assertEquals(720719, counter.getAsInt());

        counter.beginFrame();

        assertEquals(0, counter.getAsInt());

        counter.beginFrame();

        assertEquals(1, counter.getAsInt());
    }

    @Test
    public void timerUsesMillisecondResolutionAndWrapsAfterOneHour() {
        SystemTimeUniforms.Timer timer = new SystemTimeUniforms.Timer();

        timer.beginFrame(1_000_000_000L);

        assertEquals(0.0F, timer.getLastFrameTime(), 0.0F);
        assertEquals(0.0F, timer.getFrameTimeCounter(), 0.0F);

        timer.beginFrame(1_016_900_000L);

        assertEquals(0.016F, timer.getLastFrameTime(), 0.0F);
        assertEquals(0.016F, timer.getFrameTimeCounter(), 0.0F);

        timer.beginFrame(3_601_016_900_000L);

        assertEquals(3600.0F, timer.getLastFrameTime(), 0.0F);
        assertEquals(0.0F, timer.getFrameTimeCounter(), 0.0F);
    }
}
