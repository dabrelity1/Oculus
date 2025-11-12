package net.oculus.gl.shader;

import net.oculus.gl.GLDebug;
import net.oculus.gl.OculusRenderSystem;

/**
 * Skeleton link helper that mirrors the 1.16.5 entry point. It hands out synthetic program
 * identifiers and records calls so the per-stage shader wrappers can stay API-compatible.
 */
public final class ProgramCreator {
    private ProgramCreator() {
    }

    public static int create(String name, GlShader... shaders) {
        int program = OculusRenderSystem.createProgram();

        // Bind the slots we know we will use once the full attribute pipeline lands.
        OculusRenderSystem.bindAttributeLocation(program, 11, "mc_Entity");
        OculusRenderSystem.bindAttributeLocation(program, 12, "mc_midTexCoord");
        OculusRenderSystem.bindAttributeLocation(program, 13, "at_tangent");
        OculusRenderSystem.bindAttributeLocation(program, 14, "at_midBlock");

        for (GlShader shader : shaders) {
            OculusRenderSystem.attachShader(program, shader.getHandle());
        }

        OculusRenderSystem.linkProgram(program);
        GLDebug.nameObject(0x8B82, program, name);

        for (GlShader shader : shaders) {
            OculusRenderSystem.detachShader(program, shader.getHandle());
        }

        return program;
    }
}
