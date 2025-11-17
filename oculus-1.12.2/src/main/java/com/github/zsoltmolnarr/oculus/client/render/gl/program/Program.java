package com.github.zsoltmolnarr.oculus.client.render.gl.program;

import java.util.HashMap;
import java.util.Map;

import net.oculus.gl.OculusRenderSystem;
import org.lwjgl.opengl.GL20;

/**
 * Lightweight shader program wrapper used by the new framebuffer pipeline.
 */
public final class Program {
    private final int programId;
    private final String debugName;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    Program(String debugName, int programId) {
        this.debugName = debugName;
        this.programId = programId;
    }

    public void use() {
        OculusRenderSystem.glUseProgram(programId);
    }

    public static void unbind() {
        OculusRenderSystem.glUseProgram(0);
    }

    public Uniform uniform(String name) {
        return new Uniform(this, name);
    }

    void setUniform1i(String name, int value) {
        int location = uniformLocations.computeIfAbsent(name, n -> GL20.glGetUniformLocation(programId, n));
        if (location >= 0) {
            GL20.glUniform1i(location, value);
        }
    }

    void destroy() {
        OculusRenderSystem.glDeleteProgram(programId);
    }

    int getProgramId() {
        return programId;
    }

    String getDebugName() {
        return debugName;
    }
}
