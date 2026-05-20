package net.oculus.pipeline.vertex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.oculus.pipeline.BlockRenderingSettings;
import org.junit.Test;

public class FluidSeparateAoTrackerTest {
    @Test
    public void classifiesVanillaFluidAoFromQuadNormalAndBottomFace() {
        assertEquals(1.0F, FluidSeparateAoTracker.ambientOcclusionForFluidQuad(0.0F, 1.0F, 0.0F, false), 0.0001F);
        assertEquals(0.5F, FluidSeparateAoTracker.ambientOcclusionForFluidQuad(0.0F, -1.0F, 0.0F, true), 0.0001F);
        assertEquals(1.0F, FluidSeparateAoTracker.ambientOcclusionForFluidQuad(0.0F, -1.0F, 0.0F, false), 0.0001F);
        assertEquals(0.8F, FluidSeparateAoTracker.ambientOcclusionForFluidQuad(0.0F, 0.0F, 1.0F, false), 0.0001F);
        assertEquals(0.6F, FluidSeparateAoTracker.ambientOcclusionForFluidQuad(1.0F, 0.0F, 0.0F, false), 0.0001F);
        assertTrue(Float.isNaN(FluidSeparateAoTracker.ambientOcclusionForFluidQuad(0.0F, 0.0F, 0.0F, false)));
    }

    @Test
    public void restoresCapturedFluidTintInsteadOfRoundedMultipliedColor() {
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(true);
        FluidSeparateAoTracker.captureTint(0x336699);

        assertEquals(0x33, FluidSeparateAoTracker.colorComponentWithoutAo(40, 0.8F, 0, false));
        assertEquals(0x66, FluidSeparateAoTracker.colorComponentWithoutAo(81, 0.8F, 1, false));
        assertEquals(0x99, FluidSeparateAoTracker.colorComponentWithoutAo(122, 0.8F, 2, false));
        assertEquals((int) (0.8F * 255.0F), FluidSeparateAoTracker.alphaFromAo(0.8F));

        BlockRenderingSettings.INSTANCE.setUseSeparateAo(false);
        FluidSeparateAoTracker.clear();
        BlockRenderingSettings.INSTANCE.clearReloadRequired();
    }

    @Test
    public void treatsBottomFluidFaceAsUntintedVanillaWhiteWithHalfAo() {
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(true);
        FluidSeparateAoTracker.captureTint(0x336699);

        assertEquals(255, FluidSeparateAoTracker.colorComponentWithoutAo(127, 0.5F, 0, true));
        assertEquals(255, FluidSeparateAoTracker.colorComponentWithoutAo(127, 0.5F, 1, true));
        assertEquals(255, FluidSeparateAoTracker.colorComponentWithoutAo(127, 0.5F, 2, true));
        assertEquals(127, FluidSeparateAoTracker.alphaFromAo(0.5F));

        BlockRenderingSettings.INSTANCE.setUseSeparateAo(false);
        FluidSeparateAoTracker.clear();
        BlockRenderingSettings.INSTANCE.clearReloadRequired();
    }
}
