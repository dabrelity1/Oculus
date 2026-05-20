package net.oculus.shaderpack;

public enum ParticleRenderingOrder {
    DEFAULT,
    MIXED,
    AFTER,
    BEFORE;

    public ParticleRenderingOrder resolve(boolean hasDeferredPasses) {
        if (this == DEFAULT) {
            return hasDeferredPasses ? AFTER : MIXED;
        }
        return this;
    }
}
