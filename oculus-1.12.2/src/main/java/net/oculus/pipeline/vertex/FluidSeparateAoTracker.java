package net.oculus.pipeline.vertex;

import net.oculus.pipeline.BlockRenderingSettings;

/**
 * Carries the active vanilla fluid tint into completed fluid quads so separateAo can
 * preserve pre-lighting RGB while moving the vanilla directional factor into alpha.
 */
public final class FluidSeparateAoTracker {
    private static final float EPSILON = 1.0E-6F;
    private static final ThreadLocal<Tint> CURRENT_TINT = new ThreadLocal<>();

    private FluidSeparateAoTracker() {
    }

    public static void captureTint(int color) {
        if (!BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()) {
            clear();
            return;
        }

        CURRENT_TINT.set(new Tint((color >> 16) & 255, (color >> 8) & 255, color & 255));
    }

    public static float ambientOcclusionForFluidQuad(float normalX, float normalY, float normalZ, boolean blockBottomFace) {
        if (Math.abs(normalX) + Math.abs(normalY) + Math.abs(normalZ) < EPSILON) {
            return Float.NaN;
        }

        if (normalY > 0.5F) {
            return 1.0F;
        }

        if (normalY < -0.5F) {
            return blockBottomFace ? 0.5F : 1.0F;
        }

        return Math.abs(normalZ) >= Math.abs(normalX) ? 0.8F : 0.6F;
    }

    public static boolean isDownFacingNormal(float normalY) {
        return normalY < -0.5F;
    }

    public static int colorComponentWithoutAo(int multipliedValue, float ao, int channel, boolean untintedBottom) {
        if (untintedBottom) {
            return 255;
        }

        Tint tint = CURRENT_TINT.get();
        if (tint != null) {
            return tint.component(channel);
        }

        if (ao > EPSILON) {
            return clampColor(Math.round(multipliedValue / ao));
        }

        return clampColor(multipliedValue);
    }

    public static int alphaFromAo(float ao) {
        return clampColor((int) (clampUnit(ao) * 255.0F));
    }

    public static void clear() {
        CURRENT_TINT.remove();
    }

    private static int clampColor(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }

    private static float clampUnit(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static final class Tint {
        private final int red;
        private final int green;
        private final int blue;

        private Tint(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        private int component(int channel) {
            if (channel == 0) {
                return red;
            }
            if (channel == 1) {
                return green;
            }
            return blue;
        }
    }
}
