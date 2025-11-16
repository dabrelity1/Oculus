package net.oculus.gl.blending;

/**
 * Describes per-render-target blend overrides parsed from shaders.properties.
 */
public final class BufferBlendInformation {
    private final int index;
    private final BlendMode blendMode;

    public BufferBlendInformation(int index, BlendMode blendMode) {
        this.index = index;
        this.blendMode = blendMode;
    }

    public int getIndex() {
        return index;
    }

    public BlendMode getBlendMode() {
        return blendMode;
    }
}
