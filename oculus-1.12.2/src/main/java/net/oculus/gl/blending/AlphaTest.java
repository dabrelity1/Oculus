package net.oculus.gl.blending;

/**
 * Minimal alpha test descriptor used by shader metadata. The actual GL state
 * plumbing will be wired in once the deferred renderer is ported.
 */
public final class AlphaTest {
    private final AlphaTestFunction function;
    private final float reference;

    public AlphaTest(AlphaTestFunction function, float reference) {
        this.function = function;
        this.reference = reference;
    }

    public AlphaTestFunction getFunction() {
        return function;
    }

    public float getReference() {
        return reference;
    }
}
