package net.oculus.gl.program;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
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
    private List<GlUniform1iCall> initializer;
    private final int activeSamplerCount;

    private ProgramSamplers(String programName, List<SamplerBinding> bindings, int activeSamplerCount, List<GlUniform1iCall> initializer) {
        this.programName = programName;
        this.bindings = bindings;
        this.activeSamplerCount = activeSamplerCount;
        this.initializer = initializer;
    }

    public void update() {
        active = this;
        if (initializer != null) {
            for (GlUniform1iCall call : initializer) {
                call.apply();
            }
            initializer = null;
        }
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
        return builder(programName, programId, SamplerOverrideMap.empty());
    }

    public static Builder builder(String programName, int programId, SamplerOverrideMap overrides) {
        return new Builder(programName, programId, overrides);
    }

    private static final class SamplerBinding {
        private final String programName;
        private final String uniformName;
        private final int location;
        private final int unit;
        private final int target;
        private TextureBinding binding;

        private SamplerBinding(String programName, String uniformName, int location, int unit, int target, TextureBinding binding) {
            this.programName = programName;
            this.uniformName = uniformName;
            this.location = location;
            this.unit = unit;
            this.target = target;
            setBinding(binding);
        }

        private void bind() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            binding.bind();
            GL20.glUniform1i(location, unit);
        }

        private void unbind() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            binding.unbind();
        }

        private void setBinding(TextureBinding binding) {
            this.binding = binding == null ? TextureBinding.unbound() : binding;
        }
    }

    private static final class GlUniform1iCall {
        private final int location;
        private final int unit;

        private GlUniform1iCall(int location, int unit) {
            this.location = location;
            this.unit = unit;
        }

        private void apply() {
            GL20.glUniform1i(location, unit);
        }
    }

    public static final class Builder {
        private final String programName;
        private final int programId;
        private final List<SamplerBinding> bindings;
    private final Set<String> registeredSamplers;
    private final List<GlUniform1iCall> initializerCalls;
        private int highestUnit;
        private final SamplerOverrideMap overrides;
    private final Map<String, TextureBinding> customBindings;
    private static final HashMap<String, Integer> SAMPLER_UNIT_MAP = new HashMap<>();

    static {
        // Primary color / texture
        registerAliases(0,
            "gtexture0", "s_texture", "gcolor", "colortex0", "tex", "texture", "gaux0");
        // Normal map
        registerAliases(1,
            "gtexture1", "s_normal", "gnormal", "colortex1", "normals");
        // Specular / material
        registerAliases(2,
            "gtexture2", "s_specular", "colortex2", "specular", "gaux1");
        // Additional color attachments
        registerAliases(3, "colortex3", "gaux2");
        registerAliases(4, "colortex4", "gaux3");
        registerAliases(13, "colortex5", "gaux4");
        registerAliases(14, "colortex6", "gaux5");
        registerAliases(15, "colortex7", "gaux6");

        // Shadow maps and related targets
        registerAliases(5, "shadow", "s_shadow", "shadowtex0", "shadowtex0hw", "watershadow");
        registerAliases(6, "shadowcolor", "s_shadowcolor", "shadowtex1", "shadowtex1hw", "shadowcolor0");
        registerAliases(7, "shadowcolor1", "shadowcolorimg0");
        registerAliases(8, "shadowcolorimg1");

        // Depth buffers
        registerAliases(9, "depthtex0", "gdepthtex");
        registerAliases(10, "depthtex1");
        registerAliases(11, "depthtex2");

        // Noise
        registerAliases(12, "noisetex");
    }

    private static void registerAliases(int unit, String... names) {
        for (String name : names) {
        SAMPLER_UNIT_MAP.put(name, unit);
        }
    }

        private Builder(String programName, int programId, SamplerOverrideMap overrides) {
            this.programName = Objects.requireNonNull(programName, "programName");
            this.programId = programId;
            this.bindings = new ArrayList<>();
            this.registeredSamplers = new HashSet<>();
            this.initializerCalls = new ArrayList<>();
            this.highestUnit = -1;
            this.overrides = overrides == null ? SamplerOverrideMap.empty() : overrides;
            this.customBindings = new HashMap<>();
        }

        public void addExternalSampler(int textureUnit, String... names) {
            if (names == null || names.length == 0 || textureUnit < 0) {
                return;
            }
            for (String name : names) {
                int location = OculusRenderSystem.glGetUniformLocation(programId, name);
                if (location >= 0) {
                    initializerCalls.add(new GlUniform1iCall(location, textureUnit));
                }
            }
        }

        public boolean hasSampler(String name) {
            if (name == null) {
                return false;
            }
            return OculusRenderSystem.glGetUniformLocation(programId, name) >= 0;
        }

        public boolean addDefaultSampler(IntSupplier sampler, String... names) {
            return registerExternalBinding(sampler, names);
        }

        public boolean addDynamicSampler(IntSupplier sampler, String... names) {
            return registerExternalBinding(sampler, names);
        }

        public boolean addDynamicSampler(IntSupplier sampler, Runnable notifier, String... names) {
            return addDynamicSampler(sampler, names);
        }

        private boolean registerExternalBinding(IntSupplier sampler, String... names) {
            if (sampler == null || names == null || names.length == 0) {
                return false;
            }

            TextureBinding binding = TextureBinding.texture2D(() -> sampler.getAsInt());
            boolean registered = false;

            for (String name : names) {
                if (hasSampler(name)) {
                    TextureBindingRegistry.register(name.toLowerCase(Locale.ROOT), binding);
                    registered = true;
                }
            }

            return registered;
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

            String normalized = uniformName.toLowerCase(Locale.ROOT);
            TextureBinding binding = customBindings.getOrDefault(normalized, TextureBindingRegistry.resolve(uniformName));
            bindings.add(new SamplerBinding(programName, uniformName, location, unit, GL11.GL_TEXTURE_2D, binding));
            if (unit > highestUnit) {
                highestUnit = unit;
            }
            return this;
        }

        public void overrideBinding(String samplerName, TextureBinding binding) {
            if (samplerName == null || binding == null) {
                return;
            }

            String normalized = samplerName.toLowerCase(Locale.ROOT);
            boolean updated = false;

            for (SamplerBinding samplerBinding : bindings) {
                if (samplerBinding.uniformName.equalsIgnoreCase(samplerName)) {
                    samplerBinding.setBinding(binding);
                    updated = true;
                }
            }

            if (!updated) {
                customBindings.put(normalized, binding);
            }
        }

        public ProgramSamplers build() {
            int activeSamplers = highestUnit + 1;
            List<GlUniform1iCall> init = initializerCalls.isEmpty() ? null : new ArrayList<>(initializerCalls);
            return new ProgramSamplers(programName, Collections.unmodifiableList(new ArrayList<>(bindings)), activeSamplers, init);
        }

        private int resolveUnit(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);

            int overrideUnit = overrides.resolve(normalized);
            if (overrideUnit >= 0) {
                return overrideUnit;
            }

            if (SAMPLER_UNIT_MAP.containsKey(normalized)) {
                return SAMPLER_UNIT_MAP.get(normalized);
            }

            if (normalized.startsWith("colortex")) {
                Integer parsed = parseIndex(normalized, "colortex");
                if (parsed != null) {
                    return SAMPLER_UNIT_MAP.getOrDefault("colortex" + parsed, parsed);
                }
            }

            if (normalized.startsWith("depthtex")) {
                Integer parsed = parseIndex(normalized, "depthtex");
                if (parsed != null) {
                    return SAMPLER_UNIT_MAP.getOrDefault("depthtex" + parsed, 9 + parsed);
                }
            }

            return -1;
        }

        private Integer parseIndex(String name, String prefix) {
            try {
                return Integer.parseInt(name.substring(prefix.length()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
