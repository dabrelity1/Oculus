package net.oculus.gl.program;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.state.ValueUpdateNotifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

/**
 * Tracks uniform bindings for a shader program and uploads their values whenever the
 * program is activated. The implementation mirrors the responsibilities of the 1.16.x
 * pipeline while remaining lightweight for the 1.12.2 backport.
 */
public final class ProgramUniforms {
    private static final Logger LOGGER = LogManager.getLogger(ProgramUniforms.class);

    private static ProgramUniforms active;

    private final List<UniformBinding> bindings;

    private ProgramUniforms(List<UniformBinding> bindings) {
        this.bindings = bindings;
    }

    public void update() {
        Throwable previousCleanupFailure = cleanupActiveBeforeUpdate();

        active = this;
        try {
            attachListeners();
            for (UniformBinding binding : bindings) {
                binding.upload();
            }
        } catch (RuntimeException exception) {
            suppressCleanupFailure(exception, previousCleanupFailure);
            cleanupAfterFailedUpdate(exception);
            throw exception;
        } catch (Error error) {
            suppressCleanupFailure(error, previousCleanupFailure);
            cleanupAfterFailedUpdate(error);
            throw error;
        }
        rethrowCleanupFailure(previousCleanupFailure);
    }

    public static void clearActiveUniforms() {
        ProgramUniforms current = active;
        if (current != null) {
            Throwable failure = null;
            try {
                failure = runCleanup(failure, current::removeListeners);
            } finally {
                active = null;
            }
            rethrowCleanupFailure(failure);
        }
    }

    static void clearActiveUniforms(ProgramUniforms uniforms) {
        if (active == uniforms) {
            clearActiveUniforms();
        }
    }

    public static Builder builder(String programName, int programId) {
        return new Builder(programName, programId);
    }

    private static final class UniformBinding {
        private final int location;
        private final UniformUpdater updater;
        private final ValueUpdateNotifier notifier;
        private final Runnable listener;

        private UniformBinding(int location, UniformUpdater updater, ValueUpdateNotifier notifier) {
            this.location = location;
            this.updater = updater;
            this.notifier = notifier;
            this.listener = notifier == null ? null : this::upload;
        }

        private void upload() {
            updater.upload(location);
        }

        private void attachListener() {
            if (notifier != null) {
                notifier.setListener(listener);
            }
        }

        private void detachListener() {
            if (notifier != null) {
                notifier.removeListener(listener);
            }
        }
    }

    @FunctionalInterface
    public interface UniformUpdater {
        void upload(int location);
    }

    @FunctionalInterface
    public interface FloatSupplier {
        float getAsFloat();
    }

    public static final class Builder {
        private final String programName;
        private final int programId;
        private final List<UniformBinding> bindings;

        private Builder(String programName, int programId) {
            this.programName = Objects.requireNonNull(programName, "programName");
            this.programId = programId;
            this.bindings = new ArrayList<>();
        }

        public Builder register(String uniformName, UniformUpdater updater) {
            return register(uniformName, updater, null);
        }

        public Builder register(String uniformName, UniformUpdater updater, ValueUpdateNotifier notifier) {
            Objects.requireNonNull(uniformName, "uniformName");
            Objects.requireNonNull(updater, "updater");

            int location = findLocation(uniformName);
            if (location < 0) {
                return this;
            }

            bindings.add(new UniformBinding(location, updater, notifier));
            return this;
        }

        public Builder addFloat(String uniformName, FloatSupplier supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> GL20.glUniform1f(location, supplier.getAsFloat()));
        }

        public Builder addFloatSupplier(String uniformName, Supplier<Float> supplier) {
            return addFloatSupplier(uniformName, supplier, null);
        }

