package net.oculus.gl.image;

import net.oculus.gl.OculusRenderSystem;

/**
 * Caches the number of image units supported by the current OpenGL context. Shader packs
 * that rely on image load/store need to respect this number when binding image uniforms.
 */
public final class ImageLimits {
    private static ImageLimits instance;

    private final int maxImageUnits;

    private ImageLimits() {
        this.maxImageUnits = OculusRenderSystem.getMaxImageUnits();
    }

    public int getMaxImageUnits() {
        return maxImageUnits;
    }

    public static ImageLimits get() {
        if (instance == null) {
            instance = new ImageLimits();
        }

        return instance;
    }
}
