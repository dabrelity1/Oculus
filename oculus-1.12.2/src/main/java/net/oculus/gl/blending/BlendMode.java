package net.oculus.gl.blending;

/**
 * Container describing the four GL blend function values for RGB/alpha. Even
 * though the 1.12 renderer does not yet apply them, storing the metadata keeps
 * shader pack expectations intact.
 */
public final class BlendMode {
    private final int srcRgb;
    private final int dstRgb;
    private final int srcAlpha;
    private final int dstAlpha;

    public BlendMode(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
        this.srcAlpha = srcAlpha;
        this.dstAlpha = dstAlpha;
    }

    public int getSrcRgb() {
        return srcRgb;
    }

    public int getDstRgb() {
        return dstRgb;
    }

    public int getSrcAlpha() {
        return srcAlpha;
    }

    public int getDstAlpha() {
        return dstAlpha;
    }
}
