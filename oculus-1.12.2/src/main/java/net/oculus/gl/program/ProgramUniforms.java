package net.oculus.gl.program;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.oculus.gl.OculusRenderSystem;
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

    private final String programName;
    private final List<UniformBinding> bindings;

    private ProgramUniforms(String programName, List<UniformBinding> bindings) {
        this.programName = programName;
        this.bindings = bindings;
    }

    public void update() {
        active = this;
        for (UniformBinding binding : bindings) {
            binding.upload();
        }
    }

    public static void clearActiveUniforms() {
        active = null;
    }

    public static Builder builder(String programName, int programId) {
        return new Builder(programName, programId);
    }

    private static final class UniformBinding {
        private final String programName;
        private final String uniformName;
        private final int location;
        private final UniformUpdater updater;

        private UniformBinding(String programName, String uniformName, int location, UniformUpdater updater) {
            this.programName = programName;
            this.uniformName = uniformName;
            this.location = location;
            this.updater = updater;
        }

        private void upload() {
            try {
                updater.upload(location);
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to upload uniform {} for program {}", uniformName, programName, ex);
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
            Objects.requireNonNull(uniformName, "uniformName");
            Objects.requireNonNull(updater, "updater");

            int location = findLocation(uniformName);
            if (location < 0) {
                return this;
            }

            bindings.add(new UniformBinding(programName, uniformName, location, updater));
            return this;
        }

        public Builder addFloat(String uniformName, FloatSupplier supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> GL20.glUniform1f(location, supplier.getAsFloat()));
        }

        public Builder addFloatSupplier(String uniformName, Supplier<Float> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> {
                Float value = supplier.get();
                if (value != null) {
                    GL20.glUniform1f(location, value);
                }
            });
        }

        public Builder addInt(String uniformName, IntSupplier supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return register(uniformName, location -> GL20.glUniform1i(location, supplier.getAsInt()));
        }

        public Builder addVec2(String uniformName, Supplier<float[]> supplier) {
            return registerVecUniform(uniformName, supplier, 2);
        }

        public Builder addVec3(String uniformName, Supplier<float[]> supplier) {
            return registerVecUniform(uniformName, supplier, 3);
        }

        public Builder addVec4(String uniformName, Supplier<float[]> supplier) {
            return registerVecUniform(uniformName, supplier, 4);
        }

        public Builder addMatrix4(String uniformName, Supplier<float[]> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            return register(uniformName, location -> {
                float[] values = supplier.get();
                if (values == null) {
                    return;
                }

                buffer.clear();
                int length = Math.min(values.length, 16);
                buffer.put(values, 0, length);
                if (length < 16) {
                    for (int i = length; i < 16; i++) {
                        buffer.put(0.0F);
                    }
                }
                buffer.flip();
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
            });
        }

        private int findLocation(String uniformName) {
            int location = OculusRenderSystem.glGetUniformLocation(programId, uniformName);
            if (location < 0) {
                LOGGER.debug("Program {} does not define uniform {}", programName, uniformName);
            }
            return location;
        }

        public ProgramUniforms build() {
            return new ProgramUniforms(programName, Collections.unmodifiableList(new ArrayList<>(bindings)));
        }
    }
}
