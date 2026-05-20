package net.oculus.uniforms;

import net.minecraft.util.math.MathHelper;

import net.oculus.gl.state.GameDataSuppliers;

/**
 * Provides derived uniforms that OptiFine shader packs usually calculate via
 * {@code shaders.properties}. The values are recomputed on demand so packs
 * targeting newer OptiFine versions can keep their existing conditionals.
 */
public final class CustomUniforms {
    private static final float[] TAA_OFFSET = new float[2];

    private static final float[][] TAA_SEQUENCE = {
        { 0.5F,  0.5F}, {-0.5F, -0.5F}, {-0.5F,  0.5F}, { 0.5F, -0.5F},
        { 0.5F,  0.5F}, {-0.5F, -0.5F}, {-0.5F,  0.5F}, { 0.5F, -0.5F},
        { 0.5F,  0.5F}, {-0.5F, -0.5F}, {-0.5F,  0.5F}, { 0.5F, -0.5F},
        { 0.5F,  0.5F}, {-0.5F, -0.5F}, {-0.5F,  0.5F}, { 0.5F, -0.5F}
    };

    private static final float[] DITHER_SEQUENCE = {
        0.0625F, 0.4375F, 0.8750F, 0.6250F,
        0.2500F, 0.8125F, 0.1250F, 0.9375F,
        0.3125F, 0.5000F, 0.3750F, 0.5625F,
        0.7500F, 0.6875F, 0.1875F, 0.0F
    };

    private CustomUniforms() {
    }

    public static float getPixelSizeX() {
        Float width = GameDataSuppliers.viewWidth().get();
        if (width == null || width <= 0.0F) {
            return 0.0F;
        }
        return 1.0F / width;
    }

    public static float getPixelSizeY() {
        Float height = GameDataSuppliers.viewHeight().get();
        if (height == null || height <= 0.0F) {
            return 0.0F;
        }
        return 1.0F / height;
    }

    public static float getInverseAspectRatio() {
        Float aspect = GameDataSuppliers.aspectRatio().get();
        if (aspect == null || Math.abs(aspect) < 1.0e-6F) {
            return 0.0F;
        }
        return 1.0F / aspect;
    }

    public static float getDayMoment() {
        return GameplayUniforms.getWorldTime() / 24000.0F;
    }

    public static float getDayMixer() {
        float moment = getDayMoment() - 0.25F;
        float aux = moment * moment;
        return MathHelper.clamp(-aux * 20.0F + 1.25F, 0.0F, 1.0F);
    }

    public static float getNightMixer() {
        float moment = getDayMoment() - 0.75F;
        float aux = moment * moment;
        return MathHelper.clamp(-aux * 50.0F + 3.125F, 0.0F, 1.0F);
    }

    public static float getVolumeMixer() {
        float dayMoment = getDayMoment();
        float aux5 = (dayMoment * 4.0F) - 1.0F;
        float aux6 = pow4(aux5);
        float day = MathHelper.clamp(((-aux6 + 1.0F) * 7.0F) + 1.0F, 1.0F, 8.0F);

        float aux7 = (dayMoment * 4.0F) - 3.0F;
        float aux8 = pow4(aux7);
        float night = MathHelper.clamp(((-aux8 + 1.0F) * 7.0F) + 1.0F, 1.0F, 8.0F);

        return Math.max(day, night);
    }

    public static float getLightMix() {
        int worldTime = GameplayUniforms.getWorldTime();
        float mixA = (worldTime < 12485 || worldTime >= 23515) ? 1.0F : 0.0F;
        float mixB = (worldTime >= 12485 && worldTime < 13085)
            ? 1.0F - ((worldTime - 12485) * 0.0016666667F)
            : 0.0F;
        float mixD = (worldTime >= 22915 && worldTime < 23515)
            ? (worldTime - 22915) * 0.0016666667F
            : 0.0F;
        float maxAB = Math.max(mixA, mixB);
        float maxCD = Math.max(0.0F, mixD);
        return Math.max(maxAB, maxCD);
    }

    public static int getFrameMod() {
        return SystemTimeUniforms.COUNTER.getAsInt() & 15;
    }

    public static float[] getTaaOffset() {
        float pixelX = getPixelSizeX();
        float pixelY = getPixelSizeY();
        int index = getFrameMod();
        if (index < 0 || index >= TAA_SEQUENCE.length) {
            index = Math.floorMod(index, TAA_SEQUENCE.length);
        }

        float[] offsets = TAA_SEQUENCE[index];
        TAA_OFFSET[0] = offsets[0] * pixelX;
        TAA_OFFSET[1] = offsets[1] * pixelY;
        return TAA_OFFSET;
    }

    public static float getDitherShift() {
        int index = MathHelper.clamp(getFrameMod(), 0, DITHER_SEQUENCE.length - 1);
        return DITHER_SEQUENCE[index];
    }

    public static float getFovYInverse() {
        float[] projection = CapturedRenderingState.INSTANCE.getGbufferProjection();
        if (projection == null || projection.length <= 5) {
            return 0.0F;
        }

        float entry = projection[5];
        if (Math.abs(entry) < 1.0e-6F) {
            return 0.0F;
        }

        return (float) (1.0F / Math.atan(1.0 / entry) * 0.5F);
    }

    private static float pow4(float value) {
        float square = value * value;
        return square * square;
    }
}
