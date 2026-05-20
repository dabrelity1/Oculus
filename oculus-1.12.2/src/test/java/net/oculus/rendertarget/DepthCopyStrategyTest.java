package net.oculus.rendertarget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.texture.DepthBufferFormat;
import org.junit.Test;

public class DepthCopyStrategyTest {
    @Test
    public void depthOnlyFallbackDoesNotRequireDestinationFramebuffer() {
        withCapabilityProbesDisabled(() -> {
            DepthCopyStrategy strategy = DepthCopyStrategy.fastest(DepthBufferFormat.DEPTH);

            assertFalse("Depth-only texture copy fallback must allow null destination framebuffers",
                strategy.needsDestFramebuffer());
        });
    }

    @Test
    public void nullFormatFallsBackToDepthOnlyTextureCopyContract() {
        withCapabilityProbesDisabled(() -> {
            DepthCopyStrategy strategy = DepthCopyStrategy.fastest(null);

            assertFalse("Null depth formats are normalized to the depth-only fallback",
                strategy.needsDestFramebuffer());
        });
    }

    @Test
    public void unsupportedCombinedDepthStencilCopyFailsClearlyAndRequiresDestinationFramebuffer() {
        withCapabilityProbesDisabled(() -> {
            DepthCopyStrategy strategy = DepthCopyStrategy.fastest(DepthBufferFormat.DEPTH24_STENCIL8);

            assertTrue("Combined depth-stencil fallback must expose its destination-framebuffer requirement",
                strategy.needsDestFramebuffer());

            try {
                strategy.copy(null, 0, null, 0, 1, 1);
            } catch (IllegalStateException exception) {
                assertTrue(exception.getMessage().contains("Combined depth-stencil depth copies require GL 4.3"));
                return;
            }

            throw new AssertionError("Unsupported combined depth-stencil copies must fail clearly");
        });
    }

    private static void withCapabilityProbesDisabled(Runnable action) {
        String property = OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
