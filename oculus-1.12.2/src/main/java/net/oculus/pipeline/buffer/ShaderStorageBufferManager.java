package net.oculus.pipeline.buffer;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.oculus.Oculus;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.shaderpack.ShaderProperties;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * Owns shader storage buffer objects declared with OptiFine-style
 * {@code bufferObject.<binding> = <byteSize>} properties.
 */
public final class ShaderStorageBufferManager {
    private static final int TARGET = ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER;
    private static final int ZERO_CLEAR_CHUNK_BYTES = 1024 * 1024;

    private final Map<Integer, Long> requestedSizes;
    private final BooleanSupplier storageBufferSupport;
    private final Map<Integer, Integer> buffers = new LinkedHashMap<>();
    private boolean initialized;

    public ShaderStorageBufferManager(ShaderProperties properties) {
        this(properties, OculusRenderSystem::supportsShaderStorageBuffers);
    }

    ShaderStorageBufferManager(ShaderProperties properties, BooleanSupplier storageBufferSupport) {
        this.requestedSizes = properties == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(properties.getShaderStorageBufferSizes());
        this.storageBufferSupport = storageBufferSupport == null
            ? OculusRenderSystem::supportsShaderStorageBuffers
            : storageBufferSupport;
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        if (requestedSizes.isEmpty()) {
            initialized = true;
            return;
        }

        if (!storageBufferSupport.getAsBoolean()) {
            throw unsupportedException();
        }

        try {
            int maxBindings = GL11.glGetInteger(ARBShaderStorageBufferObject.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
            for (Map.Entry<Integer, Long> entry : requestedSizes.entrySet()) {
                int binding = entry.getKey();
                long size = entry.getValue();

                if (binding < 0 || binding >= maxBindings) {
                    throw new IllegalStateException("Shader pack requested shader storage buffer binding " + binding
                        + ", but only " + maxBindings + " binding(s) are supported.");
                }

                if (size <= 0L) {
                    throw new IllegalStateException("Shader pack requested shader storage buffer binding " + binding
                        + " with invalid size " + size + ".");
                }

                int buffer = GL15.glGenBuffers();
                if (buffer <= 0) {
                    throw new IllegalStateException("Failed to allocate shader storage buffer at binding " + binding + ".");
                }

                buffers.put(binding, buffer);
                int previousBuffer = GL11.glGetInteger(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER_BINDING);
                GL15.glBindBuffer(TARGET, buffer);
                Throwable setupFailure = null;
                try {
                    GL15.glBufferData(TARGET, size, GL15.GL_DYNAMIC_DRAW);
                    clearBoundBuffer(size);
                    GL30.glBindBufferBase(TARGET, binding, buffer);
                } catch (RuntimeException | Error exception) {
                    setupFailure = exception;
                    throw exception;
                } finally {
                    restoreGenericBufferBinding(previousBuffer, setupFailure);
                }
            }
        } catch (RuntimeException | Error ex) {
            destroy();
            throw ex;
        }
        initialized = true;
    }

    public void bindAll() {
        if (!initialized) {
            initialize();
        }

        if (buffers.isEmpty()) {
            return;
        }

        if (!storageBufferSupport.getAsBoolean()) {
            throw unsupportedException();
        }

        int previousBuffer = GL11.glGetInteger(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER_BINDING);
        Throwable bindingFailure = null;
        try {
            for (Map.Entry<Integer, Integer> entry : buffers.entrySet()) {
                GL30.glBindBufferBase(TARGET, entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException | Error exception) {
            bindingFailure = exception;
            throw exception;
        } finally {
            restoreGenericBufferBinding(previousBuffer, bindingFailure);
        }
    }

    public void destroy() {
        try {
            for (Integer buffer : buffers.values()) {
                if (buffer != null) {
                    deleteBuffer(buffer);
                }
            }
        } finally {
            buffers.clear();
            initialized = false;
        }
    }

    private static void deleteBuffer(int buffer) {
        if (buffer <= 0) {
            return;
        }

        try {
            GL15.glDeleteBuffers(buffer);
        } catch (RuntimeException | Error exception) {
            Oculus.LOGGER.debug("Failed to delete shader storage buffer {}", buffer, exception);
        }
    }

    private static void restoreGenericBufferBinding(int previousBuffer, Throwable primaryFailure) {
        try {
            GL15.glBindBuffer(TARGET, previousBuffer);
        } catch (RuntimeException | Error restoreFailure) {
            if (primaryFailure != null) {
                suppressFailure(primaryFailure, restoreFailure);
                return;
            }
            throw restoreFailure;
        }
    }

    private static void suppressFailure(Throwable failure, Throwable exception) {
        if (failure != exception) {
            failure.addSuppressed(exception);
        }
    }

    private void clearBoundBuffer(long size) {
        if (size <= 0L) {
            return;
        }

        ByteBuffer zero = BufferUtils.createByteBuffer(4);
        if (OculusRenderSystem.clearBufferData(TARGET, GL30.GL_R32UI, GL30.GL_RED_INTEGER,
            GL11.GL_UNSIGNED_INT, zero)) {
            return;
        }

        ByteBuffer zeros = BufferUtils.createByteBuffer((int) Math.min(ZERO_CLEAR_CHUNK_BYTES, size));
        long offset = 0L;
        while (offset < size) {
            int length = (int) Math.min(zeros.capacity(), size - offset);
            ((Buffer) zeros).position(0);
            ((Buffer) zeros).limit(length);
            GL15.glBufferSubData(TARGET, offset, zeros);
            offset += length;
        }
        ((Buffer) zeros).clear();
    }

    private static IllegalStateException unsupportedException() {
        return new IllegalStateException("Shader pack declares shader storage buffers, but SSBOs are not supported on this platform.");
    }

    boolean isInitialized() {
        return initialized;
    }
}
