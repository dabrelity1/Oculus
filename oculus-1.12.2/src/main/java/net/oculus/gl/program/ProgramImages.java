package net.oculus.gl.program;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.ToIntBiFunction;

import net.oculus.gl.image.ImageBinding;
import net.oculus.gl.image.ImageHolder;
import net.oculus.gl.image.ImageLimits;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.gl.OculusRenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20;

/**
 * Tracks image uniforms for a shader program. Shader packs can bind render targets or
 * shadow buffers to image units and this class will update the bindings every frame.
 */
public final class ProgramImages {
    private static final Logger LOGGER = LogManager.getLogger(ProgramImages.class);

    private static ProgramImages active;

    private List<GlUniform1iCall> initializer;
    private final List<ImageBinding> imageBindings;

    private ProgramImages(List<ImageBinding> imageBindings, List<GlUniform1iCall> initializer) {
        this.imageBindings = imageBindings;
        this.initializer = initializer;
    }

    public void update() {
        Throwable previousCleanupFailure = cleanupPreviousActiveBeforeUpdate();

        active = this;
        try {
            if (initializer != null) {
                for (GlUniform1iCall call : initializer) {
                    call.apply();
                }
                initializer = null;
            }

            for (ImageBinding binding : imageBindings) {
                binding.update();
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

    public int getActiveImages() {
        return imageBindings.size();
    }

    public static void clearActiveImages() {
        ProgramImages current = active;
        if (current != null) {
            Throwable failure = null;
            try {
                failure = runCleanup(failure, current::unbind);
            } finally {
                active = null;
            }
            rethrowCleanupFailure(failure);
        }
    }

    static void clearActiveImages(ProgramImages images) {
        if (active == images) {
            clearActiveImages();
        }
    }

    private void unbind() {
        Throwable failure = null;
        for (ImageBinding binding : imageBindings) {
            failure = runCleanup(failure, binding::unbind);
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

    private Throwable cleanupPreviousActiveBeforeUpdate() {
        ProgramImages current = active;
        if (current == null || current == this) {
            return null;
        }
        return runCleanup(null, current::unbind);
    }

    private static void cleanupAfterFailedUpdate(Throwable failure) {
        try {
            clearActiveImages();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
        }
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    public static Builder builder(int program) {
        return new Builder(program, () -> ImageLimits.get().getMaxImageUnits(),
            OculusRenderSystem::glGetUniformLocation);
    }

    static Builder builder(int program, IntSupplier maxImageUnitsSupplier,
                           ToIntBiFunction<Integer, String> uniformLocationResolver) {
        return new Builder(program, maxImageUnitsSupplier, uniformLocationResolver);
    }

    private static final class GlUniform1iCall {
        private final int location;
        private final int value;

        private GlUniform1iCall(int location, int value) {
            this.location = location;
            this.value = value;
        }

        private void apply() {
            GL20.glUniform1i(location, value);
        }
    }

    public static final class Builder implements ImageHolder {
        private final int programId;
        private final List<ImageBinding> bindings;
        private final List<GlUniform1iCall> uniformInitializers;
        private final ToIntBiFunction<Integer, String> uniformLocationResolver;
        private final int maxImageUnits;
        private int nextImageUnit;

        private Builder(int programId, IntSupplier maxImageUnitsSupplier,
                        ToIntBiFunction<Integer, String> uniformLocationResolver) {
            this.programId = programId;
            this.bindings = new ArrayList<>();
            this.uniformInitializers = new ArrayList<>();
            this.uniformLocationResolver = Objects.requireNonNull(uniformLocationResolver, "uniformLocationResolver");
            this.maxImageUnits = Math.max(0, Objects.requireNonNull(maxImageUnitsSupplier, "maxImageUnitsSupplier").getAsInt());
        }

        @Override
        public boolean hasImage(String name) {
            if (name == null) {
                return false;
            }
            return findLocation(name) >= 0;
        }

        @Override
        public void addTextureImage(IntSupplier textureId, InternalTextureFormat internalFormat, String name) {
            Objects.requireNonNull(textureId, "textureId");
            Objects.requireNonNull(internalFormat, "internalFormat");
            Objects.requireNonNull(name, "name");

            int location = findLocation(name);
            if (location < 0) {
                LOGGER.debug("Program {} does not define image uniform {}", programId, name);
                return;
            }

            if (nextImageUnit >= maxImageUnits) {
                if (maxImageUnits == 0) {
                    throw new IllegalStateException("Image units are not supported on this platform, but a shader program attempted to reference " + name + ".");
                }
                throw new IllegalStateException("No more available texture units while activating image " + name
                    + ". Only " + maxImageUnits + " image units are available.");
            }

            InternalTextureFormat effectiveFormat = internalFormat == InternalTextureFormat.RGBA
                ? InternalTextureFormat.RGBA8
                : internalFormat;

            bindings.add(new ImageBinding(nextImageUnit, effectiveFormat.getGlFormat(), textureId));
            uniformInitializers.add(new GlUniform1iCall(location, nextImageUnit));
            nextImageUnit++;
        }

        public ProgramImages build() {
            return new ProgramImages(Collections.unmodifiableList(new ArrayList<>(bindings)),
                uniformInitializers.isEmpty() ? null : new ArrayList<>(uniformInitializers));
        }

        private int findLocation(String name) {
            return uniformLocationResolver.applyAsInt(programId, name);
        }
    }
}