        public Builder addFloatSupplier(String uniformName, Supplier<Float> supplier, ValueUpdateNotifier notifier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                Float value = supplier.get();
                if (value != null) {
                    GL20.glUniform1f(location, value);
                }
            }, notifier);
        }

        public Builder addInt(String uniformName, IntSupplier supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> GL20.glUniform1i(location, supplier.getAsInt()));
        }

        public Builder addInt(String uniformName, IntSupplier supplier, ValueUpdateNotifier notifier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> GL20.glUniform1i(location, supplier.getAsInt()), notifier);
        }

        public Builder addIntVec2(String uniformName, Supplier<int[]> supplier) {
            return addIntVec2(uniformName, supplier, null);
        }

        public Builder addIntVec2(String uniformName, Supplier<int[]> supplier, ValueUpdateNotifier notifier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                int[] values = supplier.get();
                if (values == null || values.length < 2) {
                    return;
                }
                GL20.glUniform2i(location, values[0], values[1]);
            }, notifier);
        }

        public Builder addIntVec3(String uniformName, Supplier<int[]> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                int[] values = supplier.get();
                if (values == null || values.length < 3) {
                    return;
                }
                GL20.glUniform3i(location, values[0], values[1], values[2]);
            });
        }

        public Builder addIntVec4(String uniformName, Supplier<int[]> supplier) {
            return addIntVec4(uniformName, supplier, null);
        }

        public Builder addIntVec4(String uniformName, Supplier<int[]> supplier, ValueUpdateNotifier notifier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                int[] values = supplier.get();
                if (values == null || values.length < 4) {
                    return;
                }
                GL20.glUniform4i(location, values[0], values[1], values[2], values[3]);
            }, notifier);
        }

        public Builder addVec2(String uniformName, Supplier<float[]> supplier) {
            return addVec2(uniformName, supplier, null);
        }

        public Builder addVec2(String uniformName, Supplier<float[]> supplier, ValueUpdateNotifier notifier) {
            return registerVecUniform(uniformName, supplier, 2, notifier);
        }

        public Builder addVec3(String uniformName, Supplier<float[]> supplier) {
            return addVec3(uniformName, supplier, null);
        }

        public Builder addVec3(String uniformName, Supplier<float[]> supplier, ValueUpdateNotifier notifier) {
            return registerVecUniform(uniformName, supplier, 3, notifier);
        }

        public Builder addVec4(String uniformName, Supplier<float[]> supplier) {
            return addVec4(uniformName, supplier, null);
        }

        public Builder addVec4(String uniformName, Supplier<float[]> supplier, ValueUpdateNotifier notifier) {
            return registerVecUniform(uniformName, supplier, 4, notifier);
        }

        public Builder addMatrix4(String uniformName, Supplier<float[]> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            return register(uniformName, location -> {
                float[] values = supplier.get();
                if (values == null) {
                    return;
                }

                ((Buffer) buffer).clear();
                int length = Math.min(values.length, 16);
                buffer.put(values, 0, length);
                if (length < 16) {
                    for (int i = length; i < 16; i++) {
                        buffer.put(0.0F);
                    }
                }
                ((Buffer) buffer).flip();
                GL20.glUniformMatrix4(location, false, buffer);
            });
        }

        public Builder addVec3f(String uniformName, Supplier<FloatBuffer> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                FloatBuffer buffer = supplier.get();
                if (buffer == null || buffer.limit() < 3) {
                    return;
                }
                GL20.glUniform3f(location, buffer.get(0), buffer.get(1), buffer.get(2));
            });
        }

        public Builder addVec4f(String uniformName, Supplier<FloatBuffer> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                FloatBuffer buffer = supplier.get();
                if (buffer == null || buffer.limit() < 4) {
                    return;
                }
                GL20.glUniform4f(location, buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3));
            });
        }

        public Builder addMat4f(String uniformName, Supplier<FloatBuffer> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                FloatBuffer buffer = supplier.get();
                if (buffer == null) {
                    return;
                }
                GL20.glUniformMatrix4(location, false, buffer);
            });
        }

        private Builder registerVecUniform(String uniformName, Supplier<float[]> supplier, int componentCount) {
            return registerVecUniform(uniformName, supplier, componentCount, null);
        }

        private Builder registerVecUniform(String uniformName, Supplier<float[]> supplier, int componentCount,
                                           ValueUpdateNotifier notifier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                float[] values = supplier.get();
                if (values == null || values.length < componentCount) {
                    return;
                }

                switch (componentCount) {
                    case 2:
                        GL20.glUniform2f(location, values[0], values[1]);
                        break;
                    case 3:
                        GL20.glUniform3f(location, values[0], values[1], values[2]);
                        break;
                    case 4:
                        GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported vector size: " + componentCount);
                }
            }, notifier);
        }

        private int findLocation(String uniformName) {
            int location = OculusRenderSystem.glGetUniformLocation(programId, uniformName);
            if (location < 0) {
                LOGGER.debug("Program {} does not define uniform {}", programName, uniformName);
            }
            return location;
        }

        public ProgramUniforms build() {
            return new ProgramUniforms(Collections.unmodifiableList(new ArrayList<>(bindings)));
        }
    }

    private void attachListeners() {
        for (UniformBinding binding : bindings) {
            binding.attachListener();
        }
    }

    private void removeListeners() {
        Throwable failure = null;
        for (UniformBinding binding : bindings) {
            failure = runCleanup(failure, binding::detachListener);
        }
        rethrowCleanupFailure(failure);
    }

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            if (failure != null) {
                suppressCleanupFailure(failure, exception);
                return failure;
            }
            return exception;
        }
        return failure;
    }

    private static void rethrowCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }

    private static Throwable cleanupActiveBeforeUpdate() {
        ProgramUniforms current = active;
        if (current == null) {
            return null;
        }
        return runCleanup(null, current::removeListeners);
    }

    private static void cleanupAfterFailedUpdate(Throwable failure) {
        try {
            clearActiveUniforms();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
        }
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
