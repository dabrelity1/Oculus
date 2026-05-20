package net.oculus.gl.shader;

import net.oculus.gl.GLDebug;
import net.oculus.gl.GlResource;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.shaderpack.ProgramLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Compiled shader object backed by the legacy LWJGL 2 OpenGL bindings.
 */
public class GlShader extends GlResource {
    private static final Logger LOGGER = LogManager.getLogger(GlShader.class);

    private static final int GL_DEBUG_SHADER = 0x82E1; // GL_SHADER for KHR_debug naming

    private final String name;
    private final ShaderType type;

    public GlShader(ShaderType type, String name, String source) {
        super(OculusRenderSystem.glCreateShader(type.id));
        this.name = name;
        this.type = type;

        try {
            OculusRenderSystem.glShaderSource(getGlId(), source);
            OculusRenderSystem.glCompileShader(getGlId());

            GLDebug.nameObject(GL_DEBUG_SHADER, getGlId(), name);

            int logLength = OculusRenderSystem.glGetShaderi(getGlId(), GL20.GL_INFO_LOG_LENGTH);
            String log = OculusRenderSystem.glGetShaderInfoLog(getGlId(), logLength);
            if (!log.isEmpty()) {
                LOGGER.warn("Shader compilation log for {}: {}", name, log);
            }

            int status = OculusRenderSystem.glGetShaderi(getGlId(), GL20.GL_COMPILE_STATUS);
            if (status != GL11.GL_TRUE) {
                throw new ProgramLoadException("Failed to compile shader " + name + " (" + type + ")\n" + log);
            }
        } catch (RuntimeException | Error exception) {
            closeFailedShader(exception);
            throw exception;
        }
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
        OculusRenderSystem.glDeleteShader(getGlId());
    }

    private void closeFailedShader(Throwable failure) {
        try {
            destroy();
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            LOGGER.debug("Failed to delete shader {} after construction failure", name, cleanupFailure);
        }
    }
}
