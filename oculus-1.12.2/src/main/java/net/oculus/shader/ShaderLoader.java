package net.oculus.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.SamplerOverrideMap;
import net.oculus.gl.program.SamplerOverrideProvider;
import net.oculus.gl.shader.ShaderType;
import net.oculus.pipeline.context.ObjectContext;
import net.oculus.pipeline.texture.CustomTextureManager;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.shaderpack.StringPair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coordinates loading shader programs from a pack. The modern Iris implementation
 * compiles and links GLSL here; the 1.12.2 backport now mirrors that workflow by
 * compiling every available program source through the LWJGL-backed pipeline.
 */
public final class ShaderLoader {
    private static final Logger LOGGER = LogManager.getLogger(ShaderLoader.class);

    private final ProgramSet programSet;
    private final Map<String, Program> programs;
    private final SamplerOverrideProvider samplerOverrideProvider;
    private final CustomTextureManager customTextureManager;
    private final List<StringPair> environmentDefines;
    private boolean initialized;

    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("^\\s*#version\\s+(\\d+)", Pattern.MULTILINE);

    public ShaderLoader(ProgramSet programSet) {
        this(programSet, SamplerOverrideProvider.NONE, null);
    }

    public ShaderLoader(ProgramSet programSet, SamplerOverrideProvider samplerOverrideProvider) {
        this(programSet, samplerOverrideProvider, null);
    }

    public ShaderLoader(ProgramSet programSet,
                        SamplerOverrideProvider samplerOverrideProvider,
                        CustomTextureManager customTextureManager) {
        this.programSet = programSet;
        this.programs = new HashMap<>();
        this.samplerOverrideProvider = samplerOverrideProvider == null ? SamplerOverrideProvider.NONE : samplerOverrideProvider;
    this.customTextureManager = customTextureManager;
    this.environmentDefines = ShaderPreprocessor.createEnvironmentDefines(programSet);
    }

    public void initialize(ObjectContext context) {
        if (initialized) {
            return;
        }

        context.prepare();

        List<ProgramSource> sources = collectProgramSources();
        Set<String> compiledNames = new HashSet<>();
        List<String> failedPrograms = new ArrayList<>();

        int attempted = 0;
        int successful = 0;

        for (ProgramSource source : sources) {
            if (source == null || !source.isValid()) {
                continue;
            }

            if (!compiledNames.add(source.getName())) {
                continue;
            }

            attempted++;

            try {
                Program program = compileProgram(source);
                programs.put(source.getName(), program);
                successful++;
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

        LOGGER.info("Compiled {} shader program(s) for pack {}", successful, programSet.getPack().getName());
        initialized = true;
    }

    private Program compileProgram(ProgramSource source) {
        String vertexSource = applyStandardDefines(source.getVertexSource().orElse(null), ShaderType.VERTEX, source.getName());
        String geometrySource = applyStandardDefines(source.getGeometrySource().orElse(null), ShaderType.GEOMETRY, source.getName());
        String fragmentSource = applyStandardDefines(source.getFragmentSource().orElse(null), ShaderType.FRAGMENT, source.getName());

        SamplerOverrideMap overrides = samplerOverrideProvider.overridesFor(source.getName());
        ProgramBuilder builder = ProgramBuilder.begin(source.getName(), vertexSource, geometrySource, fragmentSource, overrides);
        if (customTextureManager != null) {
            customTextureManager.applyCustomSamplers(source.getName(), builder.samplers());
        }
        return builder.build();
    }

    private void logProgramFailure(String name, Exception ex) {
        LOGGER.error("Failed to compile program {}: {}", name, ex.getMessage());
        LOGGER.debug("Stacktrace for program {}", name, ex);
    }

    private String applyStandardDefines(String source, ShaderType shaderType, String programName) {
        if (source == null) {
            return null;
        }

        String withDefines = ShaderPreprocessor.applyDefines(source, shaderType, programName, environmentDefines);
        String patched = ShaderCompatibilityPatcher.patch(
            programSet.getPack().getName(),
            programName,
            shaderType,
            withDefines
        );

        return ensureShaderCompatibility(patched, shaderType);
    }

    private String ensureShaderCompatibility(String source, ShaderType shaderType) {
        if (source == null || shaderType == null) {
            return source;
        }

        switch (shaderType) {
            case VERTEX:
            case GEOMETRY:
                return ensureGpuShader4Support(source);
            default:
                return source;
        }
    }

    private String ensureGpuShader4Support(String source) {
        String upgraded = bumpVersionDirective(source, 130);
        return ensureExtensionEnabled(upgraded, "GL_EXT_gpu_shader4");
    }

    private String bumpVersionDirective(String source, int minimumVersion) {
        Matcher matcher = VERSION_DIRECTIVE.matcher(source);
        if (matcher.find()) {
            int current;
            try {
                current = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                current = minimumVersion;
            }

            if (current >= minimumVersion) {
                return source;
            }

            return matcher.replaceFirst("#version " + minimumVersion);
        }

        return "#version " + minimumVersion + '\n' + source;
    }

    private String ensureExtensionEnabled(String source, String extension) {
        if (source.contains("#extension " + extension)) {
            return source;
        }

        int versionIndex = source.indexOf("#version");
        if (versionIndex < 0) {
            return "#extension " + extension + " : enable\n" + source;
        }

        int lineEnd = source.indexOf('\n', versionIndex);
        if (lineEnd < 0) {
            return source + '\n' + "#extension " + extension + " : enable\n";
        }

        StringBuilder builder = new StringBuilder(source.length() + extension.length() + 32);
        builder.append(source, 0, lineEnd + 1)
            .append("#extension ").append(extension).append(" : enable\n")
            .append(source.substring(lineEnd + 1));
        return builder.toString();
    }

    private List<ProgramSource> collectProgramSources() {
        List<ProgramSource> sources = new ArrayList<>();

        addOptional(programSet.getShadow(), sources);
        addArray(programSet.getShadowComposite(), sources);
        addArray(programSet.getPrepare(), sources);

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

        addArray(programSet.getDeferred(), sources);

        addOptional(programSet.getGbuffersWater(), sources);
        addOptional(programSet.getGbuffersHandWater(), sources);

        addArray(programSet.getComposite(), sources);
        addOptional(programSet.getCompositeFinal(), sources);

        return sources;
    }

    private void addOptional(Optional<ProgramSource> optional, List<ProgramSource> sink) {
        optional.ifPresent(sink::add);
    }

    private void addArray(ProgramSource[] array, List<ProgramSource> sink) {
        if (array == null) {
            return;
        }
        Collections.addAll(sink, array);
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

    public Map<String, Program> getPrograms() {
        return Collections.unmodifiableMap(programs);
    }

    public void destroy() {
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
}
