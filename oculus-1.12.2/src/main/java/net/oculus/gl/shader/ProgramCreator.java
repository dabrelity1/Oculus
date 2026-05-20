package net.oculus.gl.shader;

import net.oculus.gl.GLDebug;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.shaderpack.ProgramLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Links one or more compiled shaders into a program object and surfaces link errors.
 */
public final class ProgramCreator {
    private static final Logger LOGGER = LogManager.getLogger(ProgramCreator.class);

    private static final int GL_DEBUG_PROGRAM = 0x82E2; // GL_PROGRAM for KHR_debug naming

    private ProgramCreator() {
    }

    public static int create(String name, GlShader... shaders) {
        int program = OculusRenderSystem.glCreateProgram();
        boolean linked = false;
        Throwable primaryFailure = null;

        // Bind attribute slots used by shader packs before linking.
        OculusRenderSystem.bindAttributeLocation(program, 11, "mc_Entity");
        OculusRenderSystem.bindAttributeLocation(program, 12, "mc_midTexCoord");
        OculusRenderSystem.bindAttributeLocation(program, 13, "at_tangent");
        OculusRenderSystem.bindAttributeLocation(program, 14, "at_midBlock");

        try {
            for (GlShader shader : shaders) {
                OculusRenderSystem.glAttachShader(program, shader.getHandle());
            }

            OculusRenderSystem.glLinkProgram(program);

            int logLength = OculusRenderSystem.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH);
            String log = OculusRenderSystem.glGetProgramInfoLog(program, logLength);
            if (!log.isEmpty()) {
                LOGGER.warn("Program link log for {}: {}", name, log);
            }

            int status = OculusRenderSystem.glGetProgrami(program, GL20.GL_LINK_STATUS);
            if (status != GL11.GL_TRUE) {
                throw new ProgramLoadException("Failed to link program " + name + "\n" + log);
            }

            GLDebug.nameObject(GL_DEBUG_PROGRAM, program, name);

            linked = true;
            return program;
        } catch (RuntimeException | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            for (GlShader shader : shaders) {
                detachShader(program, shader, name, primaryFailure);
            }

            if (!linked) {
                deleteFailedProgram(program, name, primaryFailure);
            }
        }
    }

    private static void detachShader(int program, GlShader shader, String name, Throwable primaryFailure) {
        if (program == 0) {
            return;
        }

        try {
            OculusRenderSystem.glDetachShader(program, shader.getHandle());
        } catch (RuntimeException | Error cleanupFailure) {
            handleCleanupFailure(primaryFailure, cleanupFailure,
                "Failed to detach shader " + shader.getName() + " from program " + name);
        }
    }

    private static void deleteFailedProgram(int program, String name, Throwable primaryFailure) {
        if (program == 0) {
            return;
        }

        try {
            OculusRenderSystem.glDeleteProgram(program);
        } catch (RuntimeException | Error cleanupFailure) {
            handleCleanupFailure(primaryFailure, cleanupFailure,
                "Failed to delete incomplete program " + name);
        }
    }

    private static void handleCleanupFailure(Throwable primaryFailure, Throwable cleanupFailure, String message) {
        if (primaryFailure != null) {
            primaryFailure.addSuppressed(cleanupFailure);
        }

        LOGGER.debug(message, cleanupFailure);
    }
}
