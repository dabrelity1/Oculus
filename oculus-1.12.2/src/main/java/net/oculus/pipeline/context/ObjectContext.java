package net.oculus.pipeline.context;

import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;

/**
 * Captures shader-pack level objects that would normally be shared across the
 * rendering pipeline. The full implementation wires models, textures, and
 * buffer bindings; the port retains the data relationship so callers can be
 * migrated without churn.
 */
public final class ObjectContext {
    private final ShaderPack pack;
    private final ShaderProperties properties;

    public ObjectContext(ShaderPack pack, ShaderProperties properties) {
        this.pack = pack;
        this.properties = properties;
    }

    public ShaderPack getPack() {
        return pack;
    }

    public ShaderProperties getProperties() {
        return properties;
    }

    public void prepare() {
        // Stub: real implementation populates shared objects.
    }
}
