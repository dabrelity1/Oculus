package net.oculus.gl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import net.oculus.gl.shader.ShaderType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

/**
 * Lightweight shim that emulates the 1.16.5 {@code IrisRenderSystem} entry points while
 * delegating to the LWJGL 2 OpenGL bindings available on 1.12.2.
 */
public final class OculusRenderSystem {
    private OculusRenderSystem() {
    }

    public static int glCreateShader(int type) {
        int handle = GL20.glCreateShader(type);
        if (handle == 0) {
            throw new IllegalStateException("Failed to create shader object (type=" + type + ")");
        }
        return handle;
    }

    public static int createShader(ShaderType type) {
        return glCreateShader(type.id);
    }

    public static void glDeleteShader(int shaderId) {
        if (shaderId != 0) {
            GL20.glDeleteShader(shaderId);
        }
    }

    public static void deleteShader(int shaderId) {
        glDeleteShader(shaderId);
    }

    public static void glCompileShader(int shaderId) {
        GL20.glCompileShader(shaderId);
    }

    public static void compileShader(int shaderId) {
        glCompileShader(shaderId);
    }

    public static void glShaderSource(int shaderId, String source) {
        GL20.glShaderSource(shaderId, source);
    }

    public static int glGetShaderi(int shaderId, int pname) {
        return GL20.glGetShaderi(shaderId, pname);
    }

    public static int getShaderParameter(int shaderId, int parameter) {
        return glGetShaderi(shaderId, parameter);
    }

    public static int glCreateProgram() {
        int handle = GL20.glCreateProgram();
        if (handle == 0) {
            throw new IllegalStateException("Failed to create shader program");
        }
        return handle;
    }

    public static int createProgram() {
        return glCreateProgram();
    }

    public static void glDeleteProgram(int programId) {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
    }

    public static void deleteProgram(int programId) {
        glDeleteProgram(programId);
    }

    public static void glAttachShader(int programId, int shaderId) {
        GL20.glAttachShader(programId, shaderId);
    }

    public static void attachShader(int programId, int shaderId) {
        glAttachShader(programId, shaderId);
    }

    public static void glDetachShader(int programId, int shaderId) {
        GL20.glDetachShader(programId, shaderId);
    }

    public static void detachShader(int programId, int shaderId) {
        glDetachShader(programId, shaderId);
    }

    public static void glLinkProgram(int programId) {
        GL20.glLinkProgram(programId);
    }

    public static void linkProgram(int programId) {
        glLinkProgram(programId);
    }

    public static int glGetProgrami(int programId, int pname) {
        return GL20.glGetProgrami(programId, pname);
    }

    public static int getProgramParameter(int programId, int parameter) {
        return glGetProgrami(programId, parameter);
    }

    public static void bindAttributeLocation(int programId, int index, String name) {
        GL20.glBindAttribLocation(programId, index, name);
    }

    public static String glGetProgramInfoLog(int programId, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        String log = GL20.glGetProgramInfoLog(programId, maxLength);
        return log == null ? "" : log.trim();
    }

    public static String getProgramInfoLog(int programId) {
        int length = glGetProgrami(programId, GL20.GL_INFO_LOG_LENGTH);
        return glGetProgramInfoLog(programId, length);
    }

    public static String glGetShaderInfoLog(int shaderId, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        String log = GL20.glGetShaderInfoLog(shaderId, maxLength);
        return log == null ? "" : log.trim();
    }

    public static String getShaderInfoLog(int shaderId) {
        int length = glGetShaderi(shaderId, GL20.GL_INFO_LOG_LENGTH);
        return glGetShaderInfoLog(shaderId, length);
    }

    public static void glUseProgram(int programId) {
        GL20.glUseProgram(programId);
    }

    public static void useProgram(int programId) {
        glUseProgram(programId);
    }

    public static int glGetUniformLocation(int programId, String name) {
        if (name == null) {
            throw new IllegalArgumentException("Uniform name cannot be null");
        }
        return GL20.glGetUniformLocation(programId, name);
    }

    public static int getUniformLocation(int programId, String name) {
        return glGetUniformLocation(programId, name);
    }

    public static String getActiveUniform(int program, int index) {
        int maxLength = glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
        if (maxLength <= 0) {
            return "";
        }

        IntBuffer lengthBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);
        ByteBuffer nameBuffer = BufferUtils.createByteBuffer(maxLength);

        GL20.glGetActiveUniform(program, index, lengthBuffer, sizeBuffer, typeBuffer, nameBuffer);

        int nameLength = lengthBuffer.get(0);
        if (nameLength <= 0) {
            return "";
        }

        byte[] bytes = new byte[nameLength];
        for (int i = 0; i < nameLength; i++) {
            bytes[i] = nameBuffer.get(i);
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }
}
