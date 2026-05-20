package net.oculus.pipeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.Framebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.FramebufferManager;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.blending.AlphaTestOverride;
import net.oculus.gl.blending.BlendModeOverride;
import net.oculus.gl.blending.BufferBlendInformation;
import net.oculus.gl.blending.BufferBlendOverride;
import net.oculus.gl.program.Program;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.oculus.Oculus;
import net.oculus.blockrendering.BlockMaterialMapping;
import net.oculus.colorspace.ColorSpace;
import net.oculus.colorspace.ColorSpaceComputeConverter;
import net.oculus.colorspace.ColorSpaceConverter;
import net.oculus.colorspace.ColorSpaceFragmentConverter;
import net.oculus.colorspace.NoOpColorSpaceConverter;
import net.oculus.config.OculusConfig;
import net.oculus.gl.image.ImageLimits;
import net.oculus.gl.texture.DepthBufferFormat;
import net.oculus.layer.GbufferPrograms;
import net.oculus.pipeline.buffer.ShaderStorageBufferManager;
import net.oculus.pipeline.context.ObjectContext;
import net.oculus.pipeline.state.StateTracker;
import net.oculus.pipeline.texture.CustomImageManager;
import net.oculus.pipeline.texture.CustomTextureManager;
import net.oculus.samplers.IrisSamplers;
import net.oculus.shader.ShaderLoader;
import net.oculus.shader.ShaderPreprocessor;
import net.oculus.shaderpack.CloudSetting;
import net.oculus.shaderpack.ComputeSource;
import net.oculus.shaderpack.OptionalBoolean;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.PackShadowDirectives;
import net.oculus.shaderpack.ParticleRenderingOrder;
import net.oculus.shaderpack.ProgramFallbackResolver;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.StringPair;
import net.oculus.shaderpack.loading.ProgramId;
import net.oculus.texture.FallbackTextures;
import net.oculus.texture.TextureInfoCache;
import net.oculus.texture.format.TextureFormat;
import net.oculus.texture.format.TextureFormatLoader;
import net.oculus.texture.pbr.PBRTextureHolder;
import net.oculus.texture.pbr.PBRTextureManager;
import net.oculus.texture.pbr.PBRType;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.CompatibilityUniforms;
import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.GameplayUniforms;
import net.oculus.uniforms.ShadowUniforms;
import net.oculus.uniforms.custom.CustomUniformExpressionManager;
import net.oculus.vendored.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/**
 * Shader-driven world renderer that compiles and uses shader programs from shader packs.
 * This pipeline intercepts Minecraft's rendering and applies custom shader programs.
 */
public final class ShaderWorldRenderingPipeline implements WorldRenderingPipeline {
    private static final Logger LOGGER = LogManager.getLogger(ShaderWorldRenderingPipeline.class);
    private static final ProgramId[] PROGRAM_IDS = createProgramIdTable();
    private static final int[] DEFAULT_GBUFFER_DRAW_BUFFERS = new int[] {0};
    private static final Vector4f SHADOW_CLEAR_DEFAULT = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

    private final ShaderPack pack;
    private final ProgramSet programSet;
    private final ShaderProperties shaderProperties;
    private final PackDirectives directives;
    private final PackRenderTargetDirectives renderTargetDirectives;
    private final PackShadowDirectives shadowDirectives;
    private final FramebufferManager framebufferManager;
    private final ShaderLoader shaderLoader;
    private final CustomTextureManager customTextureManager;
    private final CustomImageManager customImageManager;
    private final CustomUniformExpressionManager customUniforms;
    private final ShaderStorageBufferManager shaderStorageBufferManager;
    private final ProgramFallbackResolver programFallbackResolver;
    private final Map<String, TextureBinding> registeredRenderTargetBindings = new HashMap<>();
    private final Map<String, Framebuffer> gbufferFramebuffersBeforeTranslucent = new HashMap<>();
    private final Map<String, Framebuffer> gbufferFramebuffersAfterTranslucent = new HashMap<>();
    private boolean framebuffersInitialized;
    private boolean gbufferBound;
    private boolean shadersCompiled;
    private boolean isBeforeTranslucent = true;
    private boolean handDepthCapturedThisFrame;
    private boolean shadowRenderTargetsPreparedThisFrame;
    private boolean isRenderingShadow;
    private boolean sodiumTerrainRendering;
    private boolean isPostChain;
    private boolean isMainBound = true;
    private boolean bindingShaderFramebuffer;
    // Active shader program for current render phase
    private Program activeGbufferProgram;
    private String activeGbufferProgramName;

    private final FrameUpdateNotifier frameUpdateNotifier = new FrameUpdateNotifier();
    private final RenderTargetStateListener renderTargetStateListener = new RenderTargetStateListener() {
        @Override
        public void beginPostChain() {
            isPostChain = true;
            unbindProgram();
        }

        @Override
        public void endPostChain() {
            isPostChain = false;
        }

        @Override
        public void setIsMainBound(boolean bound) {
            if (bindingShaderFramebuffer && !bound) {
                isMainBound = true;
                return;
            }

            isMainBound = bound;

            if (!prepared || !isRenderingWorld || isRenderingFullScreenPass || isPostChain) {
                return;
            }

            if (bound) {
                activeGbufferProgram = null;
                activeGbufferProgramName = null;
            } else {
                unbindProgram();
            }
        }
    };
    private final SodiumTerrainPipeline sodiumTerrainPipeline;

    // Shadow rendering
    private net.oculus.pipeline.shadow.ShadowRenderer shadowRenderer;
    private net.oculus.pipeline.shadow.ShadowMap shadowMap;
    private List<ClearPass> disabledShadowClearPassesFull = Collections.emptyList();
    private boolean shadowRendererInitialized;

    // Post-processing renderers
    private net.oculus.postprocess.CompositeRenderer prepareRenderer;
    private net.oculus.postprocess.CompositeRenderer deferredRenderer;
    private net.oculus.postprocess.CompositeRenderer compositeRenderer;
    private net.oculus.postprocess.FinalPassRenderer finalPassRenderer;
    private ColorSpaceConverter colorSpaceConverter;
    private ColorSpace currentColorSpace = ColorSpace.SRGB;
    private int colorSpaceWidth = -1;
    private int colorSpaceHeight = -1;
    private net.oculus.postprocess.BufferFlipper bufferFlipper;
    private net.oculus.postprocess.CenterDepthSampler centerDepthSampler;
    private net.oculus.rendertarget.RenderTargets renderTargets;
    private List<ClearPass> clearPassesFull = Collections.emptyList();
    private List<ClearPass> clearPasses = Collections.emptyList();
    private com.google.common.collect.ImmutableSet<Integer> flippedAfterPrepare = com.google.common.collect.ImmutableSet.of();
    private com.google.common.collect.ImmutableSet<Integer> flippedAfterTranslucent = com.google.common.collect.ImmutableSet.of();
    private boolean renderTargetDependentsDirty;

    private final CloudSetting cloudSetting;
    private final boolean renderUnderwaterOverlay;
    private final boolean renderVignette;
    private final boolean renderSun;
    private final boolean renderMoon;
    private final boolean writeRainAndSnowToDepthBuffer;
    private final boolean writeBeaconBeamToDepthBuffer;
    private final ParticleRenderingOrder particleRenderingOrder;
    private final boolean allowConcurrentCompute;
    private final boolean oldLighting;
    private final boolean disableFrustumCulling;
    private final boolean disableOcclusionCulling;
    private final boolean prepareBeforeShadow;
    private final OptionalInt forcedShadowDistanceChunks;

    private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
    private WorldRenderingPhase overridePhase;
    private InputAvailability inputs = new InputAvailability(false, false, false);
    private SpecialCondition specialCondition;
    private boolean prepared;
    private boolean isRenderingWorld;
    private boolean isRenderingFullScreenPass;
    private boolean setupLogged;
    private boolean frameRuntimeUniformsPrepared;
    private int currentNormalTexture;
    private int currentSpecularTexture;

    public ShaderWorldRenderingPipeline(ShaderPack pack, ProgramSet programSet, ShaderProperties shaderProperties) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.programSet = Objects.requireNonNull(programSet, "programSet");
        rejectUnsupportedShadowCompositeRasterPrograms(this.programSet);
        this.shaderProperties = Objects.requireNonNull(shaderProperties, "shaderProperties");
        this.directives = Objects.requireNonNull(programSet.getPackDirectives(), "directives");
        this.renderTargetDirectives = directives.getRenderTargetDirectives();
        this.shadowDirectives = directives.getShadowDirectives();
        this.prepareBeforeShadow = directives.isPrepareBeforeShadow();
        Object2IntMap<IBlockState> blockStateIds = pack.getBlockStateIdMap();
        Map<Block, BlockRenderLayer> renderLayerOverrides =
            BlockMaterialMapping.createRenderLayerMap(pack.getIdMap().getBlockRenderTypeMap());
        Object2IntFunction<net.oculus.shaderpack.materialmap.NamespacedId> entityIds =
            pack.getIdMap().getEntityIdMap();
        this.framebufferManager = new FramebufferManager(this.directives);

        this.customTextureManager = CustomTextureManager.fromShaderPack(pack, directives);
        this.customImageManager = new CustomImageManager(shaderProperties, pack.getOptionValues());
        this.customUniforms = CustomUniformExpressionManager.fromShaderPack(pack);
        this.shaderStorageBufferManager = new ShaderStorageBufferManager(shaderProperties);
        this.shaderLoader = new ShaderLoader(
            programSet,
            null,
            customTextureManager,
            customImageManager,
            () -> renderTargets,
            this::getCurrentRenderTargetReadBuffers,
            this::getOrCreateShadowMap,
            customUniforms,
            frameUpdateNotifier
        );
        this.programFallbackResolver = new ProgramFallbackResolver(programSet);
        this.sodiumTerrainPipeline = new SodiumTerrainPipeline(
            pack.getName(),
            programSet,
            ShaderPreprocessor.createEnvironmentDefines(programSet),
            customTextureManager,
            customImageManager,
            () -> renderTargets,
            this::getCurrentRenderTargetReadBuffers,
            this::getOrCreateShadowMap,
            customUniforms,
            frameUpdateNotifier,
            this::prepareSodiumTerrainResources
        );

        this.cloudSetting = directives.getCloudSetting();
        this.renderUnderwaterOverlay = directives.underwaterOverlay();
        this.renderVignette = directives.vignette();
        this.renderSun = directives.shouldRenderSun();
        this.renderMoon = directives.shouldRenderMoon();
        this.writeRainAndSnowToDepthBuffer = directives.rainDepth();
        this.writeBeaconBeamToDepthBuffer = directives.beaconBeamDepth();
        this.particleRenderingOrder = directives.getParticleRenderingOrder().resolve(hasDeferredPasses(programSet));
        this.allowConcurrentCompute = directives.getConcurrentCompute();
        this.oldLighting = directives.isOldLighting();
        this.disableFrustumCulling = !directives.shouldUseFrustumCulling();
        this.disableOcclusionCulling = !directives.shouldUseOcclusionCulling();
        this.forcedShadowDistanceChunks = resolveForcedShadowDistance(shadowDirectives);
        GameplayUniforms.configure(directives);
        ShadowUniforms.configure(directives);
        BlockRenderingSettings.INSTANCE.setBlockStateIds(blockStateIds);
        BlockRenderingSettings.INSTANCE.setRenderLayerOverrides(renderLayerOverrides);
        BlockRenderingSettings.INSTANCE.setEntityIds(entityIds);
        BlockRenderingSettings.INSTANCE.setAmbientOcclusionLevel(directives.getAmbientOcclusionLevel());
        BlockRenderingSettings.INSTANCE.setDisableDirectionalShading(shouldDisableDirectionalShading());
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(directives.shouldUseSeparateAo());
        BlockRenderingSettings.INSTANCE.setUseExtendedVertexFormat(true);

