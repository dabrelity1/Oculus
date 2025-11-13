package net.oculus.gl.program;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import net.oculus.gl.OculusRenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * Tracks sampler uniforms for a program, ensuring that textures are bound to the correct
 * units and that the shader knows which unit to sample from.
 */
public final class ProgramSamplers {
    private static final Logger LOGGER = LogManager.getLogger(ProgramSamplers.class);

    private static ProgramSamplers active;

    private final String programName;
    private final List<SamplerBinding> bindings;
    private final int activeSamplerCount;

    private ProgramSamplers(String programName, List<SamplerBinding> bindings, int activeSamplerCount) {
        this.programName = programName;
        this.bindings = bindings;
        this.activeSamplerCount = activeSamplerCount;
    }

    public void update() {
        active = this;
        for (SamplerBinding binding : bindings) {
            binding.bind();
        }
    }

    public static void clearActiveSamplers() {
        if (active != null) {
            active.unbind();
            active = null;
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    public int getActiveSamplers() {
        return activeSamplerCount;
    }

    private void unbind() {
        for (SamplerBinding binding : bindings) {
            binding.unbind();
        }
    }

    public static Builder builder(String programName, int programId) {
        return new Builder(programName, programId);
    }

    private static final class SamplerBinding {
        private final String programName;
        private final String uniformName;
        private final int location;
        private final int unit;
        private final int target;

        private SamplerBinding(String programName, String uniformName, int location, int unit, int target) {
            this.programName = programName;
            this.uniformName = uniformName;
            this.location = location;
            this.unit = unit;
            this.target = target;
        }

        private void bind() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL20.glUniform1i(location, unit);
        }

        private void unbind() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(target, 0);
        }
    }

    public static final class Builder {
        private final String programName;
        private final int programId;
        private final List<SamplerBinding> bindings;
        private final Set<String> registeredSamplers;
        private int highestUnit;

        private Builder(String programName, int programId) {
            this.programName = Objects.requireNonNull(programName, "programName");
            this.programId = programId;
            this.bindings = new ArrayList<>();
            this.registeredSamplers = new HashSet<>();
            this.highestUnit = -1;
        }

        public Builder addSampler(String uniformName) {
            Objects.requireNonNull(uniformName, "uniformName");

            if (!registeredSamplers.add(uniformName)) {
                return this;
            }

            int unit = resolveUnit(uniformName);
            if (unit < 0) {
                LOGGER.warn("Unknown sampler {} will not be bound.", uniformName);
                return this;
            }

            int location = OculusRenderSystem.glGetUniformLocation(programId, uniformName);
            if (location < 0) {
                LOGGER.debug("Program {} does not define sampler uniform {}", programName, uniformName);
                return this;
            }

            bindings.add(new SamplerBinding(programName, uniformName, location, unit, GL11.GL_TEXTURE_2D));
            if (unit > highestUnit) {
                highestUnit = unit;
            }
            return this;
        }

        public ProgramSamplers build() {
            int activeSamplers = highestUnit + 1;
            return new ProgramSamplers(programName, Collections.unmodifiableList(new ArrayList<>(bindings)), activeSamplers);
        }

        private int resolveUnit(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "gtexture0":
                case "s_texture":
                    return 0;
                case "gtexture1":
                case "s_normal":
                    return 1;
                case "gtexture2":
                case "s_specular":
                    return 2;
                case "shadow":
                case "s_shadow":
                    return 5;
                case "shadowcolor":
                case "s_shadowcolor":
                    return 6;
                default:
                    return -1;
            }
        }
    }
}
