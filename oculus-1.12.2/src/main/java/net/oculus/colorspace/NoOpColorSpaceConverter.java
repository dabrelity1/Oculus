package net.oculus.colorspace;

/**
 * Used when a shader pack declares support for its own color correction. In
 * that mode Oculus exposes the enum defines/uniforms and must not apply the
 * post-final screen transform on top of the pack's own logic.
 */
public final class NoOpColorSpaceConverter implements ColorSpaceConverter {
    public static final NoOpColorSpaceConverter INSTANCE = new NoOpColorSpaceConverter();

    private NoOpColorSpaceConverter() {
    }

    @Override
    public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
    }

    @Override
    public void process(int targetTexture) {
    }
}