        LOGGER.info("Initialized shader pipeline for pack {}", pack.getName());
    }

    static boolean hasDeferredPasses(ProgramSet programSet) {
        for (ProgramSource source : programSet.getDeferred()) {
            if (source != null && source.isValid()) {
                return true;
            }
        }

        for (ComputeSource[] computeSources : programSet.getDeferredCompute()) {
            if (computeSources == null) {
                continue;
            }
            for (ComputeSource source : computeSources) {
                if (source != null && source.isValid()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static ProgramId[] createProgramIdTable() {
        ProgramId[] ids = new ProgramId[] {
            ProgramId.Basic, ProgramId.Textured, ProgramId.TexturedLit,
            ProgramId.SkyBasic, ProgramId.SkyTextured, ProgramId.SkyTextured,
            null, null, ProgramId.Terrain,
            null, null, ProgramId.Water,
            null, ProgramId.Clouds, ProgramId.Clouds,
            null, ProgramId.DamagedBlock, ProgramId.DamagedBlock,
            ProgramId.Block, ProgramId.Block, ProgramId.Block,
            ProgramId.BeaconBeam, ProgramId.BeaconBeam, ProgramId.BeaconBeam,
            ProgramId.Entities, ProgramId.Entities, ProgramId.Entities,
            ProgramId.EntitiesTrans, ProgramId.EntitiesTrans, ProgramId.EntitiesTrans,
            null, ProgramId.ArmorGlint, ProgramId.ArmorGlint,
            null, ProgramId.SpiderEyes, ProgramId.SpiderEyes,
            ProgramId.Hand, ProgramId.Hand, ProgramId.Hand,
            ProgramId.HandWater, ProgramId.HandWater, ProgramId.HandWater,
            null, null, ProgramId.Weather,
            null, ProgramId.TexturedLit, ProgramId.TexturedLit,
            ProgramId.Shadow, ProgramId.Shadow, ProgramId.Shadow
        };

        if (ids.length != RenderCondition.values().length * 3) {
            throw new IllegalStateException("Program ID table length mismatch");
        }

        return ids;
    }

    static void rejectUnsupportedShadowCompositeRasterPrograms(ProgramSet programSet) {
        if (programSet == null) {
            return;
        }

        String unsupportedProgram = firstUnsupportedShadowCompositeRasterProgram(programSet);
        if (unsupportedProgram != null) {
            throw new ProgramLoadException("Shader pack requires unsupported raster shadow composite pass "
                + unsupportedProgram
                + ". The Oculus 1.12.2 backport currently supports root shadow passes and compute-only shadowcomp "
                + "passes, but raster shadowcomp color flipping is not implemented; disable raster shadowcomp or "
                + "select a shader-pack profile without raster shadow composite passes.");
        }
    }

    private static String firstUnsupportedShadowCompositeRasterProgram(ProgramSet programSet) {
        ProgramSource[] shadowComposites = programSet.getShadowComposite();
        if (shadowComposites != null) {
            for (ProgramSource source : shadowComposites) {
                if (source != null && source.isValid()) {
                    return source.getName();
                }
            }
        }

        return null;
    }

    private net.oculus.pipeline.shadow.ShadowMap getOrCreateShadowMap() {
        if (shadowMap == null && shadowDirectives != null && shadowDirectives.getResolution() > 0) {
            net.oculus.pipeline.shadow.ShadowMap createdShadowMap = new net.oculus.pipeline.shadow.ShadowMap(
                directives, shaderProperties, net.oculus.util.Config.get(), true);
            List<ClearPass> createdDisabledShadowClearPassesFull = Collections.emptyList();
            try {
                if (shadowDirectives.isShadowEnabled() == OptionalBoolean.FALSE) {
                    createdDisabledShadowClearPassesFull = ClearPassCreator.createShadowClearPasses(
                        createdShadowMap, true, shadowDirectives);
                }
            } catch (RuntimeException | Error exception) {
                Throwable failure = null;
                failure = destroyShadowClearPassFramebuffers(
                    failure, createdShadowMap, createdDisabledShadowClearPassesFull);
                failure = runCleanup(failure, createdShadowMap::destroy);
                addSuppressedCleanupFailure(exception, failure);
                throw exception;
            }
            shadowMap = createdShadowMap;
            disabledShadowClearPassesFull = createdDisabledShadowClearPassesFull;
            shadowRendererInitialized = false;
        }
        return shadowMap;
    }

    private void ensureForcedShadowMapInitialized() {
        if (shadowDirectives != null && shadowDirectives.isShadowEnabled() == OptionalBoolean.TRUE) {
            getOrCreateShadowMap();
        }
    }

    private void ensureShadowRendererInitialized() {
        if (shadowRendererInitialized) {
            return;
        }

        if (shadowDirectives == null || shadowDirectives.getResolution() <= 0) {
            shadowRendererInitialized = true;
            this.shadowMap = null;
            this.shadowRenderer = null;
            return;
        }

        ensureForcedShadowMapInitialized();

        if (shadowMap == null || !shadowMap.isEnabled()
                || !net.oculus.util.Config.get().shadowsEnabled(directives)) {
            this.shadowRenderer = null;
            shadowRendererInitialized = true;
            return;
        }

        net.oculus.pipeline.shadow.ShadowRenderer createdShadowRenderer = null;
        try {
            createdShadowRenderer = new net.oculus.pipeline.shadow.ShadowRenderer(
                directives,
                shadowDirectives,
                programSet.getShadow().orElse(null),
                programSet.getShadowCompute(),
                programSet.getShadowCompCompute(),
                shadowMap,
                () -> renderTargets,
                () -> Collections.emptySet(),
                this::getShadowReadBuffers,
                this,
                customTextureManager::getNoiseTextureId,
                customTextureManager,
                customImageManager,
                pack.getName(),
                net.oculus.shader.ShaderPreprocessor.createEnvironmentDefines(programSet),
                customUniforms,
                frameUpdateNotifier);
            this.shadowRenderer = createdShadowRenderer;
            updateShadowRendererImageCullingHint();
            shadowRendererInitialized = true;
        } catch (RuntimeException | Error exception) {
            this.shadowRenderer = null;
            shadowRendererInitialized = false;
            Throwable failure = null;
            if (createdShadowRenderer != null) {
                failure = runCleanup(failure, createdShadowRenderer::destroy);
            }
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        LOGGER.info("Shadow rendering enabled: {}x{}",
            shadowDirectives.getResolution(), shadowDirectives.getResolution());
    }

    private void updateShadowRendererImageCullingHint() {
        if (!shadersCompiled || shadowRenderer == null) {
            return;
        }

        ProgramSource shadowSource = programSet.getShadow().orElse(null);
        if (shadowSource == null) {
            return;
        }

        Program shadowProgram = shaderLoader.getProgram(shadowSource.getName(), new InputAvailability(true, true, true));
        shadowRenderer.setUsesImages(shadowProgram != null && shadowProgram.getActiveImages() > 0);
    }

    private OptionalInt resolveForcedShadowDistance(PackShadowDirectives shadowDirectives) {
        if (shadowDirectives == null || !shadowDirectives.isDistanceRenderMulExplicit()) {
            return OptionalInt.empty();
        }

        float mul = shadowDirectives.getDistanceRenderMul();
        if (mul < 0.0F) {
            return OptionalInt.of(-1);
        }

        float distance = shadowDirectives.getDistance();
        int chunks = ((int) (distance * mul) + 15) / 16;
        return OptionalInt.of(chunks);
    }

    private void ensureSetup() {
        if (setupLogged) {
            return;
        }

        LOGGER.info("Preparing shader pipeline for {}", pack.getName());
        PackRenderTargetDirectives targets = renderTargetDirectives;
        LOGGER.debug("Render target directives: {} entries", targets.getRenderTargetSettings().size());
        setupLogged = true;
    }

    private void ensureShadersCompiled() {
        if (shadersCompiled) {
            return;
        }

        try {
            ObjectContext context = ObjectContext.forPipeline(programSet, directives);
            shaderLoader.initialize(context);
            shadersCompiled = true;
            if (shadowMap != null && !shadowRendererInitialized) {
                ensureShadowRendererInitialized();
            }
            updateShadowRendererImageCullingHint();
            LOGGER.info("Shader programs compiled for {}", pack.getName());

            // Log which programs were loaded
            Map<String, Program> programs = shaderLoader.getPrograms();
            LOGGER.info("Loaded {} shader program object(s); {} unspecialized program(s): {}",
                shaderLoader.getProgramCount(), programs.size(), programs.keySet());
        } catch (RuntimeException | Error exception) {
            LOGGER.error("Failed to compile shaders for pack {}", pack.getName(), exception);
            shadersCompiled = false;
            throw exception;
        }
    }

    @Override
    public void beginWorldRendering(float partialTicks) {
        CapturedRenderingState.INSTANCE.beginFrame(partialTicks);
        frameRuntimeUniformsPrepared = false;

        boolean levelPrepared = false;
        try {
            beginLevelRendering();
            levelPrepared = true;
            StateTracker.INSTANCE.refreshFromGlState();
            inputs = StateTracker.INSTANCE.getInputs();
            bindGbufferFramebuffer();
            syncProgram();
        } catch (RuntimeException | Error exception) {
            if (levelPrepared) {
                rethrowCleanupFailure(restoreAfterFrameBeginFailure(exception));
            }
            throw exception;
        }
    }

    @Override
    public void afterCameraSetup(float partialTicks) {
        try {
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.afterCameraSetup.entry");
            CapturedRenderingState.INSTANCE.capturePostCameraSetup(partialTicks);
            if (!frameRuntimeUniformsPrepared) {
                GameplayUniforms.onFrameStart();
                CompatibilityUniforms.onFrameStart();
                customUniforms.beginFrame();
                frameRuntimeUniformsPrepared = true;
            }
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.afterCameraSetup.uniforms");
            prepareShadowRenderTargets();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.afterCameraSetup.prepareShadowTargets");
            syncProgram();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.afterCameraSetup.syncProgram");
        } catch (RuntimeException | Error exception) {
            if (prepared || isRenderingWorld) {
                rethrowCleanupFailure(restoreAfterFrameBeginFailure(exception));
            }
            throw exception;
        }
    }

    @Override
    public void beginLevelRendering() {
        if (prepared || isRenderingWorld) {
            throw new IllegalStateException("Called beginLevelRendering but level rendering appears to still be in progress");
        }

        try {
            ensureSetup();
            ensureFramebufferManagerInitialized();
            ensureFramebufferDimensionsUpToDate();
            customTextureManager.initialize();
            customTextureManager.applyGlobalOverrides();
            customImageManager.initializeOrResize(framebufferManager.getWidth(), framebufferManager.getHeight());
            customImageManager.clearNewFrameImages();
            shaderStorageBufferManager.initialize();
            shaderStorageBufferManager.bindAll();
            ensureShadowRendererInitialized();
            ensureShadersCompiled();
            prepared = true;
            isRenderingWorld = true;
            isRenderingFullScreenPass = false;
            isMainBound = true;
            bindingShaderFramebuffer = false;
            isPostChain = false;
            isBeforeTranslucent = true;
            handDepthCapturedThisFrame = false;
            shadowRenderTargetsPreparedThisFrame = false;
            isRenderingShadow = false;
            sodiumTerrainRendering = false;
            phase = WorldRenderingPhase.NONE;
            overridePhase = null;
            frameUpdateNotifier.onNewFrame();
            if (shadersCompiled) {
                clearRenderTargets();
            }
            setPhase(WorldRenderingPhase.SKY);
        } catch (RuntimeException | Error exception) {
            rethrowCleanupFailure(restoreAfterFrameBeginFailure(exception));
            throw exception;
        }
    }

    private void bindProgram(ResolvedProgram resolved) {
        if (resolved == null) {
            unbindProgram();
            return;
        }

        Program program = resolved.program;
        if (program == null) {
            bindWorldFramebufferForProgram(resolved, false);
            Program.unbind();
            activeGbufferProgram = null;
            activeGbufferProgramName = null;
            restoreRenderStateOverrides();
            applyNoProgramShadowFallbackRenderState(resolved);
            return;
        }

        if (activeGbufferProgram == program) {
            bindWorldFramebufferForProgram(resolved, false);
            restoreRenderStateOverrides();
            shaderStorageBufferManager.bindAll();
            program.use();
            applyRenderStateOverrides(resolved);
            return;
        }

        bindWorldFramebufferForProgram(resolved, false);
        restoreRenderStateOverrides();
        shaderStorageBufferManager.bindAll();
        program.use();
        applyRenderStateOverrides(resolved);
        activeGbufferProgram = program;
        activeGbufferProgramName = program.getName();
    }

    private void applyNoProgramShadowFallbackRenderState(ResolvedProgram resolved) {
        if (isRenderingShadow && resolved != null
                && resolved.source == null && resolved.id == ProgramId.Shadow) {
            BlendModeOverride.OFF.apply();
        }
    }

    private void bindWorldFramebufferForProgram(ResolvedProgram resolved, boolean clear) {
        if (isRenderingShadow) {
            requirePipelineResource(shadowRenderer, "shadow renderer", "shadow program bind")
                .bindFramebufferForShadowPass();
            return;
        }

        bindGbufferFramebuffer(resolved, clear);
    }

    private ResolvedProgram resolveProgram(RenderCondition condition, InputAvailability availability) {
        ProgramId id = resolveProgramId(condition, availability);
        if (id == null) {
            return null;
        }

        ProgramSource source = programFallbackResolver.resolveNullable(id);
        if (source == null) {
            return new ResolvedProgram(id, null, null);
        }

        Program program = shaderLoader.getProgram(source.getName(), availability);
        if (program == null) {
            throw new ProgramLoadException("Failed to create pass for " + source.getName()
                + " for rendering condition " + condition
                + " specialized to input availability " + availability);
        }
        return new ResolvedProgram(id, source, program);
    }

    private void applyRenderStateOverrides(ResolvedProgram resolved) {
        if (resolved == null || resolved.source == null) {
            return;
        }

        resolved.source.getDirectives().getAlphaTestOverride().ifPresent(AlphaTestOverride::apply);
        BlendModeOverride blendModeOverride = resolved.source.getDirectives().getBlendModeOverride()
            .orElse(resolved.id.getBlendModeOverride().orElse(null));
        if (blendModeOverride != null) {
            blendModeOverride.apply();
        }

        applyBufferBlendOverrides(resolved);
    }

    private void applyBufferBlendOverrides(ResolvedProgram resolved) {
        int[] drawBuffers = resolved.source.getDirectives().getDrawBuffers();
        for (BufferBlendInformation information : resolved.source.getDirectives().getBufferBlendOverrides()) {
            int drawBufferIndex = indexOf(drawBuffers, information.getIndex());
            if (drawBufferIndex < 0) {
                continue;
            }

            new BufferBlendOverride(drawBufferIndex, information.getBlendMode()).apply();
        }
    }

    private static int indexOf(int[] values, int needle) {
        if (values == null) {
            return -1;
        }

        for (int i = 0; i < values.length; i++) {
            if (values[i] == needle) {
                return i;
            }
        }

        return -1;
    }

    private void restoreRenderStateOverrides() {
        AlphaTestOverride.restore();
        BlendModeOverride.restore();
    }

    private void restoreRenderStateOverridesForDestroy() {
        BlendModeOverride.restore();
        AlphaTestOverride.restore();
    }

    private Throwable restoreAfterFrameBeginFailure(Throwable failure) {
        failure = runCleanup(failure, Program::unbind);
        activeGbufferProgram = null;
        activeGbufferProgramName = null;
        failure = runCleanup(failure, this::restoreRenderStateOverrides);
        failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);
        resetFrameLifecycleAfterBeginFailure();
        return failure;
    }

    private void resetFrameLifecycleAfterBeginFailure() {
        prepared = false;
        isRenderingWorld = false;
        isRenderingFullScreenPass = false;
        gbufferBound = false;
        isMainBound = true;
        bindingShaderFramebuffer = false;
        isPostChain = false;
        isBeforeTranslucent = true;
        handDepthCapturedThisFrame = false;
        shadowRenderTargetsPreparedThisFrame = false;
        isRenderingShadow = false;
        sodiumTerrainRendering = false;
        frameRuntimeUniformsPrepared = false;
        phase = WorldRenderingPhase.NONE;
        overridePhase = null;
    }

    private ProgramId resolveProgramId(RenderCondition condition, InputAvailability availability) {
        int idx;
        if (availability.texture && availability.lightmap) {
            idx = 2;
        } else if (availability.texture) {
            idx = 1;
        } else {
            idx = 0;
        }

        ProgramId id = PROGRAM_IDS[condition.ordinal() * 3 + idx];
        return id == null ? PROGRAM_IDS[idx] : id;
    }

    private RenderCondition getCondition(WorldRenderingPhase phase) {
        if (isRenderingShadow) {
            return RenderCondition.SHADOW;
        }

        if (specialCondition == SpecialCondition.BEACON_BEAM) {
            return RenderCondition.BEACON_BEAM;
        }
        if (specialCondition == SpecialCondition.ENTITY_EYES) {
            return RenderCondition.ENTITY_EYES;
        }
        if (specialCondition == SpecialCondition.GLINT) {
            return RenderCondition.GLINT;
        }

        switch (phase) {
            case NONE:
            case OUTLINE:
            case DEBUG:
            case PARTICLES:
                return RenderCondition.DEFAULT;
            case SKY:
            case SUNSET:
            case CUSTOM_SKY:
            case SUN:
            case MOON:
            case STARS:
            case VOID:
                return RenderCondition.SKY;
            case TERRAIN_SOLID:
            case TERRAIN_CUTOUT:
            case TERRAIN_CUTOUT_MIPPED:
                return RenderCondition.TERRAIN_OPAQUE;
            case ENTITIES:
                return isStandardTranslucentBlend() ? RenderCondition.ENTITIES_TRANSLUCENT : RenderCondition.ENTITIES;
            case BLOCK_ENTITIES:
                return RenderCondition.BLOCK_ENTITIES;
            case DESTROY:
                return RenderCondition.DESTROY;
            case HAND_SOLID:
                return RenderCondition.HAND_OPAQUE;
            case TERRAIN_TRANSLUCENT:
            case TRIPWIRE:
                return RenderCondition.TERRAIN_TRANSLUCENT;
            case CLOUDS:
                return RenderCondition.CLOUDS;
            case RAIN_SNOW:
                return RenderCondition.RAIN_SNOW;
            case HAND_TRANSLUCENT:
                return RenderCondition.HAND_TRANSLUCENT;
            case WORLD_BORDER:
                return RenderCondition.WORLD_BORDER;
            default:
                throw new IllegalStateException("Unknown render phase " + phase);
        }
    }

    private boolean isStandardTranslucentBlend() {
        return GL11.glIsEnabled(GL11.GL_BLEND)
            && GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL11.GL_SRC_ALPHA
            && GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) == GL11.GL_ONE_MINUS_SRC_ALPHA
            && GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA) == GL11.GL_ONE
            && GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) == GL11.GL_ONE_MINUS_SRC_ALPHA;
    }

    /**
     * Unbinds any active shader program.
     */
    private void unbindProgram() {
        Throwable failure = null;
        failure = runCleanup(failure, Program::unbind);
        activeGbufferProgram = null;
        activeGbufferProgramName = null;
        failure = runCleanup(failure, this::restoreRenderStateOverrides);
        rethrowCleanupFailure(failure);
    }

    @Override
    public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks) {
        try {
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.renderShadows.entry");
            ensureShadowRendererInitialized();
            prepareShadowRenderTargets();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.renderShadows.prepareShadowTargets");

            if (prepareBeforeShadow) {
                runPreparePass();
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.renderShadows.prepareBeforeShadow");
            }

            if (shadowRenderer != null) {
                isRenderingShadow = true;
                Throwable failure = null;
                try {
                    if (shadowRenderer.shouldRenderThisFrame()) {
                        shadowRenderer.bindFramebufferForShadowPass();
                        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.renderShadows.bindShadowFramebuffer");
                        syncProgram();
                        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.renderShadows.syncShadowProgram");
                        shaderStorageBufferManager.bindAll();
                        shadowRenderer.renderShadows(renderGlobal, cameraEntity, partialTicks);
                        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.renderShadows.shadowRendererReturn");
                    }
                } catch (RuntimeException | Error exception) {
                    failure = exception;
                    throw exception;
                } finally {
                    try {
                        failure = restoreAfterShadowRender(failure);
                    } finally {
                        isRenderingShadow = false;
                    }
                    rethrowCleanupFailure(failure);
                }
            }

            if (!prepareBeforeShadow) {
                runPreparePass();
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.renderShadows.prepareAfterShadow");
            }
        } catch (RuntimeException | Error exception) {
            if (prepared || isRenderingWorld) {
                rethrowCleanupFailure(restoreAfterFrameBeginFailure(exception));
            }
            throw exception;
        }
    }

    private Throwable restoreAfterShadowRender(Throwable failure) {
        failure = runCleanup(failure, this::unbindProgram);
        failure = runCleanup(failure, () -> bindGbufferFramebuffer(false));
        failure = runCleanup(failure, ShaderWorldRenderingPipeline::restoreMinecraftViewport);
        return failure;
    }

    private static void restoreMinecraftViewport() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null) {
            GL11.glViewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
        }
    }

    private void prepareShadowRenderTargets() {
        if (shadowRenderTargetsPreparedThisFrame) {
            return;
        }

        ensureShadowRendererInitialized();
        if (shadowRenderer == null) {
            return;
        }

        shadowRenderer.prepareRenderTargets();
        shadowRenderTargetsPreparedThisFrame = true;
    }

    @Override
    public void addDebugText(List<String> messages) {
        messages.add("Oculus shader pipeline active for " + pack.getName());
        messages.add("Phase: " + getPhase());
        messages.add("Color space: " + getConfiguredColorSpace().name());
        if (activeGbufferProgramName != null) {
            messages.add("Active program: " + activeGbufferProgramName);
        }
        if (shadersCompiled) {
            messages.add("Programs loaded: " + shaderLoader.getProgramCount());
        }
        if (shadowRenderer != null) {
            shadowRenderer.addDebugText(messages);
        } else {
            messages.add("[Oculus] Shadow Maps: not used by shader pack");
        }
    }

    @Override
    public OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
        return forcedShadowDistanceChunks;
    }

    @Override
    public WorldRenderingPhase getPhase() {
        return overridePhase != null ? overridePhase : phase;
    }

    @Override
    public void beginSodiumTerrainRendering() {
        sodiumTerrainRendering = true;
        syncProgram();
    }

    @Override
    public void endSodiumTerrainRendering() {
        sodiumTerrainRendering = false;
        activeGbufferProgram = null;
        activeGbufferProgramName = null;
        syncProgram();
    }

    @Override
    public void setOverridePhase(WorldRenderingPhase phase) {
        this.overridePhase = phase;
        GbufferPrograms.runPhaseChangeNotifier();
        syncProgram();
    }

    @Override
    public void setPhase(WorldRenderingPhase phase) {
        this.phase = phase == null ? WorldRenderingPhase.NONE : phase;
        GbufferPrograms.runPhaseChangeNotifier();
        syncProgram();
    }

    @Override
    public void setInputs(InputAvailability availability) {
        this.inputs = availability == null ? new InputAvailability(false, false, false) : availability;
        syncProgram();
    }

    @Override
    public void setSpecialCondition(SpecialCondition special) {
        this.specialCondition = special;
        syncProgram();
    }

    @Override
    public void syncProgram() {
        boolean worldTargetReady = isMainBound || gbufferBound || isRenderingShadow;
        if (!prepared || !isRenderingWorld || isRenderingFullScreenPass
                || isPostChain || !worldTargetReady
                || !shadersCompiled) {
            return;
        }

        WorldRenderingPhase currentPhase = getPhase();
        RenderCondition condition = getCondition(currentPhase);
        InputAvailability rawAvailability = sodiumTerrainRendering
            ? new InputAvailability(true, true, false)
            : inputs;
        InputAvailability availability = normalizeLegacyImmediateAvailability(condition, rawAvailability);

        ResolvedProgram resolved = resolveProgram(condition, availability);
        OculusRuntimeValidation.logWorldProgramSelection(
            currentPhase == null ? "null" : currentPhase.name(),
            condition.name(),
            rawAvailability,
            availability,
            resolved == null ? null : resolved.id.name(),
            resolved == null || resolved.source == null ? null : resolved.source.getName(),
            resolved == null || resolved.program == null ? null : resolved.program.getName());
        bindProgram(resolved);
    }

    private static InputAvailability normalizeLegacyImmediateAvailability(RenderCondition condition,
                                                                          InputAvailability availability) {
        InputAvailability safeAvailability = availability == null
            ? new InputAvailability(false, false, false)
            : availability;
        if (condition == RenderCondition.ENTITIES
                || condition == RenderCondition.ENTITIES_TRANSLUCENT
                || condition == RenderCondition.BLOCK_ENTITIES
                || condition == RenderCondition.HAND_OPAQUE
                || condition == RenderCondition.HAND_TRANSLUCENT) {
            return new InputAvailability(true, true, safeAvailability.overlay);
        }
        return safeAvailability;
    }

    public void syncEntityProgramForLegacyDraw() {
        if (!prepared || !isRenderingWorld || isRenderingFullScreenPass
                || isPostChain || !shadersCompiled) {
            OculusRuntimeValidation.logLegacyEntityProgramSync(
                "skip",
                getPhase() == null ? null : getPhase().name(),
                null,
                prepared,
                isRenderingWorld,
                isRenderingFullScreenPass,
                isPostChain,
                shadersCompiled,
                isMainBound,
                gbufferBound,
                isRenderingShadow,
                null,
                -1,
                OculusRenderSystem.getFramebufferBinding(),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            return;
        }

        RenderCondition condition = getCondition(WorldRenderingPhase.ENTITIES);
        InputAvailability rawAvailability = sodiumTerrainRendering
            ? new InputAvailability(true, true, false)
            : inputs;
        InputAvailability availability = normalizeLegacyImmediateAvailability(condition, rawAvailability);
        ResolvedProgram resolved = resolveProgram(condition, availability);
        OculusRuntimeValidation.logWorldProgramSelection(
            WorldRenderingPhase.ENTITIES.name(),
            condition.name(),
            rawAvailability,
            availability,
            resolved == null ? null : resolved.id.name(),
            resolved == null || resolved.source == null ? null : resolved.source.getName(),
            resolved == null || resolved.program == null ? null : resolved.program.getName());
        bindProgram(resolved);
        OculusRuntimeValidation.logLegacyEntityProgramSync(
            "bound",
            WorldRenderingPhase.ENTITIES.name(),
            condition.name(),
            prepared,
            isRenderingWorld,
            isRenderingFullScreenPass,
            isPostChain,
            shadersCompiled,
            isMainBound,
            gbufferBound,
            isRenderingShadow,
            resolved == null || resolved.program == null ? null : resolved.program.getName(),
            resolved == null || resolved.program == null ? -1 : resolved.program.getProgramId(),
            OculusRenderSystem.getFramebufferBinding(),
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }

    @Override
    public RenderTargetStateListener getRenderTargetStateListener() {
        return renderTargetStateListener;
    }

    @Override
    public int getCurrentNormalTexture() {
        return currentNormalTexture;
    }

    @Override
    public int getCurrentSpecularTexture() {
        return currentSpecularTexture;
    }

    @Override
    public void resetPbrTextureBindings() {
        if (!PBRTextureManager.INSTANCE.hasPbrSampler()) {
            currentNormalTexture = 0;
            currentSpecularTexture = 0;
            return;
        }

        updatePbrTextureBindings(PBRTextureManager.INSTANCE.getOrLoadHolder(0));
        PBRTextureManager.notifyPBRTexturesChanged();
    }

    @Override
    public void onBindTexture(int id) {
        if (isRenderingWorld && PBRTextureManager.INSTANCE.hasPbrSampler()) {
            updatePbrTextureBindings(PBRTextureManager.INSTANCE.getOrLoadHolder(id));
            PBRTextureManager.notifyPBRTexturesChanged();
        }
    }

    private void updatePbrTextureBindings(PBRTextureHolder pbrHolder) {
        currentNormalTexture = pbrHolder.getNormalTexture().getGlTextureId();
        currentSpecularTexture = pbrHolder.getSpecularTexture().getGlTextureId();

        TextureFormat textureFormat = TextureFormatLoader.getFormat();
        if (textureFormat != null) {
            textureFormat.setupTextureParameters(PBRType.NORMAL, currentNormalTexture);
            textureFormat.setupTextureParameters(PBRType.SPECULAR, currentSpecularTexture);
        }
    }

    @Override
    public void beginHand() {
        if (isRenderingShadow) {
            return;
        }

        if (!prepared) {
            return;
        }

        requirePipelineResource(renderTargets, "render targets", "beginHand");
        if (!handDepthCapturedThisFrame) {
            renderTargets.copyPreHandDepth();
            handDepthCapturedThisFrame = true;
        }
        syncProgram();
    }

    @Override
    public void beginTranslucents() {
        if (isRenderingShadow) {
            return;
        }

        if (!prepared) {
            return;
        }

        if (!isBeforeTranslucent) {
            return;
        }

        requirePipelineResource(renderTargets, "render targets", "beginTranslucents");
        requirePipelineResource(deferredRenderer, "deferred renderer", "beginTranslucents");
        Throwable failure = null;
        try {
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.beginTranslucents.entry");
            Set<Integer> preDeferredReadBuffers = getActiveReadBuffers();
            dumpValidationRenderTarget("pre-deferred-colortex0", 0, preDeferredReadBuffers);
            dumpValidationRenderTarget("pre-deferred-colortex3", 3, preDeferredReadBuffers);
            dumpValidationRenderTarget("pre-deferred-colortex6", 6, preDeferredReadBuffers);
            dumpValidationDepthTargets("pre-deferred");
            isBeforeTranslucent = false;
            renderTargets.copyPreTranslucentDepth();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.beginTranslucents.copyDepth");
            dumpValidationDepthTargets("post-pretranslucent-depth-copy");
            unbindProgram();
            isRenderingFullScreenPass = true;
            shaderStorageBufferManager.bindAll();
            deferredRenderer.renderAll();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.beginTranslucents.deferred");
            Set<Integer> postDeferredReadBuffers = bufferFlipper == null
                ? Collections.emptySet()
                : bufferFlipper.snapshot();
            dumpValidationRenderTarget("post-deferred-colortex0", 0, postDeferredReadBuffers);
            dumpValidationRenderTarget("post-deferred-colortex3", 3, postDeferredReadBuffers);
            dumpValidationRenderTarget("post-deferred-colortex6", 6, postDeferredReadBuffers);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            failure = restoreAfterDeferredPass(failure);
            rethrowCleanupFailure(failure);
        }
        syncProgram();
    }

    @Override
    public void finalizeLevelRendering() {
        if (!prepared) {
            return;
        }

        Throwable failure = null;
        try {
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.finalize.entry");
            // Unbind any active shader programs before composite pass
            unbindProgram();
            isRenderingWorld = false;
            phase = WorldRenderingPhase.NONE;
            overridePhase = null;
            isRenderingFullScreenPass = true;
            runCompositePass();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.finalize.composite");
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            failure = restoreAfterFinalizeLevelRendering(failure);
            rethrowCleanupFailure(failure);
        }
    }

    private Throwable restoreAfterFinalizeLevelRendering(Throwable failure) {
        failure = runCleanup(failure, this::unbindProgram);
        resetFrameLifecycleAfterFinalize();
        failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);
        failure = runCleanup(failure, ShaderWorldRenderingPipeline::restorePostWorldFixedFunctionState);
        return failure;
    }

    private static void restorePostWorldFixedFunctionState() {
        OculusRenderSystem.restoreDefaultActiveTexture();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }

    private void resetFrameLifecycleAfterFinalize() {
        prepared = false;
        isRenderingWorld = false;
        isRenderingFullScreenPass = false;
        gbufferBound = false;
        isMainBound = true;
        bindingShaderFramebuffer = false;
        isPostChain = false;
        isBeforeTranslucent = true;
        handDepthCapturedThisFrame = false;
        shadowRenderTargetsPreparedThisFrame = false;
        isRenderingShadow = false;
        sodiumTerrainRendering = false;
        frameRuntimeUniformsPrepared = false;
        phase = WorldRenderingPhase.NONE;
        overridePhase = null;
        activeGbufferProgram = null;
        activeGbufferProgramName = null;
    }

    @Override
    public void destroy() {
        Throwable failure = null;
        failure = runCleanup(failure, this::restoreRenderStateOverridesForDestroy);
        failure = runCleanup(failure, this::unbindProgram);
        failure = runCleanup(failure, ShaderWorldRenderingPipeline::unbindCustomFramebuffersForDestroy);
        failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);

        // Destroy shader loader and compiled programs
        failure = runCleanup(failure, () -> {
            if (shaderLoader != null) {
                shaderLoader.destroy();
            }
        });

        failure = runCleanup(failure, this::destroyGbufferFramebuffers);
        failure = runCleanup(failure, this::destroyPostProcessors);
        failure = runCleanup(failure, this::destroyShadowRenderer);
        failure = runCleanup(failure, customImageManager::destroy);
        failure = runCleanup(failure, customTextureManager::destroy);
        failure = runCleanup(failure, shaderStorageBufferManager::destroy);
        failure = runCleanup(failure, framebufferManager::destroy);
        failure = runCleanup(failure, PBRTextureManager.INSTANCE::clear);
        failure = runCleanup(failure, FallbackTextures::destroy);
        resetFrameLifecycleAfterDestroy();
        rethrowCleanupFailure(failure);

        LOGGER.info("Destroyed shader pipeline for {}", pack.getName());
    }

    private void resetFrameLifecycleAfterDestroy() {
        resetFrameLifecycleAfterFinalize();
        framebuffersInitialized = false;
        shadersCompiled = false;
        setupLogged = false;
        shadowRendererInitialized = false;
        frameRuntimeUniformsPrepared = false;
        currentNormalTexture = 0;
        currentSpecularTexture = 0;
    }

    private void destroyPostProcessors() {
        try {
            Throwable failure = null;
            if (prepareRenderer != null) {
                failure = runCleanup(failure, prepareRenderer::destroy);
            }
            if (deferredRenderer != null) {
                failure = runCleanup(failure, deferredRenderer::destroy);
            }
            if (compositeRenderer != null) {
                failure = runCleanup(failure, compositeRenderer::destroy);
            }
            if (finalPassRenderer != null) {
                failure = runCleanup(failure, finalPassRenderer::destroy);
            }
            failure = destroyColorSpaceConverter(failure);
            failure = runCleanup(failure, this::unregisterRenderTargetBindings);
            if (centerDepthSampler != null) {
                failure = runCleanup(failure, centerDepthSampler::destroy);
            }
            if (renderTargets != null) {
                failure = runCleanup(failure, renderTargets::destroy);
            }
            rethrowCleanupFailure(failure);
        } finally {
            prepareRenderer = null;
            deferredRenderer = null;
            compositeRenderer = null;
            finalPassRenderer = null;
            colorSpaceConverter = null;
            centerDepthSampler = null;
            renderTargets = null;
            clearPassesFull = Collections.emptyList();
            clearPasses = Collections.emptyList();
            bufferFlipper = null;
            flippedAfterPrepare = com.google.common.collect.ImmutableSet.of();
            flippedAfterTranslucent = com.google.common.collect.ImmutableSet.of();
            renderTargetDependentsDirty = false;
        }
    }

    private void destroyColorSpaceConverter() {
        try {
            Throwable failure = destroyColorSpaceConverter(null);
            rethrowCleanupFailure(failure);
        } finally {
            colorSpaceConverter = null;
        }
    }

    private Throwable destroyColorSpaceConverter(Throwable failure) {
        if (colorSpaceConverter == null) {
            return failure;
        }
        failure = runCleanup(failure, colorSpaceConverter::destroy);
        return failure;
    }

    private void destroyShadowRenderer() {
        try {
            Throwable failure = null;
            if (shadowRenderer != null) {
                failure = runCleanup(failure, shadowRenderer::destroy);
            }
            failure = destroyDisabledShadowClearPassFramebuffers(failure);
            if (shadowMap != null) {
                failure = runCleanup(failure, shadowMap::destroy);
            }
            rethrowCleanupFailure(failure);
        } finally {
            shadowRenderer = null;
            shadowMap = null;
            disabledShadowClearPassesFull = Collections.emptyList();
            shadowRendererInitialized = false;
            shadowRenderTargetsPreparedThisFrame = false;
        }
    }

    private void destroyGbufferFramebuffers() {
        try {
            Throwable failure = null;
            for (Framebuffer framebuffer : gbufferFramebuffersBeforeTranslucent.values()) {
                failure = destroyGbufferFramebuffer(failure, framebuffer);
            }

            for (Framebuffer framebuffer : gbufferFramebuffersAfterTranslucent.values()) {
                failure = destroyGbufferFramebuffer(failure, framebuffer);
            }
            rethrowCleanupFailure(failure);
        } finally {
            gbufferFramebuffersBeforeTranslucent.clear();
            gbufferFramebuffersAfterTranslucent.clear();
        }
    }

    private void destroyGbufferFramebuffer(Framebuffer framebuffer) {
        rethrowCleanupFailure(destroyGbufferFramebuffer(null, framebuffer));
    }

    private Throwable destroyGbufferFramebuffer(Throwable failure, Framebuffer framebuffer) {
        return destroyGbufferFramebuffer(failure, renderTargets, framebuffer);
    }

    private static Throwable destroyGbufferFramebuffer(Throwable failure,
            net.oculus.rendertarget.RenderTargets owner, Framebuffer framebuffer) {
        if (framebuffer == null) {
            return failure;
        }

        if (owner != null) {
            Throwable ownerFailure = runCleanup(null, () -> owner.destroyFramebuffer(framebuffer.getHandle()));
            if (ownerFailure != null) {
                return addCleanupFailure(failure, ownerFailure);
            }
        }
        failure = runCleanup(failure, framebuffer::destroy);
        return failure;
    }

    @Override
    public SodiumTerrainPipeline getSodiumTerrainPipeline() {
        return sodiumTerrainPipeline;
    }

    @Override
    public FrameUpdateNotifier getFrameUpdateNotifier() {
        return frameUpdateNotifier;
    }

    @Override
    public boolean shouldDisableVanillaEntityShadows() {
        return shadowRenderer != null;
    }

    @Override
    public boolean shouldDisableDirectionalShading() {
        return !oldLighting;
    }

    @Override
    public boolean shouldDisableFrustumCulling() {
        return disableFrustumCulling;
    }

    @Override
    public boolean shouldDisableOcclusionCulling() {
        return disableOcclusionCulling;
    }

    @Override
    public boolean isRenderingShadowPass() {
        return isRenderingShadow;
    }

    @Override
    public boolean shouldRenderTerrainBackFaces(BlockRenderLayer layer) {
        if (layer == null) {
            return false;
        }

        switch (layer) {
            case SOLID:
                return directives.shouldRenderSolidBackFaces();
            case CUTOUT:
                return directives.shouldRenderCutoutBackFaces();
            case CUTOUT_MIPPED:
                return directives.shouldRenderCutoutMippedBackFaces();
            case TRANSLUCENT:
                return directives.shouldRenderTranslucentBackFaces();
            default:
                return false;
        }
    }

    @Override
    public CloudSetting getCloudSetting() {
        return cloudSetting;
    }

    @Override
    public boolean shouldRenderUnderwaterOverlay() {
        return renderUnderwaterOverlay;
    }

    @Override
    public boolean shouldRenderVignette() {
        return renderVignette;
    }

    @Override
    public boolean shouldRenderSun() {
        return renderSun;
    }

    @Override
    public boolean shouldRenderMoon() {
        return renderMoon;
    }

    @Override
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return writeRainAndSnowToDepthBuffer;
    }

    @Override
    public boolean shouldWriteBeaconBeamToDepthBuffer() {
        return writeBeaconBeamToDepthBuffer;
    }

    @Override
    public boolean shouldRenderParticlesBeforeDeferred() {
        return particleRenderingOrder == ParticleRenderingOrder.BEFORE;
    }

    @Override
    public ParticleRenderingOrder getParticleRenderingOrder() {
        return particleRenderingOrder;
    }

    @Override
    public boolean allowConcurrentCompute() {
        return allowConcurrentCompute;
    }

    @Override
    public float getSunPathRotation() {
        return directives.getSunPathRotation();
    }

    @Override
    public FramebufferManager getFramebufferManager() {
        return framebufferManager;
    }

    private void bindGbufferFramebuffer() {
        bindGbufferFramebuffer(true);
    }

    private void bindGbufferFramebuffer(boolean clear) {
        bindGbufferFramebuffer(null, clear);
    }

    private void bindGbufferFramebuffer(ResolvedProgram resolved, boolean clear) {
        if (!shadersCompiled || shaderLoader == null) {
            bindMainFramebufferAfterRenderTargetPreparation();
            gbufferBound = false;
            return;
        }

        ensureFramebufferManagerInitialized();
        ensureFramebufferDimensionsUpToDate();

        int[] drawBuffers = resolveGbufferDrawBuffers(resolved);
        Framebuffer framebuffer = getOrCreateGbufferFramebuffer(isBeforeTranslucent, drawBuffers);
        bindShaderFramebuffer(framebuffer);

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null) {
            GL11.glViewport(0, 0, Math.max(1, minecraft.displayWidth), Math.max(1, minecraft.displayHeight));
        }

        if (clear) {
            clearGbufferDepth();
        }

        gbufferBound = true;
        LOGGER.debug("Bound g-buffer framebuffer for {}", pack.getName());
    }

    private void bindShaderFramebuffer(Framebuffer framebuffer) {
        bindingShaderFramebuffer = true;
        try {
            framebuffer.bind();
        } finally {
            bindingShaderFramebuffer = false;
        }
        isMainBound = true;
    }

    private static void clearGbufferDepth() {
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.depthMask(true);
        Throwable failure = null;
        try {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = runCleanup(null, () -> GlStateManager.depthMask(previousDepthMask));
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private int[] resolveGbufferDrawBuffers(ResolvedProgram resolved) {
        if (resolved == null || resolved.source == null) {
            return DEFAULT_GBUFFER_DRAW_BUFFERS;
        }

        int[] drawBuffers = resolved.source.getDirectives().getDrawBuffers();
        return drawBuffers == null ? new int[0] : drawBuffers;
    }

    private void ensureFramebufferManagerInitialized() {
        if (framebuffersInitialized) {
            return;
        }
        framebufferManager.initialize();
        customTextureManager.initialize();
        customTextureManager.applyGlobalOverrides();
        customImageManager.initializeOrResize(framebufferManager.getWidth(), framebufferManager.getHeight());
        rebuildRenderTargets(framebufferManager.getWidth(), framebufferManager.getHeight());
        framebuffersInitialized = true;
    }

    private void prepareSodiumTerrainResources() {
        ensureFramebufferManagerInitialized();
        ensureFramebufferDimensionsUpToDate();
        customTextureManager.initialize();
        customTextureManager.applyGlobalOverrides();
        customImageManager.initializeOrResize(framebufferManager.getWidth(), framebufferManager.getHeight());
        ensureShadowRendererInitialized();
    }

    private void ensureFramebufferDimensionsUpToDate() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        int displayWidth = Math.max(1, minecraft.displayWidth);
        int displayHeight = Math.max(1, minecraft.displayHeight);
        framebufferManager.resizeIfNeeded(displayWidth, displayHeight);

        if (renderTargets == null) {
            customImageManager.initializeOrResize(displayWidth, displayHeight);
            rebuildRenderTargets(displayWidth, displayHeight);
            return;
        }

        boolean changed = renderTargets.resizeIfNeeded(
            framebufferManager.getDepthTexture(),
            framebufferManager.getDepthTextureVersion(),
            displayWidth,
            displayHeight,
            getDepthBufferFormat(framebufferManager.getDepthTexture())
        );

        if (changed) {
            renderTargetDependentsDirty = true;
        }

        if (renderTargetDependentsDirty) {
            customImageManager.initializeOrResize(displayWidth, displayHeight);
            recalculateRenderTargetDependentSizes();
            renderTargetDependentsDirty = false;
        }
    }

    private void recalculateRenderTargetDependentSizes() {
        if (prepareRenderer != null) {
            prepareRenderer.recalculateSizes();
        }
        if (deferredRenderer != null) {
            deferredRenderer.recalculateSizes();
        }
        if (compositeRenderer != null) {
            compositeRenderer.recalculateSizes();
        }
        if (finalPassRenderer != null) {
            finalPassRenderer.recalculateSwapPassSize();
        }

        List<ClearPass> replacementClearPassesFull = Collections.emptyList();
        List<ClearPass> replacementClearPasses = Collections.emptyList();
        boolean complete = false;
        try {
            replacementClearPassesFull = ClearPassCreator.createClearPasses(renderTargets, true, renderTargetDirectives);
            replacementClearPasses = ClearPassCreator.createClearPasses(renderTargets, false, renderTargetDirectives);
            complete = true;
        } catch (RuntimeException | Error exception) {
            if (!complete) {
                Throwable failure = null;
                failure = destroyClearPassFramebuffers(failure, replacementClearPassesFull);
                failure = destroyClearPassFramebuffers(failure, replacementClearPasses);
                addSuppressedCleanupFailure(exception, failure);
            }
            throw exception;
        }

        List<ClearPass> previousClearPassesFull = clearPassesFull;
        List<ClearPass> previousClearPasses = clearPasses;
        clearPassesFull = replacementClearPassesFull;
        clearPasses = replacementClearPasses;
        Throwable failure = null;
        failure = destroyClearPassFramebuffers(failure, previousClearPassesFull);
        failure = destroyClearPassFramebuffers(failure, previousClearPasses);
        rethrowCleanupFailure(failure);
    }

    private void destroyClearPassFramebuffers(List<ClearPass> clearPasses) {
        rethrowCleanupFailure(destroyClearPassFramebuffers(null, clearPasses));
    }

    private Throwable destroyClearPassFramebuffers(Throwable failure, List<ClearPass> clearPasses) {
        if (clearPasses == null || renderTargets == null) {
            return failure;
        }

        for (ClearPass clearPass : clearPasses) {
            if (clearPass != null) {
                failure = runCleanup(failure, () -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));
            }
        }
        return failure;
    }

    private Framebuffer getOrCreateGbufferFramebuffer(boolean beforeTranslucent, int[] drawBuffers) {
        if (renderTargets == null) {
            rebuildRenderTargets(framebufferManager.getWidth(), framebufferManager.getHeight());
        }

        int[] framebufferDrawBuffers = drawBuffers == null ? new int[0] : drawBuffers.clone();
        String key = Arrays.toString(framebufferDrawBuffers);
        Map<String, Framebuffer> cache = beforeTranslucent
            ? gbufferFramebuffersBeforeTranslucent
            : gbufferFramebuffersAfterTranslucent;

        Framebuffer framebuffer = cache.get(key);
        if (framebuffer != null) {
            return framebuffer;
        }

        Set<Integer> flippedBuffers = beforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent;
        GlFramebuffer pendingHandle = null;
        Framebuffer pendingFramebuffer = null;
        try {
            pendingHandle = renderTargets.createGbufferFramebuffer(flippedBuffers, framebufferDrawBuffers);
            pendingFramebuffer = new Framebuffer(pendingHandle, framebufferDrawBuffers);
            pendingHandle = null;
            cache.put(key, pendingFramebuffer);
            framebuffer = pendingFramebuffer;
            pendingFramebuffer = null;
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            Throwable failure = null;
            if (pendingFramebuffer != null) {
                final Framebuffer capturedFramebuffer = pendingFramebuffer;
                failure = destroyGbufferFramebuffer(failure, capturedFramebuffer);
                failure = runCleanup(failure, () -> cache.remove(key, capturedFramebuffer));
            } else if (pendingHandle != null) {
                final GlFramebuffer capturedHandle = pendingHandle;
                failure = runCleanup(failure, () -> renderTargets.destroyFramebuffer(capturedHandle));
            }
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }
    }

    private void rebuildRenderTargets(int width, int height) {
        boolean hadShadowMapBefore = shadowMap != null;
        net.oculus.postprocess.BufferFlipper replacementBufferFlipper = new net.oculus.postprocess.BufferFlipper();
        net.oculus.postprocess.CenterDepthSampler replacementCenterDepthSampler = null;
        net.oculus.rendertarget.RenderTargets replacementRenderTargets = null;
        net.oculus.postprocess.CompositeRenderer replacementPrepareRenderer = null;
        net.oculus.postprocess.CompositeRenderer replacementDeferredRenderer = null;
        net.oculus.postprocess.CompositeRenderer replacementCompositeRenderer = null;
        net.oculus.postprocess.FinalPassRenderer replacementFinalPassRenderer = null;
        ColorSpaceConverter replacementColorSpaceConverter = null;
        List<ClearPass> replacementClearPassesFull = Collections.emptyList();
        List<ClearPass> replacementClearPasses = Collections.emptyList();
        com.google.common.collect.ImmutableSet<Integer> replacementFlippedAfterPrepare =
            com.google.common.collect.ImmutableSet.of();
        com.google.common.collect.ImmutableSet<Integer> replacementFlippedAfterTranslucent =
            com.google.common.collect.ImmutableSet.of();
        ColorSpace replacementColorSpace = getConfiguredColorSpace();
        Map<String, TextureBinding> replacementRenderTargetBindings = Collections.emptyMap();
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        boolean complete = false;

        try {
            replacementRenderTargets = new net.oculus.rendertarget.RenderTargets(
                safeWidth,
                safeHeight,
                framebufferManager.getDepthTexture(),
                framebufferManager.getDepthTextureVersion(),
                getDepthBufferFormat(framebufferManager.getDepthTexture()),
                renderTargetDirectives.getRenderTargetSettings(),
                directives
            );
            final net.oculus.rendertarget.RenderTargets stagedRenderTargets = replacementRenderTargets;
            replacementRenderTargetBindings = createRenderTargetBindings(stagedRenderTargets);
            replacementCenterDepthSampler = new net.oculus.postprocess.CenterDepthSampler(
                stagedRenderTargets::getCurrentDepthTexture,
                directives.getCenterDepthHalfLife());
            replacementClearPassesFull = ClearPassCreator.createClearPasses(
                replacementRenderTargets, true, renderTargetDirectives);
            replacementClearPasses = ClearPassCreator.createClearPasses(
                replacementRenderTargets, false, renderTargetDirectives);

            List<StringPair> environmentDefines = ShaderPreprocessor.createEnvironmentDefines(programSet);
            replacementPrepareRenderer = new net.oculus.postprocess.CompositeRenderer(
                directives,
                programSet.getPrepare(),
                programSet.getPrepareCompute(),
                replacementRenderTargets,
                this::getOrCreateShadowMap,
                customTextureManager::getNoiseTextureId,
                frameUpdateNotifier,
                replacementCenterDepthSampler,
                replacementBufferFlipper,
                customTextureManager,
                customImageManager,
                pack.getName(),
                environmentDefines,
                directives.getExplicitFlips("prepare_pre"),
                customUniforms
            );
            replacementFlippedAfterPrepare = replacementBufferFlipper.snapshot();
            replacementDeferredRenderer = new net.oculus.postprocess.CompositeRenderer(
                directives,
                programSet.getDeferred(),
                programSet.getDeferredCompute(),
                replacementRenderTargets,
                this::getOrCreateShadowMap,
                customTextureManager::getNoiseTextureId,
                frameUpdateNotifier,
                replacementCenterDepthSampler,
                replacementBufferFlipper,
                customTextureManager,
                customImageManager,
                pack.getName(),
                environmentDefines,
                directives.getExplicitFlips("deferred_pre"),
                customUniforms
            );
            replacementFlippedAfterTranslucent = replacementBufferFlipper.snapshot();
            replacementCompositeRenderer = new net.oculus.postprocess.CompositeRenderer(
                directives,
                programSet.getComposite(),
                programSet.getCompositeCompute(),
                replacementRenderTargets,
                this::getOrCreateShadowMap,
                customTextureManager::getNoiseTextureId,
                frameUpdateNotifier,
                replacementCenterDepthSampler,
                replacementBufferFlipper,
                customTextureManager,
                customImageManager,
                pack.getName(),
                environmentDefines,
                directives.getExplicitFlips("composite_pre"),
                customUniforms
            );
            replacementFinalPassRenderer = new net.oculus.postprocess.FinalPassRenderer(
                directives,
                programSet.getCompositeFinal().orElse(null),
                programSet.getFinalCompute(),
                replacementRenderTargets,
                this::getOrCreateShadowMap,
                customTextureManager::getNoiseTextureId,
                frameUpdateNotifier,
                replacementCenterDepthSampler,
                replacementBufferFlipper,
                replacementBufferFlipper.snapshot(),
                replacementCompositeRenderer.getFlippedAtLeastOnceFinal(),
                customTextureManager,
                customImageManager,
                pack.getName(),
                environmentDefines,
                customUniforms
            );
            replacementColorSpaceConverter = createColorSpaceConverter(safeWidth, safeHeight, replacementColorSpace);
            ensureForcedShadowMapInitialized();
            complete = true;
        } catch (RuntimeException | Error exception) {
            if (!complete) {
                Throwable failure = destroyStagedRenderTargetRebuild(
                    null,
                    replacementPrepareRenderer,
                    replacementDeferredRenderer,
                    replacementCompositeRenderer,
                    replacementFinalPassRenderer,
                    replacementColorSpaceConverter,
                    replacementCenterDepthSampler,
                    replacementRenderTargets);
                if (!hadShadowMapBefore && shadowMap != null) {
                    failure = destroyDisabledShadowClearPassFramebuffers(failure);
                    failure = runCleanup(failure, shadowMap::destroy);
                    shadowMap = null;
                    disabledShadowClearPassesFull = Collections.emptyList();
                    shadowRendererInitialized = false;
                }
                addSuppressedCleanupFailure(exception, failure);
            }
            throw exception;
        }

        Map<String, Framebuffer> previousGbufferFramebuffersBeforeTranslucent =
            new HashMap<>(gbufferFramebuffersBeforeTranslucent);
        Map<String, Framebuffer> previousGbufferFramebuffersAfterTranslucent =
            new HashMap<>(gbufferFramebuffersAfterTranslucent);
        net.oculus.postprocess.CenterDepthSampler previousCenterDepthSampler = centerDepthSampler;
        net.oculus.rendertarget.RenderTargets previousRenderTargets = renderTargets;
        net.oculus.postprocess.CompositeRenderer previousPrepareRenderer = prepareRenderer;
        net.oculus.postprocess.CompositeRenderer previousDeferredRenderer = deferredRenderer;
        net.oculus.postprocess.CompositeRenderer previousCompositeRenderer = compositeRenderer;
        net.oculus.postprocess.FinalPassRenderer previousFinalPassRenderer = finalPassRenderer;
        ColorSpaceConverter previousColorSpaceConverter = colorSpaceConverter;

        Throwable bindingCleanupFailure;
        try {
            bindingCleanupFailure = installRenderTargetBindings(replacementRenderTargetBindings);
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyStagedRenderTargetRebuild(
                null,
                replacementPrepareRenderer,
                replacementDeferredRenderer,
                replacementCompositeRenderer,
                replacementFinalPassRenderer,
                replacementColorSpaceConverter,
                replacementCenterDepthSampler,
                replacementRenderTargets);
            if (!hadShadowMapBefore && shadowMap != null) {
                failure = destroyDisabledShadowClearPassFramebuffers(failure);
                failure = runCleanup(failure, shadowMap::destroy);
                shadowMap = null;
                disabledShadowClearPassesFull = Collections.emptyList();
                shadowRendererInitialized = false;
            }
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        gbufferFramebuffersBeforeTranslucent.clear();
        gbufferFramebuffersAfterTranslucent.clear();

        bufferFlipper = replacementBufferFlipper;
        centerDepthSampler = replacementCenterDepthSampler;
        renderTargets = replacementRenderTargets;
        clearPassesFull = replacementClearPassesFull;
        clearPasses = replacementClearPasses;
        prepareRenderer = replacementPrepareRenderer;
        deferredRenderer = replacementDeferredRenderer;
        compositeRenderer = replacementCompositeRenderer;
        finalPassRenderer = replacementFinalPassRenderer;
        flippedAfterPrepare = replacementFlippedAfterPrepare;
        flippedAfterTranslucent = replacementFlippedAfterTranslucent;
        colorSpaceConverter = replacementColorSpaceConverter;
        currentColorSpace = replacementColorSpace;
        colorSpaceWidth = safeWidth;
        colorSpaceHeight = safeHeight;
        renderTargetDependentsDirty = false;

        Throwable failure = bindingCleanupFailure;
        failure = destroyActiveShadowRendererForRenderTargetRebuild(failure);
        failure = destroyPreviousRenderTargetRebuild(
            failure,
            previousGbufferFramebuffersBeforeTranslucent,
            previousGbufferFramebuffersAfterTranslucent,
            previousPrepareRenderer,
            previousDeferredRenderer,
            previousCompositeRenderer,
            previousFinalPassRenderer,
            previousColorSpaceConverter,
            previousCenterDepthSampler,
            previousRenderTargets);
        rethrowCleanupFailure(failure);
    }

    private Throwable destroyActiveShadowRendererForRenderTargetRebuild(Throwable failure) {
        if (shadowRenderer != null) {
            failure = runCleanup(failure, shadowRenderer::destroy);
        }
        shadowRenderer = null;
        shadowRendererInitialized = false;
        shadowRenderTargetsPreparedThisFrame = false;
        return failure;
    }

    private static Throwable destroyPreviousRenderTargetRebuild(
            Throwable failure,
            Map<String, Framebuffer> previousGbufferFramebuffersBeforeTranslucent,
            Map<String, Framebuffer> previousGbufferFramebuffersAfterTranslucent,
            net.oculus.postprocess.CompositeRenderer previousPrepareRenderer,
            net.oculus.postprocess.CompositeRenderer previousDeferredRenderer,
            net.oculus.postprocess.CompositeRenderer previousCompositeRenderer,
            net.oculus.postprocess.FinalPassRenderer previousFinalPassRenderer,
            ColorSpaceConverter previousColorSpaceConverter,
            net.oculus.postprocess.CenterDepthSampler previousCenterDepthSampler,
            net.oculus.rendertarget.RenderTargets previousRenderTargets) {
        if (previousGbufferFramebuffersBeforeTranslucent != null) {
            for (Framebuffer framebuffer : previousGbufferFramebuffersBeforeTranslucent.values()) {
                failure = destroyGbufferFramebuffer(failure, previousRenderTargets, framebuffer);
            }
        }
        if (previousGbufferFramebuffersAfterTranslucent != null) {
            for (Framebuffer framebuffer : previousGbufferFramebuffersAfterTranslucent.values()) {
                failure = destroyGbufferFramebuffer(failure, previousRenderTargets, framebuffer);
            }
        }
        if (previousPrepareRenderer != null) {
            failure = runCleanup(failure, previousPrepareRenderer::destroy);
        }
        if (previousDeferredRenderer != null) {
            failure = runCleanup(failure, previousDeferredRenderer::destroy);
        }
        if (previousCompositeRenderer != null) {
            failure = runCleanup(failure, previousCompositeRenderer::destroy);
        }
        if (previousFinalPassRenderer != null) {
            failure = runCleanup(failure, previousFinalPassRenderer::destroy);
        }
        if (previousColorSpaceConverter != null) {
            failure = runCleanup(failure, previousColorSpaceConverter::destroy);
        }
        if (previousCenterDepthSampler != null) {
            failure = runCleanup(failure, previousCenterDepthSampler::destroy);
        }
        if (previousRenderTargets != null) {
            failure = runCleanup(failure, previousRenderTargets::destroy);
        }
        return failure;
    }

    private static void destroyStagedRenderTargetRebuild(
            net.oculus.postprocess.CompositeRenderer stagedPrepareRenderer,
            net.oculus.postprocess.CompositeRenderer stagedDeferredRenderer,
            net.oculus.postprocess.CompositeRenderer stagedCompositeRenderer,
            net.oculus.postprocess.FinalPassRenderer stagedFinalPassRenderer,
            ColorSpaceConverter stagedColorSpaceConverter,
            net.oculus.postprocess.CenterDepthSampler stagedCenterDepthSampler,
            net.oculus.rendertarget.RenderTargets stagedRenderTargets) {
        rethrowCleanupFailure(destroyStagedRenderTargetRebuild(
            null,
            stagedPrepareRenderer,
            stagedDeferredRenderer,
            stagedCompositeRenderer,
            stagedFinalPassRenderer,
            stagedColorSpaceConverter,
            stagedCenterDepthSampler,
            stagedRenderTargets));
    }

    private static Throwable destroyStagedRenderTargetRebuild(
            Throwable failure,
            net.oculus.postprocess.CompositeRenderer stagedPrepareRenderer,
            net.oculus.postprocess.CompositeRenderer stagedDeferredRenderer,
            net.oculus.postprocess.CompositeRenderer stagedCompositeRenderer,
            net.oculus.postprocess.FinalPassRenderer stagedFinalPassRenderer,
            ColorSpaceConverter stagedColorSpaceConverter,
            net.oculus.postprocess.CenterDepthSampler stagedCenterDepthSampler,
            net.oculus.rendertarget.RenderTargets stagedRenderTargets) {
        if (stagedPrepareRenderer != null) {
            failure = runCleanup(failure, stagedPrepareRenderer::destroy);
        }
        if (stagedDeferredRenderer != null) {
            failure = runCleanup(failure, stagedDeferredRenderer::destroy);
        }
        if (stagedCompositeRenderer != null) {
            failure = runCleanup(failure, stagedCompositeRenderer::destroy);
        }
        if (stagedFinalPassRenderer != null) {
            failure = runCleanup(failure, stagedFinalPassRenderer::destroy);
        }
        if (stagedColorSpaceConverter != null) {
            failure = runCleanup(failure, stagedColorSpaceConverter::destroy);
        }
        if (stagedCenterDepthSampler != null) {
            failure = runCleanup(failure, stagedCenterDepthSampler::destroy);
        }
        if (stagedRenderTargets != null) {
            failure = runCleanup(failure, stagedRenderTargets::destroy);
        }
        return failure;
    }

    private void rebuildColorSpaceConverter(int width, int height) {
        destroyColorSpaceConverter();
        this.currentColorSpace = getConfiguredColorSpace();

        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        this.colorSpaceWidth = safeWidth;
        this.colorSpaceHeight = safeHeight;
        this.colorSpaceConverter = createColorSpaceConverter(safeWidth, safeHeight, this.currentColorSpace);
    }

    private ColorSpaceConverter createColorSpaceConverter(int safeWidth, int safeHeight, ColorSpace colorSpace) {
        if (directives.supportsColorCorrection()) {
            return NoOpColorSpaceConverter.INSTANCE;
        }

        if (OculusRenderSystem.supportsCompute() && ImageLimits.get().getMaxImageUnits() > 0) {
            return new ColorSpaceComputeConverter(safeWidth, safeHeight, colorSpace);
        }
        return new ColorSpaceFragmentConverter(safeWidth, safeHeight, colorSpace);
    }

    private void ensureColorSpaceConverterUpToDate(int width, int height) {
        ColorSpace configured = getConfiguredColorSpace();
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (colorSpaceConverter == null || configured != currentColorSpace
            || safeWidth != colorSpaceWidth || safeHeight != colorSpaceHeight) {
            rebuildColorSpaceConverter(safeWidth, safeHeight);
        }
    }

    private ColorSpace getConfiguredColorSpace() {
        OculusConfig config = Oculus.getConfig();
        return config != null ? config.getColorSpace() : ColorSpace.SRGB;
    }

    private Map<String, TextureBinding> createRenderTargetBindings(
            net.oculus.rendertarget.RenderTargets targets) {
        Map<String, TextureBinding> bindings = new LinkedHashMap<>();

        for (Integer targetIndex : renderTargetDirectives.getRenderTargetSettings().keySet()) {
            final int index = targetIndex;
            com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget target = targets.get(index);
            if (target == null) {
                throw new IllegalStateException("Render target colortex" + index + " is not configured");
            }

            TextureBinding currentBinding = IrisSamplers.renderTargetBinding(
                targets, this::getCurrentRenderTargetReadBuffers, index, false);
            TextureBinding altBinding = IrisSamplers.renderTargetBinding(
                targets, this::getCurrentRenderTargetReadBuffers, index, true);

            bindings.put("colortex" + index, currentBinding);
            if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
                bindings.put(PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index), currentBinding);
            }
            bindings.put("oculus_rt" + index, currentBinding);
            bindings.put("oculus_flipped_rt" + index, altBinding);
        }

        bindings.put("normals", TextureBinding.texture2D(this::getCurrentNormalTexture));
        bindings.put("specular", TextureBinding.texture2D(this::getCurrentSpecularTexture));

        TextureBinding depthBinding = TextureBinding.texture2D(() -> targets.getCurrentDepthTexture());
        TextureBinding depthNoTranslucentsBinding = TextureBinding.texture2D(() ->
            targets.getDepthTextureNoTranslucents().getTextureId());
        TextureBinding depthNoHandBinding = TextureBinding.texture2D(() ->
            targets.getDepthTextureNoHand().getTextureId());
        bindings.put("gdepthtex", depthBinding);
        bindings.put("depthtex0", depthBinding);
        bindings.put("depthtex1", depthNoTranslucentsBinding);
        bindings.put("depthtex2", depthNoHandBinding);
        bindings.put("oculus_depth", depthBinding);

        return bindings;
    }

    private Throwable installRenderTargetBindings(Map<String, TextureBinding> replacementBindings) {
        Map<String, TextureBinding> previousBindings = new HashMap<>(registeredRenderTargetBindings);
        Map<String, TextureBinding> installedBindings = new HashMap<>();

        try {
            for (Map.Entry<String, TextureBinding> entry : replacementBindings.entrySet()) {
                TextureBindingRegistry.register(entry.getKey(), entry.getValue());
                installedBindings.put(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException | Error exception) {
            Throwable failure = null;
            failure = unregisterTextureBindings(failure, installedBindings);
            failure = registerTextureBindings(failure, previousBindings);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        registeredRenderTargetBindings.clear();
        registeredRenderTargetBindings.putAll(replacementBindings);
        return unregisterTextureBindings(null, previousBindings);
    }

    private void unregisterRenderTargetBindings() {
        try {
            Throwable failure = unregisterTextureBindings(null, registeredRenderTargetBindings);
            rethrowCleanupFailure(failure);
        } finally {
            registeredRenderTargetBindings.clear();
        }
    }

    private static Throwable registerTextureBindings(Throwable failure, Map<String, TextureBinding> bindings) {
        for (Map.Entry<String, TextureBinding> entry : bindings.entrySet()) {
            failure = runCleanup(failure, () -> TextureBindingRegistry.register(entry.getKey(), entry.getValue()));
        }
        return failure;
    }

    private static Throwable unregisterTextureBindings(Throwable failure, Map<String, TextureBinding> bindings) {
        for (Map.Entry<String, TextureBinding> entry : bindings.entrySet()) {
            failure = runCleanup(failure,
                () -> TextureBindingRegistry.unregister(entry.getKey(), entry.getValue()));
        }
        return failure;
    }

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        if (cleanup == null) {
            return failure;
        }
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable addCleanupFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }
        if (exception != failure) {
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure) {
        if (primary != null && cleanupFailure != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
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
        throw new RuntimeException(failure);
    }

    private static DepthBufferFormat getDepthBufferFormat(int depthTexture) {
        if (depthTexture <= 0) {
            return DepthBufferFormat.DEPTH;
        }

        int internalFormat = TextureInfoCache.INSTANCE.getInfo(depthTexture).getInternalFormat();
        return DepthBufferFormat.fromGlEnumOrDefault(internalFormat);
    }

    private Set<Integer> getActiveReadBuffers() {
        return isBeforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent;
    }

    private Set<Integer> getCurrentRenderTargetReadBuffers() {
        return isRenderingShadow ? getShadowReadBuffers() : getActiveReadBuffers();
    }

    private Set<Integer> getShadowReadBuffers() {
        return prepareBeforeShadow ? flippedAfterPrepare : Collections.emptySet();
    }

    private void clearRenderTargets() {
        Throwable failure = null;
        try {
            OculusRenderSystem.restoreDefaultActiveTexture();
            clearDisabledShadowTargets();

            requirePipelineResource(renderTargets, "render targets", "render-target clear");

            List<ClearPass> passes;
            if (renderTargets.isFullClearRequired()) {
                renderTargets.onFullClear();
                passes = clearPassesFull;
            } else {
                passes = clearPasses;
            }

            if (passes.isEmpty()) {
                return;
            }

            float[] fogColor = CapturedRenderingState.INSTANCE.getFogColorVec4();
            Vector4f defaultClearColor = new Vector4f(
                fogColor.length > 0 ? fogColor[0] : 0.0F,
                fogColor.length > 1 ? fogColor[1] : 0.0F,
                fogColor.length > 2 ? fogColor[2] : 0.0F,
                1.0F
            );

            for (ClearPass clearPass : passes) {
                clearPass.execute(defaultClearColor);
            }
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);
            rethrowCleanupFailure(failure);
        }
    }

    private static void unbindCustomFramebuffersForDestroy() {
        OculusRenderSystem.restoreFramebufferBindings(0, 0, 0);
    }

    private static void bindMainFramebufferAfterRenderTargetPreparation() {
        Minecraft minecraft = Minecraft.getMinecraft();
        net.minecraft.client.shader.Framebuffer mainFramebuffer =
            minecraft == null ? null : minecraft.getFramebuffer();
        if (mainFramebuffer != null) {
            mainFramebuffer.bindFramebuffer(true);
        } else {
            OculusRenderSystem.restoreFramebufferBindings(0, 0, 0);
            if (minecraft != null) {
                GL11.glViewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
            }
        }
    }

    private void clearDisabledShadowTargets() {
        if (shadowMap == null || shadowDirectives == null
                || shadowDirectives.isShadowEnabled() != OptionalBoolean.FALSE) {
            return;
        }

        if (!shadowMap.isFullClearRequired()) {
            return;
        }

        ensureDisabledShadowClearPassesInitialized();
        shadowMap.consumeFullClearRequired();
        for (ClearPass clearPass : disabledShadowClearPassesFull) {
            clearPass.execute(SHADOW_CLEAR_DEFAULT);
        }
    }

    private void ensureDisabledShadowClearPassesInitialized() {
        if (!disabledShadowClearPassesFull.isEmpty()) {
            return;
        }

        disabledShadowClearPassesFull = ClearPassCreator.createShadowClearPasses(
            shadowMap, true, shadowDirectives);
    }

    private Throwable destroyDisabledShadowClearPassFramebuffers(Throwable failure) {
        failure = destroyShadowClearPassFramebuffers(failure, shadowMap, disabledShadowClearPassesFull);
        disabledShadowClearPassesFull = Collections.emptyList();
        return failure;
    }

    private static Throwable destroyShadowClearPassFramebuffers(
            Throwable failure,
            net.oculus.pipeline.shadow.ShadowMap owner,
            List<ClearPass> clearPasses) {
        if (owner == null || clearPasses == null) {
            return failure;
        }

        for (ClearPass clearPass : clearPasses) {
            if (clearPass != null) {
                failure = runCleanup(failure, () -> owner.destroyFramebuffer(clearPass.getFramebuffer()));
            }
        }
        return failure;
    }

    private void runPreparePass() {
        if (!prepared) {
            return;
        }

        requirePipelineResource(prepareRenderer, "prepare renderer", "prepare pass");
        Throwable failure = null;
        try {
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.preparePass.entry");
            unbindProgram();
            isRenderingFullScreenPass = true;
            shaderStorageBufferManager.bindAll();
            prepareRenderer.renderAll();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.preparePass.renderAll");
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            failure = restoreAfterPreparePass(failure);
            rethrowCleanupFailure(failure);
        }
        syncProgram();
    }

    private Throwable restoreAfterDeferredPass(Throwable failure) {
        failure = restoreAfterPreparePass(failure);
        failure = runCleanup(failure, GlStateManager::enableBlend);
        failure = runCleanup(failure, GlStateManager::enableAlpha);
        return failure;
    }

    private Throwable restoreAfterPreparePass(Throwable failure) {
        isRenderingFullScreenPass = false;
        failure = runCleanup(failure, () -> bindGbufferFramebuffer(false));
        return failure;
    }

    private void runCompositePass() {
        requirePipelineResource(centerDepthSampler, "center depth sampler", "final postprocess");
        requirePipelineResource(compositeRenderer, "composite renderer", "final postprocess");
        requirePipelineResource(finalPassRenderer, "final pass renderer", "final postprocess");

        dumpValidationRenderTarget("pre-composite-colortex0", 0, getActiveReadBuffers());
        dumpValidationRenderTarget("pre-composite-colortex3", 3, getActiveReadBuffers());
        dumpValidationRenderTarget("pre-composite-colortex6", 6, getActiveReadBuffers());
        dumpValidationDepthTargets("pre-composite");

        centerDepthSampler.sampleCenterDepth();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.composite.centerDepth");
        shaderStorageBufferManager.bindAll();
        compositeRenderer.renderAll();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.composite.renderAll");

        Set<Integer> postCompositeReadBuffers = bufferFlipper == null
            ? Collections.emptySet()
            : bufferFlipper.snapshot();
        dumpValidationRenderTarget("post-composite-colortex0", 0, postCompositeReadBuffers);
        dumpValidationRenderTarget("post-composite-colortex3", 3, postCompositeReadBuffers);
        dumpValidationRenderTarget("post-composite-colortex6", 6, postCompositeReadBuffers);

        shaderStorageBufferManager.bindAll();
        finalPassRenderer.render();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.composite.final");

        runColorSpaceConversion();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("pipeline.composite.colorSpace");
        gbufferBound = false;
    }

    private void dumpValidationRenderTarget(String label, int bufferIndex, Set<Integer> readBuffers) {
        if (!OculusRuntimeValidation.isRenderTargetDumpEnabled() || renderTargets == null) {
            return;
        }

        RenderTarget target = renderTargets.get(bufferIndex);
        if (target == null) {
            return;
        }

        boolean readsAlt = readBuffers != null && readBuffers.contains(bufferIndex);
        int texture = readsAlt ? target.getAltTexture() : target.getMainTexture();
        OculusRuntimeValidation.dumpRenderTargetTexture(
            label + "-" + (readsAlt ? "alt" : "main"),
            texture,
            target.getWidth(),
            target.getHeight()
        );
    }

    private void dumpValidationDepthTargets(String label) {
        if (!OculusRuntimeValidation.isRenderTargetDumpEnabled() || renderTargets == null) {
            return;
        }

        int width = renderTargets.getCurrentWidth();
        int height = renderTargets.getCurrentHeight();
        OculusRuntimeValidation.dumpDepthTexture(
            label + "-depthtex0",
            renderTargets.getCurrentDepthTexture(),
            width,
            height
        );
        OculusRuntimeValidation.dumpDepthTexture(
            label + "-depthtex1-no-translucents",
            renderTargets.getDepthTextureNoTranslucents().getTextureId(),
            width,
            height
        );
        OculusRuntimeValidation.dumpDepthTexture(
            label + "-depthtex2-no-hand",
            renderTargets.getDepthTextureNoHand().getTextureId(),
            width,
            height
        );
    }

    private static <T> T requirePipelineResource(T resource, String resourceName, String operation) {
        if (resource == null) {
            throw new IllegalStateException(operation + " requires initialized " + resourceName);
        }
        return resource;
    }

    private void runColorSpaceConversion() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            throw new IllegalStateException("Color-space conversion requires a Minecraft instance");
        }

        net.minecraft.client.shader.Framebuffer mainFramebuffer = minecraft.getFramebuffer();
        if (mainFramebuffer == null) {
            throw new IllegalStateException("Color-space conversion requires a Minecraft main framebuffer");
        }

        int colorTexture = mainFramebuffer.framebufferTexture;
        if (colorTexture <= 0) {
            throw new IllegalStateException("Color-space conversion requires a valid Minecraft main color texture");
        }

        int width = Math.max(1, mainFramebuffer.framebufferWidth);
        int height = Math.max(1, mainFramebuffer.framebufferHeight);
        ensureColorSpaceConverterUpToDate(width, height);

        Throwable failure = null;
        try {
            requirePipelineResource(colorSpaceConverter, "color-space converter", "color-space conversion")
                .process(colorTexture);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = runCleanup(null, () -> mainFramebuffer.bindFramebuffer(true));
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private static final class ResolvedProgram {
        private final ProgramId id;
        private final ProgramSource source;
        private final Program program;

        private ResolvedProgram(ProgramId id, ProgramSource source, Program program) {
            this.id = id;
            this.source = source;
            this.program = program;
        }
    }
}
