package net.oculus.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.pipeline.texture.CustomImageManager;
import net.oculus.pipeline.texture.CustomTextureManager;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.samplers.IrisImages;
import net.oculus.samplers.IrisSamplers;
import net.oculus.shader.ShaderSourcePreparer;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.shaderpack.StringPair;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.custom.CustomUniformExpressionManager;
import net.oculus.gl.shader.ShaderType;

/**
 * Source-backed terrain program bridge for Relictium's Sodium-style chunk renderer.
 *
 * <p>The 1.16.5 pipeline compiles transformed shader-pack terrain sources into
 * Sodium chunk programs. On 1.12.2 Relictium still owns the chunk draw backend,
 * so this class only owns source selection, transformation, common source
 * preparation, and wrapping the already-linked Relictium override program in
 * Oculus' uniform/sampler/image binding surface.</p>
 */
public final class SodiumTerrainPipeline {
    public static final SodiumTerrainPipeline NULL_PIPELINE = new SodiumTerrainPipeline();
    private static final InputAvailability RELICTIUM_LEVEL_INPUTS = new InputAvailability(true, true, false);

    private final TerrainProgramSource terrain;
    private final TerrainProgramSource translucent;
    private final TerrainProgramSource shadow;
    private final boolean initialized;

    private final CustomTextureManager customTextureManager;
    private final CustomImageManager customImageManager;
    private final Supplier<RenderTargets> renderTargetsSupplier;
    private final Supplier<? extends Set<Integer>> flippedBuffersSupplier;
    private final Supplier<ShadowMap> shadowMapSupplier;
    private final CustomUniformExpressionManager customUniforms;
    private final PackDirectives packDirectives;
    private final FrameUpdateNotifier frameUpdateNotifier;
    private final Runnable prepareResources;

    private SodiumTerrainPipeline() {
        this.terrain = null;
        this.translucent = null;
        this.shadow = null;
        this.initialized = false;
        this.customTextureManager = null;
        this.customImageManager = null;
        this.renderTargetsSupplier = null;
        this.flippedBuffersSupplier = Collections::emptySet;
        this.shadowMapSupplier = null;
        this.customUniforms = CustomUniformExpressionManager.empty();
        this.packDirectives = null;
        this.frameUpdateNotifier = null;
        this.prepareResources = () -> {
        };
    }

    public SodiumTerrainPipeline(String packName,
                                 ProgramSet programSet,
                                 List<StringPair> environmentDefines,
                                 CustomTextureManager customTextureManager,
                                 CustomImageManager customImageManager,
                                 Supplier<RenderTargets> renderTargetsSupplier,
                                 Supplier<? extends Set<Integer>> flippedBuffersSupplier,
                                 Supplier<ShadowMap> shadowMapSupplier,
                                 CustomUniformExpressionManager customUniforms,
                                 Runnable prepareResources) {
        this(packName, programSet, environmentDefines, customTextureManager, customImageManager,
            renderTargetsSupplier, flippedBuffersSupplier, shadowMapSupplier, customUniforms, null, prepareResources);
    }

