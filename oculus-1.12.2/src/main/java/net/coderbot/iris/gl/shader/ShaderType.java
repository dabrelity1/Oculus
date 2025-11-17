package net.coderbot.iris.gl.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

/**
 * Enumeration of shader stages supported (or explicitly unsupported) on the 1.12.2 renderer.
 */
public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER),
    GEOMETRY(GL32.GL_GEOMETRY_SHADER),
    COMPUTE(-1);

    private final int glId;

    ShaderType(int glId) {
        this.glId = glId;
    }

    public int getGlId() {
        if (glId == -1) {
            throw new UnsupportedOperationException("The " + name() + " shader stage is not available on LWJGL 2");
        }

        return glId;
    }

    public boolean isSupported() {
        return glId != -1;
    }
}
