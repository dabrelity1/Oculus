package net.oculus.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.SamplerOverrideMap;
import net.oculus.gl.program.SamplerOverrideProvider;
import net.oculus.pipeline.InputAvailability;
import net.oculus.pipeline.context.ObjectContext;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.pipeline.texture.CustomImageManager;
import net.oculus.pipeline.texture.CustomTextureManager;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.samplers.IrisImages;
import net.oculus.samplers.IrisSamplers;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.shaderpack.StringPair;
import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.custom.CustomUniformExpressionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coordinates loading shader programs from a pack. The modern Iris implementation
 * compiles and links GLSL here; the 1.12.2 backport now mirrors that workflow by
 * compiling the runtime program sources selected by the LWJGL-backed pipeline.
 */
public final class ShaderLoader {
    private static final Logger LOGGER = LogManager.getLogger(ShaderLoader.class);
    private static final long SLOW_COMPILE_LOG_NANOS = TimeUnit.MILLISECONDS.toNanos(750);

    private final ProgramSet programSet;
    private final Map<String, Program> programs;
    private final Map<ProgramVariantKey, Program> availabilityPrograms;
    private final SamplerOverrideProvider samplerOverrideProvider;
    private final CustomTextureManager customTextureManager;
    private final CustomImageManager customImageManager;
    private final Supplier<RenderTargets> renderTargetsSupplier;
    private final Supplier<? extends Set<Integer>> flippedBuffersSupplier;
    private final Supplier<ShadowMap> shadowMapSupplier;
    private final CustomUniformExpressionManager customUniforms;
    private final FrameUpdateNotifier frameUpdateNotifier;
    private final List<StringPair> environmentDefines;
    private boolean initialized;

    public ShaderLoader(ProgramSet programSet) {
        this(programSet, SamplerOverrideProvider.NONE, null, null);
    }

    public ShaderLoader(ProgramSet programSet, SamplerOverrideProvider samplerOverrideProvider) {
        this(programSet, samplerOverrideProvider, null, null);
    }

    public ShaderLoader(ProgramSet programSet,
                        SamplerOverrideProvider samplerOverrideProvider,
                        CustomTextureManager customTextureManager) {
        this(programSet, samplerOverrideProvider, customTextureManager, null);
    }

    public ShaderLoader(ProgramSet programSet,
                        SamplerOverrideProvider samplerOverrideProvider,
                        CustomTextureManager customTextureManager,
                        CustomImageManager customImageManager) {
        this(programSet, samplerOverrideProvider, customTextureManager, customImageManager, null, null, null);
    }

    public ShaderLoader(ProgramSet programSet,
                        SamplerOverrideProvider samplerOverrideProvider,
                        CustomTextureManager customTextureManager,
                        CustomImageManager customImageManager,
                        Supplier<RenderTargets> renderTargetsSupplier,
                        Supplier<? extends Set<Integer>> flippedBuffersSupplier,
                        Supplier<ShadowMap> shadowMapSupplier) {
        this(programSet, samplerOverrideProvider, customTextureManager, customImageManager,
            renderTargetsSupplier, flippedBuffersSupplier, shadowMapSupplier, CustomUniformExpressionManager.empty());
    }

    public ShaderLoader(ProgramSet programSet,
                        SamplerOverrideProvider samplerOverrideProvider,
                        CustomTextureManager customTextureManager,
                        CustomImageManager customImageManager,
                        Supplier<RenderTargets> renderTargetsSupplier,
                        Supplier<? extends Set<Integer>> flippedBuffersSupplier,
                        Supplier<ShadowMap> shadowMapSupplier,
                        CustomUniformExpressionManager customUniforms) {
        this(programSet, samplerOverrideProvider, customTextureManager, customImageManager,
            renderTargetsSupplier, flippedBuffersSupplier, shadowMapSupplier, customUniforms, null);
    }