    public SodiumTerrainPipeline(String packName,
                                 ProgramSet programSet,
                                 List<StringPair> environmentDefines,
                                 CustomTextureManager customTextureManager,
                                 CustomImageManager customImageManager,
                                 Supplier<RenderTargets> renderTargetsSupplier,
                                 Supplier<? extends Set<Integer>> flippedBuffersSupplier,
                                 Supplier<ShadowMap> shadowMapSupplier,
                                 CustomUniformExpressionManager customUniforms,
                                 FrameUpdateNotifier frameUpdateNotifier,
                                 Runnable prepareResources) {
        Objects.requireNonNull(packName, "packName");

        Optional<ProgramSource> terrainSource = selectTerrainSource(programSet);
        Optional<ProgramSource> waterSource = programSet == null ? Optional.empty() : programSet.getGbuffersWater();

        this.terrain = terrainSource
            .map(source -> prepareTerrainSource(packName, source, environmentDefines))
            .orElse(null);
        this.translucent = waterSource
            .map(source -> prepareTerrainSource(packName, source, environmentDefines))
            .orElse(this.terrain);
        this.shadow = programSet == null ? null : programSet.getShadow()
            .map(source -> prepareTerrainSource(packName, source, environmentDefines))
            .orElse(null);
        this.initialized = this.terrain != null || this.translucent != null || this.shadow != null;

        this.customTextureManager = customTextureManager;
        this.customImageManager = customImageManager;
        this.renderTargetsSupplier = renderTargetsSupplier;
        this.flippedBuffersSupplier = flippedBuffersSupplier == null ? Collections::emptySet : flippedBuffersSupplier;
        this.shadowMapSupplier = shadowMapSupplier;
        this.customUniforms = customUniforms == null ? CustomUniformExpressionManager.empty() : customUniforms;
        this.packDirectives = programSet == null ? null : programSet.getPackDirectives();
        this.frameUpdateNotifier = frameUpdateNotifier;
        this.prepareResources = prepareResources == null ? () -> {
        } : prepareResources;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean hasTerrainPass() {
        return terrain != null && terrain.hasVertexAndFragment();
    }

    public boolean hasTranslucentPass() {
        return translucent != null && translucent.hasVertexAndFragment();
    }

    public boolean hasShadowPass() {
        return shadow != null && shadow.hasVertexAndFragment();
    }

    public Optional<String> getTerrainProgramName() {
        return terrain == null ? Optional.empty() : Optional.of(terrain.programName);
    }

    public Optional<String> getTranslucentProgramName() {
        return translucent == null ? Optional.empty() : Optional.of(translucent.programName);
    }

    public Optional<String> getShadowProgramName() {
        return shadow == null ? Optional.empty() : Optional.of(shadow.programName);
    }

    public Optional<String> getTerrainVertexShaderSource() {
        return optionalSource(terrain == null ? null : terrain.vertexSource);
    }

    public Optional<String> getTerrainGeometryShaderSource() {
        return optionalSource(terrain == null ? null : terrain.geometrySource);
    }

    public Optional<String> getTerrainFragmentShaderSource() {
        return optionalSource(terrain == null ? null : terrain.fragmentSource);
    }

    public Optional<String> getTranslucentVertexShaderSource() {
        return optionalSource(translucent == null ? null : translucent.vertexSource);
    }

    public Optional<String> getTranslucentGeometryShaderSource() {
        return optionalSource(translucent == null ? null : translucent.geometrySource);
    }

    public Optional<String> getTranslucentFragmentShaderSource() {
        return optionalSource(translucent == null ? null : translucent.fragmentSource);
    }

    public Optional<String> getShadowVertexShaderSource() {
        return optionalSource(shadow == null ? null : shadow.vertexSource);
    }

    public Optional<String> getShadowGeometryShaderSource() {
        return optionalSource(shadow == null ? null : shadow.geometrySource);
    }

    public Optional<String> getShadowFragmentShaderSource() {
        return optionalSource(shadow == null ? null : shadow.fragmentSource);
    }

    /**
     * Builds Oculus binding metadata around a Relictium-owned GL program.
     *
     * <p>The returned wrapper must not be destroyed independently. Relictium owns
     * the actual GL program handle and deletes it through {@code ChunkProgram.delete()}.</p>
     */
    public Program buildProgramBindings(String programName, int programId, boolean shadowProgram) {
        Objects.requireNonNull(programName, "programName");
        if (programId <= 0) {
            throw new IllegalArgumentException("Cannot wrap invalid terrain program " + programId);
        }

        prepareResources.run();
        if (customTextureManager != null) {
            customTextureManager.initialize();
            customTextureManager.applyGlobalOverrides();
        }

        ProgramBuilder builder = ProgramBuilder.wrapLinkedProgram(programName, programId, customUniforms,
            RELICTIUM_LEVEL_INPUTS,
            frameUpdateNotifier, packDirectives);
        ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);
        if (shadowMap != null) {
            shadowMap.applySamplerBindings(builder);
        }

        RenderTargets renderTargets = renderTargetsSupplier == null ? null : renderTargetsSupplier.get();
        IrisSamplers.addRenderTargetSamplerBindings(builder, flippedBuffersSupplier, renderTargets, false);
        if (!shadowProgram) {
            IrisSamplers.addWorldDepthSamplerBindings(builder, renderTargets);
        }
        IrisImages.addRenderTargetImages(builder, flippedBuffersSupplier, renderTargets);
        if (shadowMap != null) {
            IrisImages.addShadowColorImages(builder, shadowMap);
        }

        if (customTextureManager != null) {
            customTextureManager.applyCustomSamplers(programName, builder.samplers());
        }
        if (customImageManager != null) {
            customImageManager.applyToProgram(builder);
        }

        return builder.build();
    }

    private static Optional<String> optionalSource(String source) {
        return Optional.ofNullable(source);
    }

    private static Optional<ProgramSource> selectTerrainSource(ProgramSet programSet) {
        if (programSet == null) {
            return Optional.empty();
        }

        Optional<ProgramSource> terrain = programSet.getGbuffersTerrain();
        if (terrain.isPresent()) {
            return terrain;
        }

        Optional<ProgramSource> texturedLit = programSet.getGbuffersTexturedLit();
        if (texturedLit.isPresent()) {
            return texturedLit;
        }

        Optional<ProgramSource> textured = programSet.getGbuffersTextured();
        if (textured.isPresent()) {
            return textured;
        }

        return programSet.getGbuffersBasic();
    }

    private static TerrainProgramSource prepareTerrainSource(String packName,
                                                             ProgramSource source,
                                                             List<StringPair> environmentDefines) {
        String programName = source.getName() + "_sodium";
        String vertex = source.getVertexSource()
            .map(shader -> SodiumTerrainShaderTransformer.transform(ShaderType.VERTEX, shader))
            .orElse(null);
        String geometry = source.getGeometrySource()
            .map(shader -> SodiumTerrainShaderTransformer.transform(ShaderType.GEOMETRY, shader))
            .orElse(null);
        String fragment = source.getFragmentSource()
            .map(shader -> SodiumTerrainShaderTransformer.transform(ShaderType.FRAGMENT, shader))
            .orElse(null);

        ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
            packName,
            programName,
            vertex,
            geometry,
            fragment,
            environmentDefines);

        return new TerrainProgramSource(
            programName,
            prepared.getVertexSource(),
            prepared.getGeometrySource(),
            prepared.getFragmentSource());
    }

    private static final class TerrainProgramSource {
        private final String programName;
        private final String vertexSource;
        private final String geometrySource;
        private final String fragmentSource;

        private TerrainProgramSource(String programName,
                                     String vertexSource,
                                     String geometrySource,
                                     String fragmentSource) {
            this.programName = programName;
            this.vertexSource = vertexSource;
            this.geometrySource = geometrySource;
            this.fragmentSource = fragmentSource;
        }

        private boolean hasVertexAndFragment() {
            return vertexSource != null && fragmentSource != null;
        }
    }
}
