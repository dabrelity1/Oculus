package net.oculus.gl.blending;

/**
 * Stores alpha test overrides requested by shader packs. Actual GL override
 * logic will be hooked up later; for now we only propagate metadata.
 */
public final class AlphaTestOverride {
    public static final AlphaTestOverride OFF = new AlphaTestOverride(null);

    private final AlphaTest alphaTest;

    public AlphaTestOverride(AlphaTest alphaTest) {
        this.alphaTest = alphaTest;
    }

    public boolean isDisabled() {
        return alphaTest == null;
    }

    public AlphaTest getAlphaTest() {
        return alphaTest;
    }
}
