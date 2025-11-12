package net.oculus.gl.shader;

import net.oculus.gl.GLDebug;
import net.oculus.gl.GlResource;
import net.oculus.gl.OculusRenderSystem;

/**
 * Bare-bones shader wrapper based on the 1.16.5 implementation. The actual OpenGL
 * calls are deferred, but the structure mirrors the upstream class so later ports can
 * fill in the real behaviour without touching call sites.
 */
public class GlShader extends GlResource {
    private final String name;
    private final ShaderType type;

    public GlShader(ShaderType type, String name, String source) {
        super(OculusRenderSystem.createShader());
        this.name = name;
        this.type = type;

        ShaderWorkarounds.safeShaderSource(getGlId(), source);
        GLDebug.nameObject(type.id, getGlId(), name);
    }

    public String getName() {
        return name;
    }

    public ShaderType getType() {
        return type;
    }

    public int getHandle() {
        return getGlId();
    }

    @Override
    protected void destroyInternal() {
        OculusRenderSystem.deleteShader(getGlId());
    }
}
