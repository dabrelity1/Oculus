package net.oculus.compat.relictium;

import me.jellysquid.mods.sodium.client.gl.shader.ShaderBindingPoint;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.shader.GlShader;
import net.oculus.gl.shader.ShaderType;
import net.oculus.pipeline.OculusChunkShaderBindingPoints;
import net.oculus.shaderpack.ProgramLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class OculusRelictiumProgramLinker {
    private static final Logger LOGGER = LogManager.getLogger(OculusRelictiumProgramLinker.class);

    private OculusRelictiumProgramLinker() {
    }

    public static int link(String name, String vertexSource, String geometrySource, String fragmentSource) {
        GlShader vertexShader = null;
        GlShader geometryShader = null;
        GlShader fragmentShader = null;
        int program = 0;
        boolean linked = false;
        Throwable primaryFailure = null;

        try {
            vertexShader = new GlShader(ShaderType.VERTEX, name + ".vsh", vertexSource);
            if (geometrySource != null) {
                geometryShader = new GlShader(ShaderType.GEOMETRY, name + ".gsh", geometrySource);
            }
            fragmentShader = new GlShader(ShaderType.FRAGMENT, name + ".fsh", fragmentSource);

            program = OculusRenderSystem.glCreateProgram();
            bindAttributes(program);
            OculusRenderSystem.glAttachShader(program, vertexShader.getHandle());
            if (geometryShader != null) {
                OculusRenderSystem.glAttachShader(program, geometryShader.getHandle());
            }
            OculusRenderSystem.glAttachShader(program, fragmentShader.getHandle());
            OculusRenderSystem.glLinkProgram(program);

            int logLength = OculusRenderSystem.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH);
            String log = OculusRenderSystem.glGetProgramInfoLog(program, logLength);
            if (!log.isEmpty()) {
                LOGGER.warn("Relictium terrain program link log for {}: {}", name, log);
            }

            int status = OculusRenderSystem.glGetProgrami(program, GL20.GL_LINK_STATUS);
            if (status != GL11.GL_TRUE) {
                throw new ProgramLoadException("Failed to link Relictium terrain program " + name + "\n" + log);
            }

            linked = true;
            return program;
        } catch (RuntimeException | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            detach(program, vertexShader, name, primaryFailure);
            detach(program, geometryShader, name, primaryFailure);
            detach(program, fragmentShader, name, primaryFailure);
            destroy(vertexShader, name, primaryFailure);
            destroy(geometryShader, name, primaryFailure);
            destroy(fragmentShader, name, primaryFailure);
            if (!linked && program != 0) {
                deleteFailedProgram(program, name, primaryFailure);
            }
        }
    }

    private static void bindAttributes(int program) {
        bind(program, ChunkShaderBindingPoints.POSITION, "iris_Pos");
        bind(program, ChunkShaderBindingPoints.COLOR, "iris_Color");
        bind(program, ChunkShaderBindingPoints.TEX_COORD, "iris_TexCoord");
        bind(program, ChunkShaderBindingPoints.LIGHT_COORD, "iris_LightCoord");
        bind(program, ChunkShaderBindingPoints.MODEL_OFFSET, "iris_ModelOffset");
        bind(program, OculusChunkShaderBindingPoints.NORMAL, "iris_Normal");
        bind(program, OculusChunkShaderBindingPoints.BLOCK_ID, "mc_Entity");
        bind(program, OculusChunkShaderBindingPoints.MID_UV, "mc_midTexCoord");
        bind(program, OculusChunkShaderBindingPoints.TANGENT, "at_tangent");
        bind(program, OculusChunkShaderBindingPoints.MID_BLOCK, "at_midBlock");
    }

    private static void bind(int program, ShaderBindingPoint point, String attribute) {
        OculusRenderSystem.bindAttributeLocation(program, point.getGenericAttributeIndex(), attribute);
    }

    private static void detach(int program, GlShader shader, String name, Throwable primaryFailure) {
        if (program != 0 && shader != null) {
            try {
                OculusRenderSystem.glDetachShader(program, shader.getHandle());
            } catch (RuntimeException | Error cleanupFailure) {
                handleCleanupFailure(primaryFailure, cleanupFailure,
                    "Failed to detach Relictium terrain shader " + shader.getName() + " from program " + name);
            }
        }
    }

    private static void destroy(GlShader shader, String name, Throwable primaryFailure) {
        if (shader != null) {
            try {
                shader.destroy();
            } catch (RuntimeException | Error cleanupFailure) {
                handleCleanupFailure(primaryFailure, cleanupFailure,
                    "Failed to destroy Relictium terrain shader " + shader.getName() + " for program " + name);
            }
        }
    }

    private static void deleteFailedProgram(int program, String name, Throwable primaryFailure) {
        try {
            OculusRenderSystem.glDeleteProgram(program);
        } catch (RuntimeException | Error cleanupFailure) {
            handleCleanupFailure(primaryFailure, cleanupFailure,
                "Failed to delete incomplete Relictium terrain program " + name);
        }
    }

    private static void handleCleanupFailure(Throwable primaryFailure, Throwable cleanupFailure, String message) {
        if (primaryFailure != null) {
            suppressCleanupFailure(primaryFailure, cleanupFailure);
        }

        LOGGER.debug(message, cleanupFailure);
    }

    private static void suppressCleanupFailure(Throwable primaryFailure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != primaryFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }
}
