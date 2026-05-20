package net.oculus.pipeline.vertex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.oculus.pipeline.BlockRenderingSettings;
import org.junit.Test;

public class SeparateAoTrackerTest {
    @Test
    public void mapsVanillaVertexIndicesToAoMultipliers() {
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(true);
        SeparateAoTracker.capture(new float[] {0.25F, 0.5F, 0.75F, 1.0F});

        assertEquals(0.25F, SeparateAoTracker.consume(4), 0.0001F);
        assertEquals(0.5F, SeparateAoTracker.consume(3), 0.0001F);
        assertEquals(0.75F, SeparateAoTracker.consume(2), 0.0001F);
        assertEquals(1.0F, SeparateAoTracker.consume(1), 0.0001F);
        assertTrue(Float.isNaN(SeparateAoTracker.consume(4)));

        BlockRenderingSettings.INSTANCE.setUseSeparateAo(false);
        SeparateAoTracker.clear();
        BlockRenderingSettings.INSTANCE.clearReloadRequired();
    }

    @Test
    public void capturesFlatDiffuseMultiplierForAllFourVertices() {
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(true);
        SeparateAoTracker.captureFlat(0.8F);

        assertEquals(0.8F, SeparateAoTracker.consume(4), 0.0001F);
        assertEquals(0.8F, SeparateAoTracker.consume(3), 0.0001F);
        assertEquals(0.8F, SeparateAoTracker.consume(2), 0.0001F);
        assertEquals(0.8F, SeparateAoTracker.consume(1), 0.0001F);
        assertTrue(Float.isNaN(SeparateAoTracker.consume(4)));

        BlockRenderingSettings.INSTANCE.setUseSeparateAo(false);
        SeparateAoTracker.clear();
        BlockRenderingSettings.INSTANCE.clearReloadRequired();
    }
}
