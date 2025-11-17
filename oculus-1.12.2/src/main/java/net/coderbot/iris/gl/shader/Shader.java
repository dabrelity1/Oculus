package net.coderbot.iris.gl.shader;

import java.util.Locale;

import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.IrisRenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20;

/**
 * Thin wrapper around an OpenGL shader object.
 */
public final class Shader extends GlResource {
    private static final Logger LOGGER = LogManager.getLogger(Shader.class);

    private final ShaderType type;
    private final String name;

    public Shader(ShaderType type, String name, String source) {
        super(compile(type, name, source));
        this.type = type;
        this.name = name;
    }

    private static int compile(ShaderType type, String name, String source) {
        int handle = IrisRenderSystem.createShader(type.getGlId());
        IrisRenderSystem.shaderSource(handle, source);
        IrisRenderSystem.compileShader(handle);

        String log = IrisRenderSystem.getShaderInfoLog(handle).trim();
        if (!log.isEmpty()) {
            LOGGER.warn("Shader compilation log for {}: {}", name, log);
        }

        int result = IrisRenderSystem.getShaderParameter(handle, GL20.GL_COMPILE_STATUS);
        if (result != GL20.GL_TRUE) {
            IrisRenderSystem.deleteShader(handle);
            throw new IllegalStateException("Failed to compile " + type.name().toLowerCase(Locale.ROOT) + " shader '" + name + "'.");
        }

        return handle;
    }

    public ShaderType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getHandle() {
        return getGlId();
    }

    @Override
    protected void destroyInternal() {
        IrisRenderSystem.deleteShader(getGlId());
    }
}
