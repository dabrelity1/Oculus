package net.oculus.uniforms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.oculus.gl.state.FanOutValueUpdateNotifier;
import net.oculus.gl.state.ValueUpdateNotifier;

/**
 * Built-in replacement uniforms that Iris exposes when shader source has been
 * transformed away from fixed-function GLSL state.
 */
public final class BuiltinReplacementUniforms {
    private static final Logger LOGGER = LogManager.getLogger(BuiltinReplacementUniforms.class);

    private static final float LIGHTMAP_SCALE = 0.00390625F;
    private static final float LIGHTMAP_OFFSET = LIGHTMAP_SCALE * 8.0F;

    private static final float[] LIGHTMAP_TEXTURE_MATRIX = {
        LIGHTMAP_SCALE, 0.0F, 0.0F, 0.0F,
        0.0F, LIGHTMAP_SCALE, 0.0F, 0.0F,
        0.0F, 0.0F, LIGHTMAP_SCALE, 0.0F,
        LIGHTMAP_OFFSET, LIGHTMAP_OFFSET, LIGHTMAP_OFFSET, LIGHTMAP_SCALE
    };
    private static final float[] IDENTITY_TEXTURE_MATRIX = {
        1.0F, 0.0F, 0.0F, 0.0F,
        0.0F, 1.0F, 0.0F, 0.0F,
        0.0F, 0.0F, 1.0F, 0.0F,
        0.0F, 0.0F, 0.0F, 1.0F
    };
    private static final float[] ZERO_CHUNK_OFFSET = {0.0F, 0.0F, 0.0F};
    private static final float[] COLOR_MODULATOR = {1.0F, 1.0F, 1.0F, 1.0F};
    private static final FanOutValueUpdateNotifier COLOR_MODULATOR_NOTIFIER = new FanOutValueUpdateNotifier();

    private static boolean warnedLightmapMatrixUse;

    private BuiltinReplacementUniforms() {
    }

    public static float[] getLightmapTextureMatrix() {
        if (!warnedLightmapMatrixUse) {
            warnedLightmapMatrixUse = true;
            LOGGER.warn("A shader appears to require the lightmap texture matrix even after transformations have occurred");
            LOGGER.warn("Oculus handles this correctly but it indicates that the shader is doing unusual things with lightmap coordinates");
        }

        return LIGHTMAP_TEXTURE_MATRIX.clone();
    }

    public static float[] getIdentityTextureMatrix() {
        return IDENTITY_TEXTURE_MATRIX.clone();
    }

    public static float[] getChunkOffset() {
        return ZERO_CHUNK_OFFSET.clone();
    }

    public static float[] getColorModulator() {
        return COLOR_MODULATOR.clone();
    }

    public static ValueUpdateNotifier getColorModulatorNotifier() {
        return COLOR_MODULATOR_NOTIFIER;
    }

    public static void setColorModulator(float red, float green, float blue, float alpha) {
        boolean changed = Float.compare(COLOR_MODULATOR[0], red) != 0
            || Float.compare(COLOR_MODULATOR[1], green) != 0
            || Float.compare(COLOR_MODULATOR[2], blue) != 0
            || Float.compare(COLOR_MODULATOR[3], alpha) != 0;

        COLOR_MODULATOR[0] = red;
        COLOR_MODULATOR[1] = green;
        COLOR_MODULATOR[2] = blue;
        COLOR_MODULATOR[3] = alpha;

        if (changed) {
            COLOR_MODULATOR_NOTIFIER.notifyListeners();
        }
    }
}
