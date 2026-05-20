package net.oculus.shaderpack.texture;

/**
 * Simple blur/clamp flag container used when creating GL textures for custom
 * shader pack assets.
 */
public final class TextureFilteringData {
    private final boolean blur;
    private final boolean clamp;

    public TextureFilteringData(boolean blur, boolean clamp) {
        this.blur = blur;
        this.clamp = clamp;
    }

    public boolean shouldBlur() {
        return blur;
    }

    public boolean shouldClamp() {
        return clamp;
    }
}
