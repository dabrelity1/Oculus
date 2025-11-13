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
        } finally {
            for (GlShader shader : shaders) {
                if (program != 0) {
                    OculusRenderSystem.glDetachShader(program, shader.getHandle());
                }
            }

            if (!linked) {
                OculusRenderSystem.glDeleteProgram(program);
            }
        }
    }
}
