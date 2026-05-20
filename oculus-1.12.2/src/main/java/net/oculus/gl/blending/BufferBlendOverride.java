package net.oculus.gl.blending;

public final class BufferBlendOverride {
    private final int drawBuffer;
    private final BlendMode blendMode;

    public BufferBlendOverride(int drawBuffer, BlendMode blendMode) {
        this.drawBuffer = drawBuffer;
        this.blendMode = blendMode;
    }

    public void apply() {
        BlendModeStorage.overrideBufferBlend(drawBuffer, blendMode);
    }
}
