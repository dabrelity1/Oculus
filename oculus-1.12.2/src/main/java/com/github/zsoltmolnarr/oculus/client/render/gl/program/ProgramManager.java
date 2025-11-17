package com.github.zsoltmolnarr.oculus.client.render.gl.program;

import java.util.HashMap;
import java.util.Map;

import net.oculus.Oculus;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Tiny shader program manager for internal fullscreen passes.
 */
public final class ProgramManager {
    private final Map<String, Program> programs = new HashMap<>();

    public Program getOrCreate(ProgramDefinition definition) {
        String key = definition.getName();
        return programs.computeIfAbsent(key, k -> compile(definition));
    }

    public void destroyAll() {
        programs.values().forEach(Program::destroy);
        programs.clear();
    }

    private Program compile(ProgramDefinition definition) {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, definition.getVertexSource());
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, definition.getFragmentSource());
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);

        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("Failed to link program " + definition.getName() + ": " + GL20.glGetProgramInfoLog(program, 32768));
        }

        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        return new Program(definition.getName(), program);
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetShaderInfoLog(shader, 32768);
            Oculus.LOGGER.error("Shader compilation failed: {}", message);
            throw new IllegalStateException(message);
        }

        return shader;
    }
}