    public ShaderLoader(ProgramSet programSet,
                        SamplerOverrideProvider samplerOverrideProvider,
                        CustomTextureManager customTextureManager,
                        CustomImageManager customImageManager,
                        Supplier<RenderTargets> renderTargetsSupplier,
                        Supplier<? extends Set<Integer>> flippedBuffersSupplier,
                        Supplier<ShadowMap> shadowMapSupplier,
                        CustomUniformExpressionManager customUniforms,
                        FrameUpdateNotifier frameUpdateNotifier) {
        this.programSet = programSet;
        this.programs = new HashMap<>();
        this.availabilityPrograms = new HashMap<>();
        this.samplerOverrideProvider = samplerOverrideProvider == null ? SamplerOverrideProvider.NONE : samplerOverrideProvider;
        this.customTextureManager = customTextureManager;
        this.customImageManager = customImageManager;
        this.renderTargetsSupplier = renderTargetsSupplier;
        this.flippedBuffersSupplier = flippedBuffersSupplier;
        this.shadowMapSupplier = shadowMapSupplier;
        this.customUniforms = customUniforms == null ? CustomUniformExpressionManager.empty() : customUniforms;
        this.frameUpdateNotifier = frameUpdateNotifier;
        this.environmentDefines = ShaderPreprocessor.createEnvironmentDefines(programSet);
    }

    public void initialize(ObjectContext context) {
        if (initialized) {
            return;
        }

        List<ProgramSource> sources = collectProgramSources();
        Set<String> compiledNames = new HashSet<>();
        List<String> failedPrograms = new ArrayList<>();
        int totalSources = countUniqueValidSources(sources);
        long allStart = System.nanoTime();

        int attempted = 0;
        int successful = 0;
        int processed = 0;

        LOGGER.info("Compiling {} shader source program(s) for pack {}", totalSources, programSet.getPack().getName());

        for (ProgramSource source : sources) {
            if (source == null || !source.isValid()) {
                continue;
            }

            if (!compiledNames.add(source.getName())) {
                continue;
            }

            attempted++;
            processed++;
            long sourceStart = System.nanoTime();
            int successfulForSource = 0;

            try {
                if (usesAvailabilityVariants(source.getName())) {
                    successfulForSource = compileAvailabilityVariants(source, failedPrograms);
                } else {
                    Program program = compileProgram(source, null);
                    programs.put(source.getName(), program);
                    successfulForSource = 1;
                }
                successful += successfulForSource;
                logSourceProgress(processed, totalSources, source, successfulForSource,
                    System.nanoTime() - sourceStart);
            } catch (ProgramLoadException ex) {
                logProgramFailure(source.getName(), ex);
                failedPrograms.add(source.getName());
            } catch (RuntimeException ex) {
                logProgramFailure(source.getName(), ex);
                failedPrograms.add(source.getName());
            }
        }

        if (attempted > 0 && successful == 0) {
            throw new ProgramLoadException("Failed to compile any shader programs for pack " + programSet.getPack().getName());
        }

        if (!failedPrograms.isEmpty()) {
            LOGGER.warn("{} shader program(s) failed to compile: {}", failedPrograms.size(), failedPrograms);
        }

        LOGGER.info("Compiled {} shader program object(s) for pack {} in {} ms",
            successful, programSet.getPack().getName(), elapsedMillis(System.nanoTime() - allStart));
        initialized = true;
    }

    private int compileAvailabilityVariants(ProgramSource source, List<String> failedPrograms) {
        if (!usesAvailabilityVariants(source.getName())) {
            return 0;
        }

        int successful = 0;
        for (int packed = 0; packed < InputAvailability.NUM_VALUES; packed++) {
            InputAvailability availability = InputAvailability.unpack(packed);
            ProgramVariantKey key = new ProgramVariantKey(source.getName(), availability);
            try {
                availabilityPrograms.put(key, compileProgram(source, availability));
                successful++;
            } catch (ProgramLoadException ex) {
                logProgramFailure(source.getName() + " " + availability, ex);
                failedPrograms.add(source.getName() + " " + availability);
            } catch (RuntimeException ex) {
                logProgramFailure(source.getName() + " " + availability, ex);
                failedPrograms.add(source.getName() + " " + availability);
            }
        }
        return successful;
    }

