package net.oculus.gl.image;

import java.util.function.IntSupplier;

import net.oculus.gl.OculusRenderSystem;

/**
 * Caches the number of image units supported by the current OpenGL context. Shader packs
 * that rely on image load/store need to respect this number when binding image uniforms.
 */
public final class ImageLimits {
    private static ImageLimits instance;

    private final int maxImageUnits;

    private ImageLimits(int maxImageUnits) {
        this.maxImageUnits = Math.max(0, maxImageUnits);
    }

    public int getMaxImageUnits() {
        return maxImageUnits;
    }

    public static ImageLimits get() {
        return get(OculusRenderSystem::getMaxImageUnits);
    }

    static ImageLimits get(IntSupplier maxImageUnitsSupplier) {
        if (instance != null && instance.maxImageUnits > 0) {
            return instance;
        }

        int probedMaxImageUnits = maxImageUnitsSupplier.getAsInt();
        if (instance == null || probedMaxImageUnits > instance.maxImageUnits) {
            instance = new ImageLimits(probedMaxImageUnits);
        }

        return instance;
    }

    static void reset() {
        instance = null;
    }
}
