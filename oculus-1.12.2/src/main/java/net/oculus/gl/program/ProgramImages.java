package net.oculus.gl.program;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

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

    private List<GlUniform1iCall> initializer;
    private final List<ImageBinding> imageBindings;

    private ProgramImages(List<ImageBinding> imageBindings, List<GlUniform1iCall> initializer) {
        this.imageBindings = imageBindings;
        this.initializer = initializer;
    }

    public void update() {
        if (initializer != null) {
            for (GlUniform1iCall call : initializer) {
                call.apply();
            }
            initializer = null;
        }

        for (ImageBinding binding : imageBindings) {
            binding.update();
        }
    }

    public int getActiveImages() {
        return imageBindings.size();
    }

    public static Builder builder(int program) {
        return new Builder(program);
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
        private final int maxImageUnits;
        private int nextImageUnit;
        private boolean warnedMissingSupport;

        private Builder(int programId) {
            this.programId = programId;
            this.bindings = new ArrayList<>();
            this.uniformInitializers = new ArrayList<>();
            this.maxImageUnits = ImageLimits.get().getMaxImageUnits();
        }

        @Override
        public boolean hasImage(String name) {
            if (name == null) {
                return false;
            }
            return OculusRenderSystem.glGetUniformLocation(programId, name) >= 0;
        }

        @Override
        public void addTextureImage(IntSupplier textureId, InternalTextureFormat internalFormat, String name) {
            Objects.requireNonNull(textureId, "textureId");
            Objects.requireNonNull(internalFormat, "internalFormat");
            Objects.requireNonNull(name, "name");

            if (maxImageUnits <= 0) {
                if (!warnedMissingSupport) {
                    LOGGER.warn("Image uniforms requested for program {}, but the current platform does not support image load/store.", programId);
                    warnedMissingSupport = true;
                }
                return;
            }

            int location = OculusRenderSystem.glGetUniformLocation(programId, name);
            if (location < 0) {
                LOGGER.debug("Program {} does not define image uniform {}", programId, name);
                return;
            }

            if (nextImageUnit >= maxImageUnits) {
                throw new IllegalStateException("No more available image units while activating " + name + ". Only " + maxImageUnits + " unit(s) are available.");
            }

            bindings.add(new ImageBinding(nextImageUnit, internalFormat.getGlFormat(), textureId));
            uniformInitializers.add(new GlUniform1iCall(location, nextImageUnit));
            nextImageUnit++;
        }

        public ProgramImages build() {
            return new ProgramImages(Collections.unmodifiableList(new ArrayList<>(bindings)),
                uniformInitializers.isEmpty() ? null : new ArrayList<>(uniformInitializers));
        }
    }
}
