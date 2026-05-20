package net.oculus.uniforms;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import net.oculus.gl.state.ValueUpdateNotifier;

public class BuiltinReplacementUniformsTest {
    @Test
    public void lightmapTextureMatrixMatchesIrisFallbackTransform() {
        float scale = 0.00390625F;
        float offset = scale * 8.0F;
        float[] expected = {
            scale, 0.0F, 0.0F, 0.0F,
            0.0F, scale, 0.0F, 0.0F,
            0.0F, 0.0F, scale, 0.0F,
            offset, offset, offset, scale
        };

        assertArrayEquals(expected, BuiltinReplacementUniforms.getLightmapTextureMatrix(), 0.0F);
    }

    @Test
    public void lightmapTextureMatrixReturnsDefensiveCopy() {
        float[] matrix = BuiltinReplacementUniforms.getLightmapTextureMatrix();
        matrix[0] = 42.0F;

        assertEquals(0.00390625F, BuiltinReplacementUniforms.getLightmapTextureMatrix()[0], 0.0F);
    }

    @Test
    public void vanillaCompatibilityFallbacksExposeDefensiveValues() {
        float[] identity = {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F
        };

        assertArrayEquals(identity, BuiltinReplacementUniforms.getIdentityTextureMatrix(), 0.0F);
        assertArrayEquals(new float[] {0.0F, 0.0F, 0.0F}, BuiltinReplacementUniforms.getChunkOffset(), 0.0F);

        BuiltinReplacementUniforms.setColorModulator(0.25F, 0.5F, 0.75F, 0.875F);
        assertArrayEquals(new float[] {0.25F, 0.5F, 0.75F, 0.875F},
            BuiltinReplacementUniforms.getColorModulator(), 0.0F);

        float[] color = BuiltinReplacementUniforms.getColorModulator();
        color[0] = 1.0F;
        assertEquals(0.25F, BuiltinReplacementUniforms.getColorModulator()[0], 0.0F);

        BuiltinReplacementUniforms.setColorModulator(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Test
    public void colorModulatorNotifiesWhenRuntimeValueChanges() {
        ValueUpdateNotifier notifier = BuiltinReplacementUniforms.getColorModulatorNotifier();
        AtomicInteger updates = new AtomicInteger();
        Runnable listener = updates::incrementAndGet;

        notifier.setListener(null);
        try {
            BuiltinReplacementUniforms.setColorModulator(1.0F, 1.0F, 1.0F, 1.0F);
            notifier.setListener(listener);

            BuiltinReplacementUniforms.setColorModulator(0.25F, 0.5F, 0.75F, 0.875F);
            BuiltinReplacementUniforms.setColorModulator(0.25F, 0.5F, 0.75F, 0.875F);

            assertEquals(1, updates.get());
        } finally {
            notifier.removeListener(listener);
            BuiltinReplacementUniforms.setColorModulator(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
