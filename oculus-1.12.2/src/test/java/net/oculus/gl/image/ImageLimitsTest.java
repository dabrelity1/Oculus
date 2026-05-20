package net.oculus.gl.image;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

public class ImageLimitsTest {
    @After
    public void resetImageLimits() {
        ImageLimits.reset();
    }

    @Test
    public void cachedUnavailableLimitCanRecoverAfterLaterProbeSucceeds() {
        assertEquals(0, ImageLimits.get(() -> 0).getMaxImageUnits());
        assertEquals(8, ImageLimits.get(() -> 8).getMaxImageUnits());
    }

    @Test
    public void positiveLimitIsCachedWithoutRepeatedProbes() {
        AtomicInteger probes = new AtomicInteger();

        assertEquals(8, ImageLimits.get(() -> {
            probes.incrementAndGet();
            return 8;
        }).getMaxImageUnits());

        assertEquals(8, ImageLimits.get(() -> {
            probes.incrementAndGet();
            return 16;
        }).getMaxImageUnits());
        assertEquals(1, probes.get());
    }

    @Test
    public void negativeProbeIsTreatedAsUnavailable() {
        assertEquals(0, ImageLimits.get(() -> -1).getMaxImageUnits());
    }
}
