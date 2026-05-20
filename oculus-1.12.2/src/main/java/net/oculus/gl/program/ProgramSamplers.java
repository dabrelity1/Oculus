package net.oculus.gl.program;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.ToIntBiFunction;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.sampler.SamplerLimits;
import net.oculus.gl.state.StateUpdateNotifiers;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.shaderpack.PackRenderTargetDirectives;
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
        Throwable previousCleanupFailure = cleanupActiveBeforeUpdate();

        active = this;
        try {
            if (initializer != null) {
                for (GlUniform1iCall call : initializer) {
                    call.apply();
                }
                initializer = null;
            }

            int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            try {
                for (SamplerBinding binding : bindings) {
                    binding.bind();
                    binding.attachListener();
                }
            } finally {
                OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
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

    public static void clearActiveSamplers() {
        ProgramSamplers current = active;
        if (current != null) {
            Throwable failure = null;
            int previousActiveTexture = GL13.GL_TEXTURE0;
            boolean previousActiveTextureCaptured = false;
            try {
                previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                previousActiveTextureCaptured = true;
            } catch (RuntimeException | Error exception) {
                failure = exception;
            }

            try {
                failure = runCleanup(failure, current::removeListeners);
                failure = runCleanup(failure, current::unbind);
            } finally {
                active = null;
                if (previousActiveTextureCaptured) {
                    final int textureUnitToRestore = previousActiveTexture;
                    failure = runCleanup(failure, () -> OculusRenderSystem.setActiveTextureUnit(textureUnitToRestore));
                }
            }
            rethrowCleanupFailure(failure);
        }
    }

    static void clearActiveSamplers(ProgramSamplers samplers) {
        if (active == samplers) {
            clearActiveSamplers();
        }
    }

    public int getActiveSamplers() {
        return activeSamplerCount;
    }

    private void unbind() {
        Throwable failure = null;
        for (SamplerBinding binding : bindings) {
            failure = runCleanup(failure, binding::unbind);
        }
        rethrowCleanupFailure(failure);
    }

    private void removeListeners() {
        Throwable failure = null;
        for (SamplerBinding binding : bindings) {
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
        ProgramSamplers current = active;
        if (current == null) {
            return null;
        }
        return runCleanup(null, ProgramSamplers::clearActiveSamplers);
    }

    private static void cleanupAfterFailedUpdate(Throwable failure) {
        try {
            clearActiveSamplers();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
        }
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    public static Builder builder(String programName, int programId) {
        return builder(programName, programId, SamplerOverrideMap.empty());
    }

    public static Builder builder(String programName, int programId, SamplerOverrideMap overrides) {
        return builder(programName, programId, overrides, Collections.emptySet());
    }

    public static Builder builder(String programName, int programId, SamplerOverrideMap overrides, Set<Integer> reservedTextureUnits) {
        return new Builder(programName, programId, overrides, reservedTextureUnits,
            () -> SamplerLimits.get().getMaxTextureUnits(), OculusRenderSystem::glGetUniformLocation);
    }

    static Builder builder(String programName, int programId, SamplerOverrideMap overrides,
                           Set<Integer> reservedTextureUnits, IntSupplier maxTextureUnitsSupplier,
                           ToIntBiFunction<Integer, String> uniformLocationResolver) {
        return new Builder(programName, programId, overrides, reservedTextureUnits,
            maxTextureUnitsSupplier, uniformLocationResolver);
    }

    static int nextAvailableTextureUnit(int firstCandidate, int maxTextureUnits, Set<Integer> usedUnits) {
        return nextAvailableTextureUnit(firstCandidate, maxTextureUnits, usedUnits, Collections.emptySet());
    }

    static int nextAvailableTextureUnit(int firstCandidate, int maxTextureUnits, Set<Integer> usedUnits, Set<Integer> reservedUnits) {
        int unit = Math.max(0, firstCandidate);
        while (unit < maxTextureUnits && (contains(usedUnits, unit) || contains(reservedUnits, unit))) {
            unit++;
        }
        return unit < maxTextureUnits ? unit : -1;
    }

    private static boolean contains(Set<Integer> units, int unit) {
        return units != null && units.contains(unit);
    }

    private static final class SamplerBinding {
        private final String programName;
        private final String uniformName;
        private final int location;
        private final int unit;
        private ValueUpdateNotifier notifier;
        private Runnable listener;
        private TextureBinding binding;

        private SamplerBinding(String programName, String uniformName, int location, int unit, TextureBinding binding,
                               ValueUpdateNotifier notifier) {
            this.programName = programName;
            this.uniformName = uniformName;
            this.location = location;
            this.unit = unit;
            setBinding(binding);
            setNotifier(notifier);
        }

        private void bind() {
            binding.bindToUnit(unit);
            if (location >= 0) {
                GL20.glUniform1i(location, unit);
            }
        }

        private void unbind() {
            binding.unbindFromUnit(unit);
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

        private void bindTexturePreservingActiveUnit() {
            int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            try {
                binding.bindToUnit(unit);
            } finally {
                OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
            }
        }

        private TextureBinding setBinding(TextureBinding binding) {
            TextureBinding previousBinding = this.binding;
            this.binding = binding == null ? TextureBinding.unbound() : binding;
            return previousBinding;
        }

        private ValueUpdateNotifier setNotifier(ValueUpdateNotifier notifier) {
            ValueUpdateNotifier previousNotifier = this.notifier;
            this.notifier = notifier;
            this.listener = notifier == null ? null : this::bindTexturePreservingActiveUnit;
            return previousNotifier;
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
        private final Map<Integer, TextureBinding> unitBindings;
        private final Map<String, Integer> externalSamplerUnits;
        private final int maxTextureUnits;
        private final Set<Integer> reservedTextureUnits;
        private final ToIntBiFunction<Integer, String> uniformLocationResolver;
        private static final HashMap<String, Integer> SAMPLER_UNIT_MAP = new HashMap<>();
        private static final int FIRST_DYNAMIC_TEXTURE_UNIT = 16;
        private static final TextureBinding EXTERNAL_TEXTURE_UNIT = TextureBinding.texture2D(() -> 0);

        static {
            // Primary color / texture
            registerAliases(0,
                "gtexture0", "s_texture", "colortex0", "tex", "texture", "gaux0");
            // Normal map
            registerAliases(1,
                "gtexture1", "s_normal", "colortex1", "normals");
            // Specular / material
            registerAliases(2, "gtexture2", "s_specular", "colortex2", "specular");
            // Additional color attachments
            registerAliases(3, "colortex3");
            registerAliases(4, "colortex4");
            registerAliases(13, "colortex5");
            registerAliases(14, "colortex6", "gaux5");
            registerAliases(15, "colortex7", "gaux6");
            registerLegacyRenderTargetAliases();

            // Shadow maps and related targets
            registerAliases(5, "shadow", "s_shadow", "shadowtex0", "shadowtex0HW", "shadowtex0hw", "watershadow");
            registerAliases(6, "shadowcolor", "s_shadowcolor", "shadowtex1", "shadowtex1HW", "shadowtex1hw", "shadowcolor0");
            registerAliases(7, "shadowcolor1");

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

        private static void registerLegacyRenderTargetAliases() {
            for (int index = 0; index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size(); index++) {
                String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);
                Integer unit = SAMPLER_UNIT_MAP.get("colortex" + index);
                SAMPLER_UNIT_MAP.put(legacyName, unit == null ? index : unit);
            }
        }

        private Builder(String programName, int programId, SamplerOverrideMap overrides,
                        Set<Integer> reservedTextureUnits, IntSupplier maxTextureUnitsSupplier,
                        ToIntBiFunction<Integer, String> uniformLocationResolver) {
            this.programName = Objects.requireNonNull(programName, "programName");
            this.programId = programId;
            this.bindings = new ArrayList<>();
            this.registeredSamplers = new HashSet<>();
            this.initializerCalls = new ArrayList<>();
            this.highestUnit = -1;
            this.overrides = overrides == null ? SamplerOverrideMap.empty() : overrides;
            this.customBindings = new HashMap<>();
            this.unitBindings = new HashMap<>();
            this.externalSamplerUnits = new HashMap<>();
            this.maxTextureUnits = Math.max(0,
                Objects.requireNonNull(maxTextureUnitsSupplier, "maxTextureUnitsSupplier").getAsInt());
            this.reservedTextureUnits = copyAndValidateReservedTextureUnits(reservedTextureUnits, this.maxTextureUnits);
            this.uniformLocationResolver = Objects.requireNonNull(uniformLocationResolver, "uniformLocationResolver");
        }

        public void addExternalSampler(int textureUnit, String... names) {
            if (names == null || names.length == 0) {
                return;
            }
            if (!reservedTextureUnits.contains(textureUnit)) {
                throw new IllegalArgumentException("Cannot add an externally-managed sampler for texture unit "
                    + textureUnit + " since it isn't in the set of reserved texture units.");
            }
            if (textureUnit >= maxTextureUnits) {
                throw new IllegalArgumentException("Cannot bind external sampler to texture unit " + textureUnit
                    + "; only " + maxTextureUnits + " texture unit(s) are available.");
            }
            boolean active = false;
            for (String name : names) {
                int location = findLocation(name);
                if (location >= 0) {
                    initializerCalls.add(new GlUniform1iCall(location, textureUnit));
                    externalSamplerUnits.put(name, textureUnit);
                    active = true;
                }
            }
            if (active) {
                reserveExternalTextureUnit(textureUnit);
            }
        }

        public boolean hasSampler(String name) {
            if (name == null) {
                return false;
            }
            return findLocation(name) >= 0;
        }

        public boolean hasRegisteredSamplerBinding(String name) {
            if (name == null) {
                return false;
            }
            for (SamplerBinding binding : bindings) {
                if (samplerNameMatchesOverride(binding.uniformName, name)) {
                    return true;
                }
            }
            return false;
        }

        public boolean addDefaultSampler(IntSupplier sampler, String... names) {
            if (sampler == null || names == null || names.length == 0) {
                return false;
            }
            return addDefaultSampler(TextureBinding.texture2D(() -> sampler.getAsInt()), names);
        }

        public boolean addDefaultSampler(TextureBinding binding, String... names) {
            if (binding == null || names == null || names.length == 0) {
                return false;
            }
            if (maxTextureUnits <= 0) {
                throw new IllegalStateException("Cannot bind default sampler in program " + programName
                    + "; no texture units are available.");
            }
            boolean reuseUnitZero = unitBindings.containsKey(0);
            if (reservedTextureUnits.contains(0) || (reuseUnitZero && !canReuseUnitZeroForDefaultSampler(names))) {
                throw new IllegalStateException("Texture unit 0 is already used.");
            }

            boolean registered = false;
            if (reuseUnitZero) {
                registered = replaceExistingDefaultSamplerBindings(binding, names);
            }
            for (String name : names) {
                int location = findLocation(name);
                if (location < 0 || !registeredSamplers.add(name)) {
                    continue;
                }
                customBindings.put(name, binding);
                addBinding(name, location, 0, binding, textureChangeNotifierForSampler(name));
                registered = true;
            }

            unitBindings.put(0, binding);
            if (registered) {
                return true;
            }

            addBinding(null, -1, 0, binding, null);
            return true;
        }

        private boolean canReuseUnitZeroForDefaultSampler(String... names) {
            boolean foundReusableBinding = false;
            for (SamplerBinding binding : bindings) {
                if (binding.unit != 0) {
                    continue;
                }
                if (!containsName(names, binding.uniformName)) {
                    return false;
                }
                foundReusableBinding = true;
            }
            return foundReusableBinding;
        }

        private boolean replaceExistingDefaultSamplerBindings(TextureBinding binding, String... names) {
            boolean registered = false;
            for (SamplerBinding samplerBinding : bindings) {
                if (samplerBinding.unit == 0 && containsName(names, samplerBinding.uniformName)) {
                    samplerBinding.setBinding(binding);
                    customBindings.put(samplerBinding.uniformName, binding);
                    registered = true;
                }
            }
            return registered;
        }

        private static boolean containsName(String[] names, String candidate) {
            if (candidate == null || names == null) {
                return false;
            }
            for (String name : names) {
                if (Objects.equals(name, candidate)) {
                    return true;
                }
            }
            return false;
        }

        public boolean addDynamicSampler(IntSupplier sampler, String... names) {
            return registerExternalBinding(sampler, null, names);
        }

        public boolean addDynamicSampler(IntSupplier sampler, ValueUpdateNotifier notifier, String... names) {
            return registerExternalBinding(sampler, notifier, names);
        }

        private boolean registerExternalBinding(IntSupplier sampler, ValueUpdateNotifier notifier, String... names) {
            if (sampler == null || names == null || names.length == 0) {
                return false;
            }

            TextureBinding binding = TextureBinding.texture2D(() -> sampler.getAsInt());
            boolean registered = false;

            for (String name : names) {
                if (hasSampler(name)) {
                    overrideBinding(name, binding, notifier);
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

            int location = findLocation(uniformName);
            if (location < 0) {
                LOGGER.debug("Program {} does not define sampler uniform {}", programName, uniformName);
                return this;
            }

            boolean hasCustomBinding = customBindings.containsKey(uniformName);
            TextureBinding registryBinding = TextureBindingRegistry.resolve(uniformName);
            boolean hasRegistryBinding = registryBinding != TextureBinding.unbound();
            TextureBinding binding = hasCustomBinding ? customBindings.get(uniformName) : registryBinding;
            int unit = resolveUnit(uniformName);
            if (unit < 0) {
                if (!hasCustomBinding && !hasRegistryBinding) {
                    LOGGER.warn("Unknown sampler {} will not be bound.", uniformName);
                    return this;
                }
                unit = resolveSharedOrDynamicUnit(uniformName, binding);
            }

            unit = resolveAvailableUnit(uniformName, unit, binding);
            addBinding(uniformName, location, unit, binding, textureChangeNotifierForSampler(uniformName));
            return this;
        }

        public void overrideBinding(String samplerName, TextureBinding binding) {
            overrideBinding(samplerName, binding, null);
        }

        private void overrideBinding(String samplerName, TextureBinding binding, ValueUpdateNotifier notifier) {
            if (samplerName == null || binding == null) {
                return;
            }

            customBindings.put(samplerName, binding);

            for (SamplerBinding samplerBinding : bindings) {
                if (samplerNameMatchesOverride(samplerBinding.uniformName, samplerName)) {
                    TextureBinding previousBinding = samplerBinding.setBinding(binding);
                    if (notifier != null) {
                        samplerBinding.setNotifier(notifier);
                    }
                    replaceSharedSamplerBindings(samplerBinding, previousBinding, binding, notifier);
                    replaceUnitBinding(samplerBinding.unit, previousBinding, binding);
                    return;
                }
            }

            int location = findLocation(samplerName);
            if (location < 0) {
                return;
            }

            int unit = resolveUnit(samplerName);
            Integer externalUnit = externalSamplerUnits.get(samplerName);
            if (externalUnit != null && externalUnit == 0) {
                unit = resolveUnitZeroExternalOverride(binding);
            } else if (unit < 0) {
                unit = resolveSharedOrDynamicUnit(samplerName, binding);
            } else {
                unit = resolveAvailableUnit(samplerName, unit, binding);
            }

            registeredSamplers.add(samplerName);
            addBinding(samplerName, location, unit, binding,
                notifier == null ? textureChangeNotifierForSampler(samplerName) : notifier);
        }

        public ProgramSamplers build() {
            int activeSamplers = highestUnit + 1;
            List<GlUniform1iCall> init = initializerCalls.isEmpty() ? null : new ArrayList<>(initializerCalls);
            return new ProgramSamplers(programName, Collections.unmodifiableList(new ArrayList<>(bindings)), activeSamplers, init);
        }

        private void addBinding(String uniformName, int location, int unit, TextureBinding binding,
                                ValueUpdateNotifier notifier) {
            bindings.add(new SamplerBinding(programName, uniformName, location, unit, binding, notifier));
            if (unit > highestUnit) {
                highestUnit = unit;
            }
        }

        private void replaceUnitBinding(int unit, TextureBinding previousBinding, TextureBinding binding) {
            TextureBinding current = unitBindings.get(unit);
            if (current == null || current == previousBinding) {
                unitBindings.put(unit, binding);
            }
        }

        private void replaceSharedSamplerBindings(SamplerBinding updatedBinding, TextureBinding previousBinding,
                                                  TextureBinding binding, ValueUpdateNotifier notifier) {
            for (SamplerBinding samplerBinding : bindings) {
                if (samplerBinding != updatedBinding
                    && samplerBinding.unit == updatedBinding.unit
                    && samplerBinding.binding == previousBinding) {
                    samplerBinding.setBinding(binding);
                    if (notifier != null) {
                        samplerBinding.setNotifier(notifier);
                    }
                }
            }
        }

        private int resolveUnitZeroExternalOverride(TextureBinding binding) {
            Integer sharedUnit = findExistingUnit(binding);
            if (sharedUnit != null) {
                return sharedUnit;
            }

            TextureBinding existing = unitBindings.get(0);
            if (existing == null || existing == EXTERNAL_TEXTURE_UNIT) {
                unitBindings.put(0, binding);
                return 0;
            }

            return resolveAvailableUnit("", 0, binding);
        }

        private int resolveSharedOrDynamicUnit(String samplerName, TextureBinding binding) {
            Integer sharedUnit = findExistingUnit(binding);
            return sharedUnit != null ? sharedUnit : allocateDynamicUnit(samplerName, binding);
        }

        private int resolveAvailableUnit(String uniformName, int requestedUnit, TextureBinding binding) {
            if (requestedUnit >= maxTextureUnits) {
                throw new IllegalStateException("Sampler " + uniformName + " requested texture unit " + requestedUnit
                    + ", but only " + maxTextureUnits + " texture unit(s) are available.");
            }

            Integer sharedUnit = findExistingUnit(binding);
            if (sharedUnit != null) {
                return sharedUnit;
            }

            if (reservedTextureUnits.contains(requestedUnit)) {
                int dynamicUnit = allocateDynamicUnit(uniformName, binding);
                LOGGER.debug(
                    "Program {} moved sampler {} from reserved texture unit {} to {}",
                    programName,
                    uniformName,
                    requestedUnit,
                    dynamicUnit);
                return dynamicUnit;
            }

            TextureBinding existing = unitBindings.get(requestedUnit);
            if (existing == null || existing == binding) {
                unitBindings.put(requestedUnit, binding);
                return requestedUnit;
            }

            int dynamicUnit = allocateDynamicUnit(uniformName, binding);
            LOGGER.debug(
                "Program {} moved sampler {} from texture unit {} to {} because the requested unit already has a distinct binding",
                programName,
                uniformName,
                requestedUnit,
                dynamicUnit);
            return dynamicUnit;
        }

        private int allocateDynamicUnit(String samplerName, TextureBinding binding) {
            int unit = ProgramSamplers.nextAvailableTextureUnit(
                Math.max(highestUnit + 1, FIRST_DYNAMIC_TEXTURE_UNIT),
                maxTextureUnits,
                unitBindings.keySet(),
                reservedTextureUnits);
            if (unit < 0) {
                throw new IllegalStateException("No more available texture units while activating sampler "
                    + samplerName + " in program " + programName + ". Only " + maxTextureUnits
                    + " texture unit(s) are available.");
            }
            unitBindings.put(unit, binding);
            return unit;
        }

        private Integer findExistingUnit(TextureBinding binding) {
            if (binding == null || binding == TextureBinding.unbound()) {
                return null;
            }
            for (Map.Entry<Integer, TextureBinding> entry : unitBindings.entrySet()) {
                if (entry.getValue() == binding) {
                    return entry.getKey();
                }
            }
            return null;
        }

        private void reserveExternalTextureUnit(int textureUnit) {
            TextureBinding existing = unitBindings.get(textureUnit);
            if (existing != null && existing != EXTERNAL_TEXTURE_UNIT) {
                throw new IllegalStateException("External sampler in program " + programName
                    + " requested texture unit " + textureUnit
                    + ", but that unit is already assigned to a managed sampler binding.");
            }

            unitBindings.put(textureUnit, EXTERNAL_TEXTURE_UNIT);
            if (textureUnit > highestUnit) {
                highestUnit = textureUnit;
            }
        }

        private int findLocation(String name) {
            return uniformLocationResolver.applyAsInt(programId, name);
        }

        private int resolveUnit(String name) {
            int overrideUnit = overrides.resolve(name);
            if (overrideUnit >= 0) {
                return overrideUnit;
            }

            if (SAMPLER_UNIT_MAP.containsKey(name)) {
                return SAMPLER_UNIT_MAP.get(name);
            }

            if (name.startsWith("colortex")) {
                Integer parsed = parseIndex(name, "colortex");
                if (parsed != null) {
                    return SAMPLER_UNIT_MAP.getOrDefault("colortex" + parsed, parsed);
                }
            }

            if (name.startsWith("depthtex")) {
                Integer parsed = parseIndex(name, "depthtex");
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

        static boolean samplerNameMatchesOverride(String activeUniformName, String overrideName) {
            return Objects.equals(activeUniformName, overrideName);
        }

        static ValueUpdateNotifier textureChangeNotifierForSampler(String samplerName) {
            if ("normals".equals(samplerName)) {
                return StateUpdateNotifiers.normalTextureChangeNotifier;
            }
            if ("specular".equals(samplerName)) {
                return StateUpdateNotifiers.specularTextureChangeNotifier;
            }
            return null;
        }

        private static Set<Integer> copyAndValidateReservedTextureUnits(Set<Integer> reservedTextureUnits, int maxTextureUnits) {
            if (reservedTextureUnits == null || reservedTextureUnits.isEmpty()) {
                return Collections.emptySet();
            }

            Set<Integer> copy = new HashSet<>();
            for (Integer reservedUnit : reservedTextureUnits) {
                if (reservedUnit == null) {
                    continue;
                }
                if (reservedUnit < 0) {
                    throw new IllegalArgumentException("Cannot reserve negative texture unit " + reservedUnit);
                }
                if (reservedUnit >= maxTextureUnits) {
                    throw new IllegalStateException("Cannot reserve texture unit " + reservedUnit
                        + "; only " + maxTextureUnits + " texture unit(s) are available.");
                }
                copy.add(reservedUnit);
            }

            return Collections.unmodifiableSet(copy);
        }
    }
}