    private Program compileProgram(ProgramSource source, InputAvailability availability) {
        long totalStart = System.nanoTime();
        long prepareStart = totalStart;
        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            programSet.getPack().getName(),
            source.getName(),
            source.getVertexSource().orElse(null),
            source.getGeometrySource().orElse(null),
            source.getFragmentSource().orElse(null),
            environmentDefines,
            availability);
        long prepareNanos = System.nanoTime() - prepareStart;
        String vertexSource = prepared.getVertexSource();
        String geometrySource = prepared.getGeometrySource();
        String fragmentSource = prepared.getFragmentSource();

        long buildStart = System.nanoTime();
        SamplerOverrideMap overrides = samplerOverrideProvider.overridesFor(source.getName());
        ProgramBuilder builder = ProgramBuilder.begin(
            source.getName(), vertexSource, geometrySource, fragmentSource, overrides, customUniforms,
            IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS, availability, frameUpdateNotifier, programSet.getPackDirectives());
        applyShadowSamplerBindings(builder, source.getName());
        if (customTextureManager != null) {
            customTextureManager.applyCustomSamplers(source.getName(), builder.samplers());
        }
        applyImageBindings(builder, source.getName());
        if (customImageManager != null) {
            customImageManager.applyToProgram(builder);
        }
        Program program = builder.build();
        long buildNanos = System.nanoTime() - buildStart;
        long totalNanos = System.nanoTime() - totalStart;
        logProgramTiming(source, availability, totalSourceLength(vertexSource, geometrySource, fragmentSource),
            prepareNanos, buildNanos, totalNanos);
        return program;
    }

    private static boolean usesAvailabilityVariants(String programName) {
        if (programName == null) {
            return false;
        }

        String normalized = programName.toLowerCase(Locale.ROOT);
        return normalized.startsWith("gbuffers_")
            || normalized.equals("shadow")
            || normalized.startsWith("shadow_")
            || normalized.startsWith("shadow.");
    }

    private void applyShadowSamplerBindings(ProgramBuilder builder, String programName) {
        ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);
        if (shadowMap != null) {
            shadowMap.applySamplerBindings(builder, programName);
        }
    }

    private void applyImageBindings(ProgramBuilder builder, String programName) {
        RenderTargets renderTargets = renderTargetsSupplier == null ? null : renderTargetsSupplier.get();
        IrisImages.addRenderTargetImages(builder, flippedBuffersSupplier, renderTargets);

        ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);
        if (shadowMap != null) {
            IrisImages.addShadowColorImages(builder, shadowMap);
        }
    }

    private void logProgramFailure(String name, Exception ex) {
        LOGGER.error("Failed to compile program {}: {}", name, ex.getMessage());
        LOGGER.debug("Stacktrace for program {}", name, ex);
    }

    private List<ProgramSource> collectProgramSources() {
        List<ProgramSource> sources = new ArrayList<>();

        addOptional(programSet.getShadow(), sources);

        addOptional(programSet.getGbuffersBasic(), sources);
        addOptional(programSet.getGbuffersLine(), sources);
        addOptional(programSet.getGbuffersBeaconBeam(), sources);
        addOptional(programSet.getGbuffersTextured(), sources);
        addOptional(programSet.getGbuffersTexturedLit(), sources);
        addOptional(programSet.getGbuffersTerrain(), sources);
        addOptional(programSet.getGbuffersDamagedBlock(), sources);
        addOptional(programSet.getGbuffersSkyBasic(), sources);
        addOptional(programSet.getGbuffersSkyTextured(), sources);
        addOptional(programSet.getGbuffersClouds(), sources);
        addOptional(programSet.getGbuffersWeather(), sources);
        addOptional(programSet.getGbuffersEntities(), sources);
        addOptional(programSet.getGbuffersEntitiesTrans(), sources);
        addOptional(programSet.getGbuffersEntitiesGlowing(), sources);
        addOptional(programSet.getGbuffersGlint(), sources);
        addOptional(programSet.getGbuffersEntityEyes(), sources);
        addOptional(programSet.getGbuffersBlock(), sources);
        addOptional(programSet.getGbuffersHand(), sources);

        addOptional(programSet.getGbuffersWater(), sources);
        addOptional(programSet.getGbuffersHandWater(), sources);

        return sources;
    }

    private void addOptional(Optional<ProgramSource> optional, List<ProgramSource> sink) {
        optional.ifPresent(sink::add);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public ProgramSet getProgramSet() {
        return programSet;
    }

    public Program getProgram(String name) {
        return programs.get(name);
    }

    public Program getProgram(String name, InputAvailability availability) {
        if (availability == null) {
            return getProgram(name);
        }

        Program program = availabilityPrograms.get(new ProgramVariantKey(name, availability));
        if (usesAvailabilityVariants(name)) {
            return program;
        }
        return program == null ? getProgram(name) : program;
    }

    public Map<String, Program> getPrograms() {
        return Collections.unmodifiableMap(programs);
    }

    public int getProgramCount() {
        return programs.size() + availabilityPrograms.size();
    }

    public boolean hasCompiledPrograms() {
        return getProgramCount() > 0;
    }

    public void destroy() {
        for (Map.Entry<ProgramVariantKey, Program> entry : availabilityPrograms.entrySet()) {
            try {
                entry.getValue().destroy();
            } catch (RuntimeException ex) {
                LOGGER.warn("Exception while destroying program {}", entry.getKey(), ex);
            }
        }
        availabilityPrograms.clear();

        for (Map.Entry<String, Program> entry : programs.entrySet()) {
            try {
                entry.getValue().destroy();
            } catch (RuntimeException ex) {
                LOGGER.warn("Exception while destroying program {}", entry.getKey(), ex);
            }
        }
        programs.clear();
        initialized = false;
    }

    private static final class ProgramVariantKey {
        private final String name;
        private final int availability;

        private ProgramVariantKey(String name, InputAvailability availability) {
            this.name = name;
            this.availability = availability == null ? -1 : availability.pack();
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + (name == null ? 0 : name.hashCode());
            result = 31 * result + availability;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ProgramVariantKey)) {
                return false;
            }
            ProgramVariantKey other = (ProgramVariantKey) obj;
            return availability == other.availability
                && (name == null ? other.name == null : name.equals(other.name));
        }

        @Override
        public String toString() {
            return name + "#" + availability;
        }
    }

    private static int countUniqueValidSources(List<ProgramSource> sources) {
        Set<String> names = new HashSet<>();
        for (ProgramSource source : sources) {
            if (source != null && source.isValid()) {
                names.add(source.getName());
            }
        }
        return names.size();
    }

    private void logSourceProgress(int processed, int totalSources, ProgramSource source,
                                   int compiledObjects, long elapsedNanos) {
        LOGGER.info("Shader compile progress for pack {}: {}/{} source programs, {} program object(s) from {} in {} ms",
            programSet.getPack().getName(), processed, totalSources, compiledObjects, source.getName(),
            elapsedMillis(elapsedNanos));
    }

    private void logProgramTiming(ProgramSource source, InputAvailability availability, int sourceChars,
                                  long prepareNanos, long buildNanos, long totalNanos) {
        String variant = availability == null ? "base" : availability.toString();
        if (totalNanos >= SLOW_COMPILE_LOG_NANOS) {
            LOGGER.info("Slow shader compile for pack {} program {} [{}]: {} ms total ({} ms prepare, {} ms compile/link/bind, {} chars)",
                programSet.getPack().getName(), source.getName(), variant, elapsedMillis(totalNanos),
                elapsedMillis(prepareNanos), elapsedMillis(buildNanos), sourceChars);
        } else {
            LOGGER.debug("Shader compile timing for pack {} program {} [{}]: {} ms total ({} ms prepare, {} ms compile/link/bind, {} chars)",
                programSet.getPack().getName(), source.getName(), variant, elapsedMillis(totalNanos),
                elapsedMillis(prepareNanos), elapsedMillis(buildNanos), sourceChars);
        }
    }

    private static int totalSourceLength(String... sources) {
        int total = 0;
        for (String source : sources) {
            if (source != null) {
                total += source.length();
            }
        }
        return total;
    }

    private static long elapsedMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }
}
