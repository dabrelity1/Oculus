package net.oculus.gl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import net.oculus.Oculus;
import net.oculus.gl.shader.ShaderType;
import net.oculus.vendored.joml.Vector3i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBComputeShader;
import org.lwjgl.opengl.EXTShaderImageLoadStore;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

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

    public static boolean supportsCompute() {
        return GLContext.getCapabilities().OpenGL43 || GLContext.getCapabilities().GL_ARB_compute_shader;
    }

    private static boolean supportsImageLoadStore() {
        return GLContext.getCapabilities().OpenGL42 || GLContext.getCapabilities().GL_EXT_shader_image_load_store;
    }

    public static void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        if (!supportsImageLoadStore()) {
            Oculus.LOGGER.warn("Image load/store not supported; skipping bind for texture {}", texture);
            return;
        }

        if (GLContext.getCapabilities().OpenGL42) {
            GL42.glBindImageTexture(unit, texture, level, layered, layer, access, format);
        } else {
            EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
        }
    }

    public static int getMaxImageUnits() {
        if (!supportsImageLoadStore()) {
            return 0;
        }

        if (GLContext.getCapabilities().OpenGL42) {
            return GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS);
        }

        return GL11.glGetInteger(EXTShaderImageLoadStore.GL_MAX_IMAGE_UNITS_EXT);
    }

    public static void dispatchCompute(int workX, int workY, int workZ) {
        if (!supportsCompute()) {
            Oculus.LOGGER.warn("Compute shaders are not supported on this platform; dispatch skipped.");
            return;
        }

        if (GLContext.getCapabilities().OpenGL43) {
            GL43.glDispatchCompute(workX, workY, workZ);
        } else {
            ARBComputeShader.glDispatchCompute(workX, workY, workZ);
        }
    }

    public static void dispatchCompute(Vector3i workGroups) {
        dispatchCompute(workGroups.x(), workGroups.y(), workGroups.z());
    }

    public static void memoryBarrier(int barriers) {
        if (!supportsImageLoadStore()) {
            return;
        }

        if (GLContext.getCapabilities().OpenGL42) {
            GL42.glMemoryBarrier(barriers);
        } else {
            EXTShaderImageLoadStore.glMemoryBarrierEXT(barriers);
        }
    }

    public static void getProgramiv(int program, int pname, IntBuffer params) {
        GL20.glGetProgram(program, pname, params);
    }
}
