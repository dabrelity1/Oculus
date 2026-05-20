package net.oculus.pipeline.shadow;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL42;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.oculus.Oculus;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.config.OculusConfig;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.ComputeProgram;
import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.TextureBinding;
import net.oculus.layer.GbufferPrograms;
import net.oculus.pipeline.ClearPass;
import net.oculus.pipeline.ClearPassCreator;
import net.oculus.pipeline.InputAvailability;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.pipeline.texture.CustomImageManager;
import net.oculus.pipeline.texture.CustomTextureManager;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.samplers.IrisImages;
import net.oculus.samplers.IrisSamplers;
import net.oculus.shaderpack.ComputeSource;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackShadowDirectives;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.shaderpack.ShadowCullingMode;
import net.oculus.shaderpack.StringPair;
import net.oculus.shader.ShaderSourcePreparer;
import net.oculus.gl.shader.ShaderType;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.CelestialUniforms;
import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.ShadowUniforms;
import net.oculus.uniforms.custom.CustomUniformExpressionManager;
import net.oculus.vendored.joml.Vector4f;

/**
 * Handles shadow map rendering for shader packs.
 *
 * Shadow rendering works by:
 * 1. Setting up an orthographic projection from the sun/moon position
 * 2. Rendering the world to a depth texture
 * 3. Using that depth texture in gbuffer/composite shaders for shadow sampling
 *
 * Port from Iris 1.16.5 ShadowRenderer.
 */
public class ShadowRenderer {
    private static final Logger LOGGER = LogManager.getLogger(ShadowRenderer.class);
    private static final int DEFAULT_USER_SHADOW_DISTANCE_CHUNKS = 32;
    private static final int COMPUTE_BARRIER = GL42.GL_TEXTURE_FETCH_BARRIER_BIT
        | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
    private static final Vector4f SHADOW_CLEAR_DEFAULT = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

    private final PackDirectives directives;
    private final ShadowMap shadowMap;
    private final PackShadowDirectives shadowDirectives;
    private final Supplier<RenderTargets> renderTargetsSupplier;
    private final Supplier<? extends Set<Integer>> shadowComputeFlippedBuffersSupplier;
    private final Supplier<? extends Set<Integer>> shadowCompositeFlippedBuffersSupplier;
    private final WorldRenderingPipeline levelSamplerPipeline;
    private final IntSupplier noiseTexture;
    private final ComputeProgram[] shadowComputes;
    private final ComputeProgram[][] shadowCompositeComputes;
    private final List<ClearPass> shadowClearPasses;
    private final List<ClearPass> shadowClearPassesFull;
    private final CustomTextureManager customTextureManager;
    private final CustomImageManager customImageManager;
    private final CustomUniformExpressionManager customUniforms;
    private final FrameUpdateNotifier frameUpdateNotifier;
    private final String packName;
    private final List<StringPair> environmentDefines;
    private final GlFramebuffer shadowFramebuffer;
    private final int resolution;
    private final float halfPlaneLength;
    private final float shadowDistanceRenderMultiplier;
    private final boolean shadowDistanceRenderMultiplierExplicit;
    private final float entityShadowDistanceMultiplier;
    private final float voxelDistance;
    private final ShadowCullingMode shadowCullingMode;
    private final boolean shouldRenderTerrain;
    private final boolean shouldRenderTranslucent;
    private final boolean shouldRenderEntities;
    private final boolean shouldRenderPlayer;
    private final boolean shouldRenderBlockEntities;
    private final boolean hasShadowProgram;

    private final float[] shadowProjection = new float[16];
    private final float[] shadowModelView = new float[16];
    private final float[] shadowModelViewInverse = new float[16];
    private final float[] shadowProjectionInverse = new float[16];

    private boolean destroyed = false;
    private int shadowFrameCounter = 1;
    private int shadowDepthValidationAttempts = 0;
    private boolean shadowDepthValidationComplete = false;
    private boolean packHasVoxelization;

    /**
     * Creates a new ShadowRenderer.
     *
     * @param directives The pack directives
     * @param shadowDirectives The shadow-specific directives
     * @param shadowMap The shadow map texture holder
     */
    public ShadowRenderer(PackDirectives directives,
                         PackShadowDirectives shadowDirectives,
                         ProgramSource shadowSource,
                         ComputeSource[] shadowComputeSources,
                         ComputeSource[][] shadowCompositeComputeSources,
                         ShadowMap shadowMap,
                         Supplier<RenderTargets> renderTargetsSupplier,
                         Supplier<? extends Set<Integer>> shadowComputeFlippedBuffersSupplier,
                         Supplier<? extends Set<Integer>> shadowCompositeFlippedBuffersSupplier,
                         WorldRenderingPipeline levelSamplerPipeline,
                         IntSupplier noiseTexture,
                         CustomTextureManager customTextureManager,
                         CustomImageManager customImageManager,
                         String packName,
                         List<StringPair> environmentDefines,
                         CustomUniformExpressionManager customUniforms,
                         FrameUpdateNotifier frameUpdateNotifier) {
        this.directives = directives;
        this.shadowMap = shadowMap;
        this.renderTargetsSupplier = renderTargetsSupplier;
        this.shadowComputeFlippedBuffersSupplier = shadowComputeFlippedBuffersSupplier;
        this.shadowCompositeFlippedBuffersSupplier = shadowCompositeFlippedBuffersSupplier;
        this.levelSamplerPipeline = levelSamplerPipeline;
        this.noiseTexture = noiseTexture;
        this.customTextureManager = customTextureManager;
        this.customImageManager = customImageManager;
        this.customUniforms = customUniforms == null ? CustomUniformExpressionManager.empty() : customUniforms;
        this.frameUpdateNotifier = frameUpdateNotifier;
        this.shadowDirectives = shadowDirectives;
        this.packName = packName == null ? "" : packName;
        this.environmentDefines = environmentDefines == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(environmentDefines);
        this.resolution = shadowDirectives.getResolution();
        this.halfPlaneLength = shadowDirectives.getDistance();
        this.shadowDistanceRenderMultiplier = shadowDirectives.getDistanceRenderMul();
        this.shadowDistanceRenderMultiplierExplicit = shadowDirectives.isDistanceRenderMulExplicit();
        this.entityShadowDistanceMultiplier = shadowDirectives.getEntityShadowDistanceMul();
        this.voxelDistance = shadowDirectives.getVoxelDistance();
        this.shadowCullingMode = shadowDirectives.getCullingMode();
        this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain();
        this.shouldRenderTranslucent = shadowDirectives.shouldRenderTranslucent();
        this.shouldRenderEntities = shadowDirectives.shouldRenderEntities();
        this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer();
        this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities();
        this.hasShadowProgram = shadowSource != null && shadowSource.isValid();
        this.packHasVoxelization = shadowSource != null && shadowSource.getGeometrySource().isPresent();
        ShadowUniforms.configure(directives);

        ComputeProgram[] compiledShadowComputes = compileShadowComputes(shadowComputeSources);
        ComputeProgram[][] compiledShadowCompositeComputes = new ComputeProgram[0][];

        GlFramebuffer createdShadowFramebuffer = null;
        List<ClearPass> createdShadowClearPasses = Collections.emptyList();
        List<ClearPass> createdShadowClearPassesFull = Collections.emptyList();
        try {
            compiledShadowCompositeComputes = compileShadowCompositeComputes(shadowCompositeComputeSources);
            createdShadowFramebuffer = shadowMap.createShadowFramebuffer(shadowMap.snapshot(), shadowDrawBuffers());
            createdShadowClearPasses = ClearPassCreator.createShadowClearPasses(shadowMap, false, shadowDirectives);
            createdShadowClearPassesFull = ClearPassCreator.createShadowClearPasses(shadowMap, true, shadowDirectives);
        } catch (RuntimeException | Error exception) {
            Throwable failure = null;
            failure = destroyClearPassFramebuffers(failure, createdShadowClearPasses);
            failure = destroyClearPassFramebuffers(failure, createdShadowClearPassesFull);
            failure = destroyShadowFramebuffer(failure, createdShadowFramebuffer);
            failure = destroyComputeProgramGroups(failure, compiledShadowCompositeComputes);
            failure = destroyComputePrograms(failure, compiledShadowComputes);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        this.shadowFramebuffer = createdShadowFramebuffer;
        this.shadowClearPasses = createdShadowClearPasses;
        this.shadowClearPassesFull = createdShadowClearPassesFull;
        this.shadowComputes = compiledShadowComputes;
        this.shadowCompositeComputes = compiledShadowCompositeComputes;

        copyMatrix(ShadowUniforms.getShadowRenderProjection(), shadowProjection);
        copyMatrix(ShadowUniforms.getShadowRenderProjectionInverse(), shadowProjectionInverse);
        copyMatrix(ShadowUniforms.getShadowModelView(), shadowModelView);
        copyMatrix(ShadowUniforms.getShadowModelViewInverse(), shadowModelViewInverse);

        LOGGER.info("ShadowRenderer initialized: resolution={}, distance={}", resolution, halfPlaneLength);
    }

    public void setUsesImages(boolean usesImages) {
        packHasVoxelization = packHasVoxelization || usesImages;
    }

    public void bindFramebufferForShadowPass() {
        failIfDestroyed("bind shadow pass framebuffer");
        if (!shadowMap.isEnabled() || resolution <= 0) {
            return;
        }

        shadowFramebuffer.bind();
        GL11.glViewport(0, 0, resolution, resolution);
    }

    public boolean shouldRenderThisFrame() {
        failIfDestroyed("check shadow render availability");
        return shouldRenderThisFrame(Minecraft.getMinecraft());
    }

    private boolean shouldRenderThisFrame(Minecraft mc) {
        if (!shadowMap.isEnabled() || resolution <= 0) {
            return false;
        }
        if (mc == null || mc.world == null) {
            return false;
        }
        return !shouldSkipShadowRenderingForDistance(
            halfPlaneLength,
            shadowDistanceRenderMultiplier,
            shadowDistanceRenderMultiplierExplicit,
            getConfiguredUserShadowDistanceChunks());
    }

    /**
     * Prepares shadow targets before the root shadow raster program is synced for terrain rendering.
     */
    public void prepareRenderTargets() {
        failIfDestroyed("prepare shadow render targets");
        if (!shadowMap.isEnabled() || resolution <= 0) {
            return;
        }

        int previousActiveTexture = 0;
        int previousTexture = 0;
        int previousFramebuffer = 0;
        int previousReadFramebuffer = 0;
        int previousDrawFramebuffer = 0;
        IntBuffer previousViewport = BufferUtils.createIntBuffer(16);
        boolean previousDepthMask = false;
        boolean activeTextureCaptured = false;
        boolean textureStateCaptured = false;
        boolean framebufferBindingsCaptured = false;
        boolean viewportCaptured = false;
        boolean depthMaskCaptured = false;

        Throwable failure = null;
        try {
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.prepareTargets.entry");
            previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            activeTextureCaptured = true;
            OculusRenderSystem.restoreDefaultActiveTexture();
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureStateCaptured = true;

            previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
            previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
            previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
            framebufferBindingsCaptured = true;
            GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
            viewportCaptured = true;
            previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            depthMaskCaptured = true;

            shadowMap.getDepthSourceFramebuffer().bind();
            GL11.glViewport(0, 0, resolution, resolution);

            GlStateManager.depthMask(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.prepareTargets.depthClear");

            dispatchShadowComputes();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.prepareTargets.compute");

            clearShadowColorBuffers();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.prepareTargets.colorClear");
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            cleanupFailure = runCleanup(cleanupFailure, () -> Program.unbind());
            if (textureStateCaptured) {
                final int capturedPreviousTexture = previousTexture;
                final int capturedPreviousActiveTexture = previousActiveTexture;
                cleanupFailure = runCleanup(cleanupFailure,
                    () -> restoreDefaultTextureBinding(capturedPreviousTexture, capturedPreviousActiveTexture));
            } else if (activeTextureCaptured) {
                final int capturedPreviousActiveTexture = previousActiveTexture;
                cleanupFailure = runCleanup(cleanupFailure,
                    () -> OculusRenderSystem.setActiveTextureUnit(capturedPreviousActiveTexture));
            }
            if (framebufferBindingsCaptured) {
                final int capturedPreviousFramebuffer = previousFramebuffer;
                final int capturedPreviousReadFramebuffer = previousReadFramebuffer;
                final int capturedPreviousDrawFramebuffer = previousDrawFramebuffer;
                cleanupFailure = runCleanup(cleanupFailure,
                    () -> OculusRenderSystem.restoreFramebufferBindings(capturedPreviousFramebuffer,
                        capturedPreviousReadFramebuffer, capturedPreviousDrawFramebuffer));
            }
            if (viewportCaptured) {
                cleanupFailure = runCleanup(cleanupFailure, () -> GL11.glViewport(
                    previousViewport.get(0),
                    previousViewport.get(1),
                    previousViewport.get(2),
                    previousViewport.get(3)));
            }
            if (depthMaskCaptured) {
                final boolean capturedPreviousDepthMask = previousDepthMask;
                cleanupFailure = runCleanup(cleanupFailure, () -> GlStateManager.depthMask(capturedPreviousDepthMask));
            }
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    /**
     * Renders shadow maps for the current frame.
     *
     * @param renderGlobal The world renderer
     * @param cameraEntity The camera entity (player)
     */
    public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks) {
        failIfDestroyed("render shadows");

        Minecraft mc = Minecraft.getMinecraft();
        if (!shouldRenderThisFrame(mc)) {
            return;
        }

        copyMatrix(ShadowUniforms.getShadowRenderProjection(), shadowProjection);
        copyMatrix(ShadowUniforms.getShadowRenderProjectionInverse(), shadowProjectionInverse);
        copyMatrix(ShadowUniforms.getShadowModelView(), shadowModelView);
        copyMatrix(ShadowUniforms.getShadowModelViewInverse(), shadowModelViewInverse);

        double cameraX = cameraEntity.lastTickPosX + (cameraEntity.posX - cameraEntity.lastTickPosX) * partialTicks;
        double cameraY = cameraEntity.lastTickPosY + (cameraEntity.posY - cameraEntity.lastTickPosY) * partialTicks;
        double cameraZ = cameraEntity.lastTickPosZ + (cameraEntity.posZ - cameraEntity.lastTickPosZ) * partialTicks;

        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        IntBuffer previousViewport = BufferUtils.createIntBuffer(16);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        int previousBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int previousBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int previousBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int previousBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean alphaWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lightingWasEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean light0WasEnabled = GL11.glIsEnabled(GL11.GL_LIGHT0);
        boolean light1WasEnabled = GL11.glIsEnabled(GL11.GL_LIGHT1);
        boolean colorMaterialWasEnabled = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        boolean depthMaskWasEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int previousAlphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float previousAlphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = captureDefaultTextureBinding(previousActiveTexture);
        boolean projectionModeSelected = false;
        boolean projectionPushed = false;
        boolean modelViewPushed = false;

        Throwable failure = null;
        try {
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.entry");
            ShadowRenderingState.beginShadowPass(shadowProjection);
            shadowFramebuffer.bind();
            GL11.glViewport(0, 0, resolution, resolution);
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.bindFramebuffer");

            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            GlStateManager.disableCull();
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.shadeModel(GL11.GL_FLAT);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            projectionModeSelected = true;
            GlStateManager.pushMatrix();
            projectionPushed = true;
            loadMatrix(shadowProjection);

            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            modelViewPushed = true;
            loadMatrix(shadowModelView);

            ICamera shadowCamera = createShadowCamera(mc, cameraX, cameraY, cameraZ);
            boolean previousRenderChunksMany = mc.renderChunksMany;
            mc.renderChunksMany = false;
            try {
                // Iris calls LevelRenderer.needsUpdate() before shadow setup; in 1.12 this is the matching hook.
                renderGlobal.setDisplayListEntitiesDirty();
                renderGlobal.setupTerrain(cameraEntity, partialTicks, shadowCamera, shadowFrameCounter++, false);
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.setupTerrain");
            } finally {
                mc.renderChunksMany = previousRenderChunksMany;
            }

            OculusRenderSystem.restoreDefaultActiveTexture();
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            RenderHelper.disableStandardItemLighting();

            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.5F);
            if (shouldRenderTerrain) {
                GlStateManager.disableAlpha();
                renderShadowBlockLayer(renderGlobal, BlockRenderLayer.SOLID, partialTicks, cameraEntity);
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.terrainSolid");
                GlStateManager.enableAlpha();
                renderShadowBlockLayer(renderGlobal, BlockRenderLayer.CUTOUT, partialTicks, cameraEntity);
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.terrainCutout");
                renderShadowBlockLayer(renderGlobal, BlockRenderLayer.CUTOUT_MIPPED, partialTicks, cameraEntity);
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.terrainCutoutMipped");
            }
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

            if (shouldRenderEntities || shouldRenderPlayer || shouldRenderBlockEntities) {
                ICamera entityShadowCamera = createEntityShadowCamera(mc, shadowCamera, cameraX, cameraY, cameraZ);
                Entity shadowPlayer = mc.player != null ? mc.player : cameraEntity;
                boolean renderPlayerDirectly = shouldRenderPlayer && !shouldRenderEntities;
                boolean renderFirstPersonPlayerDirectly =
                    shouldRenderEntities && shouldRenderFirstPersonPlayerDirectly(mc, shadowPlayer);
                boolean entityLightingCleanupRequired = false;
                boolean entityRenderPassCleanupRequired = false;
                boolean entityFilteringCleanupRequired = false;
                Throwable entityFailure = null;
                try {
                    entityLightingCleanupRequired = true;
                    RenderHelper.enableStandardItemLighting();
                    entityRenderPassCleanupRequired = true;
                    ForgeHooksClient.setRenderPass(0);
                    entityFilteringCleanupRequired = true;
                    if (renderPlayerDirectly) {
                        ShadowRenderingState.beginEntityFiltering(false, true, false, shadowPlayer);
                        renderShadowPlayerEntity(mc, entityShadowCamera, shadowPlayer, cameraX, cameraY, cameraZ,
                            partialTicks);
                        if (shouldRenderBlockEntities) {
                            ShadowRenderingState.beginEntityFiltering(false, false, true, shadowPlayer);
                            renderGlobal.renderEntities(cameraEntity, entityShadowCamera, partialTicks);
                        }
                    } else {
                        ShadowRenderingState.beginEntityFiltering(
                            shouldRenderEntities,
                            shouldRenderPlayer,
                            shouldRenderBlockEntities,
                            shadowPlayer);
                        renderGlobal.renderEntities(cameraEntity, entityShadowCamera, partialTicks);
                        if (renderFirstPersonPlayerDirectly) {
                            ShadowRenderingState.beginEntityFiltering(false, true, false, shadowPlayer);
                            renderShadowPlayerEntity(mc, entityShadowCamera, shadowPlayer, cameraX, cameraY, cameraZ,
                                partialTicks);
                        }
                    }
                } catch (RuntimeException | Error exception) {
                    entityFailure = exception;
                    throw exception;
                } finally {
                    OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.entities");
                    Throwable entityCleanupFailure = null;
                    if (entityFilteringCleanupRequired) {
                        entityCleanupFailure = runCleanup(entityCleanupFailure, ShadowRenderingState::endEntityFiltering);
                    }
                    if (entityRenderPassCleanupRequired) {
                        entityCleanupFailure = runCleanup(entityCleanupFailure, () -> ForgeHooksClient.setRenderPass(-1));
                    }
                    if (entityLightingCleanupRequired) {
                        entityCleanupFailure = runCleanup(entityCleanupFailure, RenderHelper::disableStandardItemLighting);
                    }
                    if (entityFailure != null) {
                        addSuppressedCleanupFailure(entityFailure, entityCleanupFailure);
                    } else {
                        rethrowCleanupFailure(entityCleanupFailure);
                    }
                }
            }

            shadowMap.copyDepthToNoTranslucents();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.copyDepthToNoTranslucents");

            if (shouldRenderTranslucent) {
                OculusRenderSystem.restoreDefaultActiveTexture();
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                GlStateManager.enableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO);
                GlStateManager.shadeModel(GL11.GL_SMOOTH);
                GlStateManager.depthMask(false);
                renderShadowBlockLayer(renderGlobal, BlockRenderLayer.TRANSLUCENT, partialTicks, cameraEntity);
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.terrainTranslucent");
                GlStateManager.depthMask(true);
                GlStateManager.disableBlend();
            }

            dispatchShadowCompositeComputes();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.compositeCompute");
            shadowMap.generateMipmaps();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.generateMipmaps");
            validateShadowDepthOutput();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.validateDepth");
            dumpShadowDepthForValidation();
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.dumpDepth");
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = restoreAfterShadowRender(
                null,
                renderGlobal,
                modelViewPushed,
                projectionModeSelected,
                projectionPushed,
                cullWasEnabled,
                blendWasEnabled,
                previousBlendSrcRgb,
                previousBlendDstRgb,
                previousBlendSrcAlpha,
                previousBlendDstAlpha,
                alphaWasEnabled,
                previousAlphaFunc,
                previousAlphaReference,
                previousColorMask,
                previousShadeModel,
                depthWasEnabled,
                previousDepthFunc,
                depthMaskWasEnabled,
                lightingWasEnabled,
                light0WasEnabled,
                light1WasEnabled,
                colorMaterialWasEnabled,
                previousTexture,
                previousActiveTexture,
                previousFramebuffer,
                previousReadFramebuffer,
                previousDrawFramebuffer,
                previousViewport);
            OculusRuntimeValidation.drainGlErrorsAtCheckpoint("shadow.render.restore");
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private static boolean shouldRenderFirstPersonPlayerDirectly(Minecraft mc, Entity shadowPlayer) {
        if (mc == null || mc.gameSettings == null || mc.player == null || shadowPlayer != mc.player) {
            return false;
        }

        return mc.gameSettings.thirdPersonView == 0
            && mc.getRenderViewEntity() == shadowPlayer
            && (!(shadowPlayer instanceof EntityPlayer) || !((EntityPlayer) shadowPlayer).isPlayerSleeping());
    }

    private static void renderShadowPlayerEntity(
            Minecraft mc,
            ICamera entityShadowCamera,
            Entity player,
            double cameraX,
            double cameraY,
            double cameraZ,
            float partialTicks) {
        if (mc == null || mc.world == null || player == null) {
            return;
        }
        if (player instanceof EntityPlayer && ((EntityPlayer) player).isSpectator()) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null
                || !player.shouldRenderInPass(MinecraftForgeClient.getRenderPass())
                || !renderManager.shouldRender(player, entityShadowCamera, cameraX, cameraY, cameraZ)) {
            return;
        }

        Entity renderViewEntity = mc.getRenderViewEntity() == null ? player : mc.getRenderViewEntity();
        renderManager.cacheActiveRenderInfo(
            mc.world,
            mc.fontRenderer,
            renderViewEntity,
            mc.pointedEntity,
            mc.gameSettings,
            partialTicks);
        renderManager.setRenderPosition(
            interpolate(renderViewEntity.lastTickPosX, renderViewEntity.posX, partialTicks),
            interpolate(renderViewEntity.lastTickPosY, renderViewEntity.posY, partialTicks),
            interpolate(renderViewEntity.lastTickPosZ, renderViewEntity.posZ, partialTicks));

        boolean entityPhaseCleanupRequired = false;
        boolean lightmapCleanupRequired = false;
        Throwable failure = null;
        try {
            GbufferPrograms.beginEntities();
            entityPhaseCleanupRequired = true;
            if (mc.entityRenderer != null) {
                lightmapCleanupRequired = true;
                mc.entityRenderer.enableLightmap();
            }
            for (Entity passenger : player.getPassengers()) {
                renderShadowEntityIfRenderable(renderManager, passenger, partialTicks);
            }
            Entity vehicle = player.getRidingEntity();
            if (vehicle != null) {
                renderShadowEntityIfRenderable(renderManager, vehicle, partialTicks);
            }
            renderShadowEntityIfRenderable(renderManager, player, partialTicks);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (lightmapCleanupRequired) {
                cleanupFailure = runCleanup(cleanupFailure, () -> mc.entityRenderer.disableLightmap());
            }
            if (entityPhaseCleanupRequired) {
                cleanupFailure = runCleanup(cleanupFailure, GbufferPrograms::endEntities);
            }
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private static void renderShadowEntityIfRenderable(RenderManager renderManager, Entity entity, float partialTicks) {
        if (entity != null && entity.shouldRenderInPass(MinecraftForgeClient.getRenderPass())) {
            renderManager.renderEntityStatic(entity, partialTicks, false);
        }
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static Throwable restoreAfterShadowRender(
            Throwable failure,
            RenderGlobal renderGlobal,
            boolean modelViewPushed,
            boolean projectionModeSelected,
            boolean projectionPushed,
            boolean cullWasEnabled,
            boolean blendWasEnabled,
            int previousBlendSrcRgb,
            int previousBlendDstRgb,
            int previousBlendSrcAlpha,
            int previousBlendDstAlpha,
            boolean alphaWasEnabled,
            int previousAlphaFunc,
            float previousAlphaReference,
            ByteBuffer previousColorMask,
            int previousShadeModel,
            boolean depthWasEnabled,
            int previousDepthFunc,
            boolean depthMaskWasEnabled,
            boolean lightingWasEnabled,
            boolean light0WasEnabled,
            boolean light1WasEnabled,
            boolean colorMaterialWasEnabled,
            int previousTexture,
            int previousActiveTexture,
            int previousFramebuffer,
            int previousReadFramebuffer,
            int previousDrawFramebuffer,
            IntBuffer previousViewport) {
        failure = restoreShadowTerrainState(failure, renderGlobal, modelViewPushed, projectionModeSelected,
            projectionPushed);
        return restoreShadowDrawState(
            failure,
            cullWasEnabled,
            blendWasEnabled,
            previousBlendSrcRgb,
            previousBlendDstRgb,
            previousBlendSrcAlpha,
            previousBlendDstAlpha,
            alphaWasEnabled,
            previousAlphaFunc,
            previousAlphaReference,
            previousColorMask,
            previousShadeModel,
            depthWasEnabled,
            previousDepthFunc,
            depthMaskWasEnabled,
            lightingWasEnabled,
            light0WasEnabled,
            light1WasEnabled,
            colorMaterialWasEnabled,
            previousTexture,
            previousActiveTexture,
            previousFramebuffer,
            previousReadFramebuffer,
            previousDrawFramebuffer,
            previousViewport);
    }

    private static Throwable restoreShadowTerrainState(
            Throwable failure,
            RenderGlobal renderGlobal,
            boolean modelViewPushed,
            boolean projectionModeSelected,
            boolean projectionPushed) {
        try {
            renderGlobal.setDisplayListEntitiesDirty();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            ForgeHooksClient.setRenderPass(-1);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        if (modelViewPushed) {
            try {
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
            try {
                GlStateManager.popMatrix();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }
        if (projectionPushed) {
            try {
                GlStateManager.matrixMode(GL11.GL_PROJECTION);
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
            try {
                GlStateManager.popMatrix();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
            try {
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        } else if (projectionModeSelected) {
            try {
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }
        return failure;
    }

    private static Throwable restoreShadowDrawState(
            Throwable failure,
            boolean cullWasEnabled,
            boolean blendWasEnabled,
            int previousBlendSrcRgb,
            int previousBlendDstRgb,
            int previousBlendSrcAlpha,
            int previousBlendDstAlpha,
            boolean alphaWasEnabled,
            int previousAlphaFunc,
            float previousAlphaReference,
            ByteBuffer previousColorMask,
            int previousShadeModel,
            boolean depthWasEnabled,
            int previousDepthFunc,
            boolean depthMaskWasEnabled,
            boolean lightingWasEnabled,
            boolean light0WasEnabled,
            boolean light1WasEnabled,
            boolean colorMaterialWasEnabled,
            int previousTexture,
            int previousActiveTexture,
            int previousFramebuffer,
            int previousReadFramebuffer,
            int previousDrawFramebuffer,
            IntBuffer previousViewport) {
        if (cullWasEnabled) {
            try {
                GlStateManager.enableCull();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        } else {
            try {
                GlStateManager.disableCull();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }
        try {
            restoreBlendState(blendWasEnabled, previousBlendSrcRgb, previousBlendDstRgb,
                previousBlendSrcAlpha, previousBlendDstAlpha);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            restoreAlphaState(alphaWasEnabled, previousAlphaFunc, previousAlphaReference);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            restoreColorMask(previousColorMask);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            GlStateManager.shadeModel(previousShadeModel);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            restoreDepthState(depthWasEnabled, previousDepthFunc, depthMaskWasEnabled);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            restoreLightingState(lightingWasEnabled, light0WasEnabled, light1WasEnabled, colorMaterialWasEnabled);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            restoreShadowBindings(
                previousTexture,
                previousActiveTexture,
                previousFramebuffer,
                previousReadFramebuffer,
                previousDrawFramebuffer,
                previousViewport);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static void restoreDepthState(
            boolean depthWasEnabled,
            int previousDepthFunc,
            boolean depthMaskWasEnabled) {
        Throwable failure = null;
        if (depthWasEnabled) {
            try {
                GlStateManager.enableDepth();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        } else {
            try {
                GlStateManager.disableDepth();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }
        try {
            GlStateManager.depthFunc(previousDepthFunc);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            GlStateManager.depthMask(depthMaskWasEnabled);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        rethrowCleanupFailure(failure);
    }

    private static void restoreShadowBindings(
            int previousTexture,
            int previousActiveTexture,
            int previousFramebuffer,
            int previousReadFramebuffer,
            int previousDrawFramebuffer,
            IntBuffer previousViewport) {
        Throwable failure = null;
        try {
            restoreDefaultTextureBinding(previousTexture, previousActiveTexture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            GL11.glViewport(
                previousViewport.get(0),
                previousViewport.get(1),
                previousViewport.get(2),
                previousViewport.get(3));
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            ShadowRenderingState.endShadowPass();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        rethrowCleanupFailure(failure);
    }

    private static void restoreBlendState(
            boolean blendWasEnabled,
            int previousBlendSrcRgb,
            int previousBlendDstRgb,
            int previousBlendSrcAlpha,
            int previousBlendDstAlpha) {
        Throwable failure = null;
        if (blendWasEnabled) {
            try {
                GlStateManager.enableBlend();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        } else {
            try {
                GlStateManager.disableBlend();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }
        try {
            GlStateManager.tryBlendFuncSeparate(
                previousBlendSrcRgb,
                previousBlendDstRgb,
                previousBlendSrcAlpha,
                previousBlendDstAlpha);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        rethrowCleanupFailure(failure);
    }

    private static void restoreColorMask(ByteBuffer previousColorMask) {
        GlStateManager.colorMask(
            previousColorMask.get(0) != 0,
            previousColorMask.get(1) != 0,
            previousColorMask.get(2) != 0,
            previousColorMask.get(3) != 0);
    }

    private static void restoreAlphaState(boolean alphaWasEnabled, int previousAlphaFunc, float previousAlphaReference) {
        Throwable failure = null;
        if (alphaWasEnabled) {
            try {
                GlStateManager.enableAlpha();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        } else {
            try {
                GlStateManager.disableAlpha();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }
        try {
            GlStateManager.alphaFunc(previousAlphaFunc, previousAlphaReference);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        rethrowCleanupFailure(failure);
    }

    private static void restoreDefaultTextureBinding(int previousTexture, int previousActiveTexture) {
        Throwable failure = null;
        try {
            OculusRenderSystem.restoreDefaultActiveTexture();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            GlStateManager.bindTexture(previousTexture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        rethrowCleanupFailure(failure);
    }

    private static void restoreLightingState(
            boolean lightingWasEnabled,
            boolean light0WasEnabled,
            boolean light1WasEnabled,
            boolean colorMaterialWasEnabled) {
        Throwable failure = null;
        try {
            restoreLight(0, light0WasEnabled);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        try {
            restoreLight(1, light1WasEnabled);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        if (colorMaterialWasEnabled) {
            try {
                GlStateManager.enableColorMaterial();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        } else {
            try {
                GlStateManager.disableColorMaterial();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }
        if (lightingWasEnabled) {
            try {
                GlStateManager.enableLighting();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        } else {
            try {
                GlStateManager.disableLighting();
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }
        rethrowCleanupFailure(failure);
    }

    private static int captureDefaultTextureBinding(int previousActiveTexture) {
        Throwable failure = null;
        try {
            OculusRenderSystem.restoreDefaultActiveTexture();
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = runCleanup(null,
                () -> OculusRenderSystem.setActiveTextureUnit(previousActiveTexture));
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private static void restoreLight(int light, boolean enabled) {
        if (enabled) {
            GlStateManager.enableLight(light);
        } else {
            GlStateManager.disableLight(light);
        }
    }

    private ComputeProgram[] compileShadowComputes(ComputeSource[] sources) {
        if (sources == null || sources.length == 0) {
            return new ComputeProgram[0];
        }

        ComputeProgram[] programs = new ComputeProgram[sources.length];
        try {
            for (int i = 0; i < sources.length; i++) {
                ComputeSource source = sources[i];
                if (source == null || !source.isValid() || !source.getSource().isPresent()) {
                    continue;
                }

                try {
                    String computeSource = applyStandardDefines(source.getSource().get(), ShaderType.COMPUTE, source.getName());
                    ProgramBuilder builder = ProgramBuilder.beginCompute(source.getName(), computeSource, null,
                        customUniforms, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS, frameUpdateNotifier, directives);
                    applyCustomBindings(source.getName(), builder, this::currentShadowComputeFlippedBuffers);
                    ComputeProgram program = builder.buildCompute();
                    programs[i] = program;
                    program.setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());
                } catch (RuntimeException ex) {
                    throw new ProgramLoadException("Failed to compile shadow compute shader " + source.getName(), ex);
                }
            }
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyComputePrograms(null, programs));
            throw exception;
        }
        return programs;
    }

    private void dispatchShadowComputes() {
        if (shadowComputes == null || shadowComputes.length == 0) {
            return;
        }

        for (ComputeProgram compute : shadowComputes) {
            if (compute == null) {
                continue;
            }
            compute.dispatch(resolution, resolution);
        }
    }

    private ComputeProgram[][] compileShadowCompositeComputes(ComputeSource[][] sourceGroups) {
        if (sourceGroups == null || sourceGroups.length == 0) {
            return new ComputeProgram[0][];
        }

        ComputeProgram[][] programs = new ComputeProgram[sourceGroups.length][];
        try {
            for (int i = 0; i < sourceGroups.length; i++) {
                programs[i] = compileShadowCompositeComputeGroup(sourceGroups[i]);
            }
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyComputeProgramGroups(null, programs));
            throw exception;
        }
        return programs;
    }

    private ComputeProgram[] compileShadowCompositeComputeGroup(ComputeSource[] sources) {
        if (sources == null || sources.length == 0) {
            return new ComputeProgram[0];
        }

        ComputeProgram[] programs = new ComputeProgram[sources.length];
        try {
            for (int i = 0; i < sources.length; i++) {
                ComputeSource source = sources[i];
                if (source == null || !source.isValid() || !source.getSource().isPresent()) {
                    continue;
                }

                try {
                    String computeSource = applyStandardDefines(source.getSource().get(), ShaderType.COMPUTE, source.getName());
                    ProgramBuilder builder = ProgramBuilder.beginCompute(source.getName(), computeSource, null,
                        customUniforms, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS, frameUpdateNotifier, directives);
                    applyCustomBindings(source.getName(), builder, this::currentShadowCompositeFlippedBuffers);
                    ComputeProgram program = builder.buildCompute();
                    programs[i] = program;
                    program.setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());
                } catch (RuntimeException ex) {
                    throw new ProgramLoadException("Failed to compile shadow composite compute shader "
                        + source.getName(), ex);
                }
            }
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyComputePrograms(null, programs));
            throw exception;
        }
        return programs;
    }

    private void dispatchShadowCompositeComputes() {
        if (shadowCompositeComputes == null || shadowCompositeComputes.length == 0) {
            return;
        }

        for (ComputeProgram[] computes : shadowCompositeComputes) {
            boolean ranCompute = dispatchComputeGroup(computes);
            if (ranCompute) {
                OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);
                Program.unbind();
            }
        }
    }

    private boolean dispatchComputeGroup(ComputeProgram[] computes) {
        if (computes == null || computes.length == 0) {
            return false;
        }

        boolean ranCompute = false;
        for (ComputeProgram compute : computes) {
            if (compute == null) {
                continue;
            }
            compute.dispatch(resolution, resolution);
            ranCompute = true;
        }
        return ranCompute;
    }

    private void clearShadowColorBuffers() {
        List<ClearPass> passes = shadowMap.consumeFullClearRequired() ? shadowClearPassesFull : shadowClearPasses;
        for (ClearPass clearPass : passes) {
            clearPass.execute(SHADOW_CLEAR_DEFAULT);
        }
    }

    private int[] shadowDrawBuffers() {
        return hasShadowProgram ? new int[] { 0, 1 } : new int[] { 0 };
    }

    private ICamera createShadowCamera(Minecraft mc, double cameraX, double cameraY, double cameraZ) {
        return createShadowCamera(mc, cameraX, cameraY, cameraZ, shadowDistanceRenderMultiplier);
    }

    private ICamera createEntityShadowCamera(
            Minecraft mc,
            ICamera terrainShadowCamera,
            double cameraX,
            double cameraY,
            double cameraZ) {
        if (usesTerrainCameraForEntityShadows(entityShadowDistanceMultiplier)) {
            return terrainShadowCamera;
        }

        return createShadowCamera(
            mc,
            cameraX,
            cameraY,
            cameraZ,
            entityShadowRenderMultiplier(shadowDistanceRenderMultiplier, entityShadowDistanceMultiplier));
    }

    private ICamera createShadowCamera(
            Minecraft mc,
            double cameraX,
            double cameraY,
            double cameraZ,
            float renderMultiplier) {
        ICamera camera;
        switch (shadowCullingMode) {
            case DISABLED:
                camera = createDistanceOnlyCamera(mc, renderMultiplier);
                break;
            case REVERSED:
                camera = createReversedCullingCamera(mc, renderMultiplier);
                break;
            case ENABLED:
            case DEFAULT:
            default:
                camera = shouldUseDistanceOnlyCullingForVoxelization()
                    ? createDistanceOnlyCamera(mc, renderMultiplier)
                    : createStandardCullingCamera(mc, 0.0D, renderMultiplier);
                break;
        }

        camera.setPosition(cameraX, cameraY, cameraZ);
        return camera;
    }

    private boolean shouldUseDistanceOnlyCullingForVoxelization() {
        return packHasVoxelization && shadowCullingMode == ShadowCullingMode.DEFAULT;
    }

    static boolean usesTerrainCameraForEntityShadows(float entityShadowDistanceMultiplier) {
        return entityShadowDistanceMultiplier == 1.0F || entityShadowDistanceMultiplier < 0.0F;
    }

    static float entityShadowRenderMultiplier(float renderMultiplier, float entityShadowDistanceMultiplier) {
        return renderMultiplier * entityShadowDistanceMultiplier;
    }

    static double shadowRenderDistanceBlocks(float halfPlaneLength, float renderMultiplier) {
        return shadowRenderDistanceBlocks(halfPlaneLength, renderMultiplier, getConfiguredUserShadowDistanceChunks());
    }

    static double shadowRenderDistanceBlocks(float halfPlaneLength, float renderMultiplier, int userShadowDistanceChunks) {
        if (renderMultiplier < 0.0F) {
            return userShadowDistanceChunks * 16.0D;
        }

        return halfPlaneLength * renderMultiplier;
    }

    static double disabledCullingShadowRenderDistanceBlocks(float halfPlaneLength, float renderMultiplier) {
        return halfPlaneLength * renderMultiplier;
    }

    static boolean shouldSkipShadowRenderingForDistance(
            float halfPlaneLength,
            float renderMultiplier,
            boolean renderMultiplierExplicit,
            int userShadowDistanceChunks) {
        if (!renderMultiplierExplicit) {
            return userShadowDistanceChunks == 0;
        }
        if (renderMultiplier < 0.0F) {
            return false;
        }

        int forcedChunks = ((int) (halfPlaneLength * renderMultiplier) + 15) / 16;
        return forcedChunks == 0;
    }

    private ICamera createStandardCullingCamera(Minecraft mc, double minimumCullDistance, float renderMultiplier) {
        double distance = getConfiguredCullDistance(mc, minimumCullDistance, renderMultiplier, true);
        if (distance == 0.0D) {
            return ShadowCullingCameras.cullEverything();
        }
        double cullDistance = shouldApplyDistanceCull(mc, distance) ? distance : -1.0D;
        ICamera camera = ShadowCullingCameras.advanced(
            CapturedRenderingState.INSTANCE.getGbufferModelView(),
            CapturedRenderingState.INSTANCE.getGbufferProjection(),
            CelestialUniforms.getShadowLightPositionInWorldSpace(),
            cullDistance);
        return camera;
    }

    private ICamera createDistanceOnlyCamera(Minecraft mc, float renderMultiplier) {
        double distance = disabledCullingShadowRenderDistanceBlocks(halfPlaneLength, renderMultiplier);
        if (shouldApplyDistanceOnlyCull(distance, getRenderDistanceBlocks(mc))) {
            return ShadowCullingCameras.distance(distance);
        }
        return ShadowCullingCameras.nonCulling();
    }

    private ICamera createReversedCullingCamera(Minecraft mc, float renderMultiplier) {
        double innerDistance = Math.max(0.0D, voxelDistance);
        ICamera outerCamera = createStandardCullingCamera(mc, innerDistance, renderMultiplier);
        if (innerDistance <= 0.0D) {
            return outerCamera;
        }
        return ShadowCullingCameras.union(ShadowCullingCameras.distance(innerDistance), outerCamera);
    }

    private double getConfiguredCullDistance(
            Minecraft mc,
            double minimumCullDistance,
            float renderMultiplier,
            boolean useUserDistanceForNegativeMultiplier) {
        double distance = useUserDistanceForNegativeMultiplier
            ? shadowRenderDistanceBlocks(halfPlaneLength, renderMultiplier)
            : disabledCullingShadowRenderDistanceBlocks(halfPlaneLength, renderMultiplier);
        if (distance <= 0.0D) {
            return distance;
        }

        if (minimumCullDistance > 0.0D) {
            distance = Math.max(distance, minimumCullDistance);
        }
        return Math.min(distance, getRenderDistanceBlocks(mc));
    }

    private static int getConfiguredUserShadowDistanceChunks() {
        OculusConfig config = Oculus.getConfig();
        return config == null ? DEFAULT_USER_SHADOW_DISTANCE_CHUNKS : config.getMaxShadowRenderDistance();
    }

    private boolean shouldApplyDistanceCull(Minecraft mc, double distance) {
        return distance > 0.0D && distance < getRenderDistanceBlocks(mc);
    }

    static boolean shouldApplyDistanceOnlyCull(double distance, double renderDistanceBlocks) {
        return distance > 0.0D && distance <= renderDistanceBlocks;
    }

    private static double getRenderDistanceBlocks(Minecraft mc) {
        if (mc == null || mc.gameSettings == null) {
            return 0.0D;
        }
        return Math.max(0, mc.gameSettings.renderDistanceChunks) * 16.0D;
    }

    private void applyCustomBindings(
            String programName,
            ProgramBuilder builder,
            Supplier<? extends Set<Integer>> flippedBuffers) {
        RenderTargets renderTargets = renderTargetsSupplier == null ? null : renderTargetsSupplier.get();
        IrisSamplers.addRenderTargetSamplerBindings(builder, flippedBuffers, renderTargets, false);
        IrisImages.addRenderTargetImages(builder, flippedBuffers, renderTargets);
        IrisSamplers.addLevelSamplerBindings(builder, levelSamplerPipeline, new InputAvailability(true, true, false));
        bindNoiseSampler(builder);
        shadowMap.applySamplerBindings(builder, programName);
        IrisImages.addShadowColorImages(builder, shadowMap);

        if (customTextureManager != null) {
            customTextureManager.applyCustomSamplers(programName, builder.samplers(),
                currentFlippedBuffers(flippedBuffers));
        }
        if (customImageManager != null) {
            customImageManager.applyToProgram(builder);
        }
    }

    private Set<Integer> currentShadowComputeFlippedBuffers() {
        return currentFlippedBuffers(shadowComputeFlippedBuffersSupplier);
    }

    private Set<Integer> currentShadowCompositeFlippedBuffers() {
        return currentFlippedBuffers(shadowCompositeFlippedBuffersSupplier);
    }

    private Set<Integer> currentFlippedBuffers(Supplier<? extends Set<Integer>> flippedBuffersSupplier) {
        Set<Integer> flippedBuffers = flippedBuffersSupplier == null ? null : flippedBuffersSupplier.get();
        return flippedBuffers == null ? Collections.emptySet() : flippedBuffers;
    }

    private void bindNoiseSampler(ProgramBuilder builder) {
        if (noiseTexture != null) {
            builder.overrideSamplerBinding("noisetex", TextureBinding.texture2D(noiseTexture));
        }
    }

    private String applyStandardDefines(String source, ShaderType shaderType, String programName) {
        return ShaderSourcePreparer.prepare(packName, programName, shaderType, source, environmentDefines);
    }

    private void validateShadowDepthOutput() {
        if (shadowDepthValidationComplete || !OculusRuntimeValidation.isEnabled()) {
            return;
        }
        shadowDepthValidationAttempts++;
        boolean shouldSampleAttempt = shadowDepthValidationAttempts == 1
            || shadowDepthValidationAttempts == 20
            || shadowDepthValidationAttempts == 80;
        if (!shouldSampleAttempt) {
            return;
        }

        int samplesPerAxis = Math.min(16, Math.max(1, resolution));
        FloatBuffer texturePixels = BufferUtils.createFloatBuffer(resolution * resolution);
        FloatBuffer framebufferPixels = BufferUtils.createFloatBuffer(resolution * resolution);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = 0;
        int currentFramebuffer = OculusRenderSystem.getFramebufferBinding();
        boolean textureCaptured = false;
        DepthSampleStats textureStats = DepthSampleStats.empty();
        DepthSampleStats framebufferStats = DepthSampleStats.empty();

        Throwable failure = null;
        try {
            OculusRenderSystem.restoreDefaultActiveTexture();
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureCaptured = true;
            GlStateManager.bindTexture(shadowMap.getDepthTexture());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, texturePixels);
            textureStats = sampleDepthPixels(texturePixels, samplesPerAxis, resolution);

            GL11.glReadPixels(0, 0, resolution, resolution, GL11.GL_DEPTH_COMPONENT,
                GL11.GL_FLOAT, framebufferPixels);
            framebufferStats = sampleDepthPixels(framebufferPixels, samplesPerAxis, resolution);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (textureCaptured) {
                final int capturedPreviousTexture = previousTexture;
                final int capturedPreviousActiveTexture = previousActiveTexture;
                cleanupFailure = runCleanup(cleanupFailure,
                    () -> restoreDefaultTextureBinding(capturedPreviousTexture, capturedPreviousActiveTexture));
            } else {
                cleanupFailure = runCleanup(cleanupFailure,
                    () -> OculusRenderSystem.setActiveTextureUnit(previousActiveTexture));
            }
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }

        int glError = drainGlErrors();
        OculusRuntimeValidation.logShadowDepthReadback(
            "texture",
            -1,
            shadowDepthValidationAttempts,
            resolution,
            textureStats.samples,
            textureStats.nonClearSamples,
            textureStats.minDepth,
            textureStats.maxDepth,
            glError);
        OculusRuntimeValidation.logShadowDepthReadback(
            "framebuffer",
            currentFramebuffer,
            shadowDepthValidationAttempts,
            resolution,
            framebufferStats.samples,
            framebufferStats.nonClearSamples,
            framebufferStats.minDepth,
            framebufferStats.maxDepth,
            glError);
        OculusRuntimeValidation.dumpDepthTexture(
            "shadow-depth-attempt-" + shadowDepthValidationAttempts,
            shadowMap.getDepthTexture(),
            resolution,
            resolution);
        if (textureStats.nonClearSamples > 0 || framebufferStats.nonClearSamples > 0
                || shadowDepthValidationAttempts >= 80) {
            shadowDepthValidationComplete = true;
        }
    }

    private static DepthSampleStats sampleDepthPixels(FloatBuffer pixels, int samplesPerAxis, int resolution) {
        int samples = 0;
        int nonClearSamples = 0;
        float minDepth = Float.POSITIVE_INFINITY;
        float maxDepth = Float.NEGATIVE_INFINITY;

        for (int y = 0; y < samplesPerAxis; y++) {
            int sampleY = sampleCoordinate(y, samplesPerAxis, resolution);
            for (int x = 0; x < samplesPerAxis; x++) {
                int sampleX = sampleCoordinate(x, samplesPerAxis, resolution);
                float depth = pixels.get(sampleY * resolution + sampleX);
                if (Float.isNaN(depth)) {
                    continue;
                }

                samples++;
                minDepth = Math.min(minDepth, depth);
                maxDepth = Math.max(maxDepth, depth);
                if (depth < 0.9999F) {
                    nonClearSamples++;
                }
            }
        }

        if (samples == 0) {
            minDepth = Float.NaN;
            maxDepth = Float.NaN;
        }

        return new DepthSampleStats(samples, nonClearSamples, minDepth, maxDepth);
    }

    private static final class DepthSampleStats {
        private final int samples;
        private final int nonClearSamples;
        private final float minDepth;
        private final float maxDepth;

        private DepthSampleStats(int samples, int nonClearSamples, float minDepth, float maxDepth) {
            this.samples = samples;
            this.nonClearSamples = nonClearSamples;
            this.minDepth = minDepth;
            this.maxDepth = maxDepth;
        }

        private static DepthSampleStats empty() {
            return new DepthSampleStats(0, 0, Float.NaN, Float.NaN);
        }
    }

    private void dumpShadowDepthForValidation() {
        OculusRuntimeValidation.dumpDepthTexture(
            "shadow-depth-post-render",
            shadowMap.getDepthTexture(),
            resolution,
            resolution);
        OculusRuntimeValidation.dumpDepthTexture(
            "shadow-depth-notrans-post-render",
            shadowMap.getDepthTextureNoTranslucents(),
            resolution,
            resolution);
    }

    private static int drainGlErrors() {
        int lastError = GL11.GL_NO_ERROR;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            lastError = error;
        }
        return lastError;
    }

    private static int sampleCoordinate(int index, int samplesPerAxis, int resolution) {
        int coordinate = (int) (((index + 0.5D) * resolution) / samplesPerAxis);
        return Math.max(0, Math.min(resolution - 1, coordinate));
    }

    private static void renderShadowBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer,
            float partialTicks, Entity cameraEntity) {
        OculusRuntimeValidation.logShadowTerrainLayerRendered(layer.name());
        renderGlobal.renderBlockLayer(layer, partialTicks, 0, cameraEntity);
    }

    private static void copyMatrix(float[] source, float[] target) {
        System.arraycopy(source, 0, target, 0, 16);
    }

    private static void loadMatrix(float[] matrix) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(matrix);
        buffer.flip();
        GL11.glLoadMatrix(buffer);
    }

    public float[] getShadowProjection() {
        return shadowProjection;
    }

    public float[] getShadowModelView() {
        return shadowModelView;
    }

    public float[] getShadowModelViewInverse() {
        return shadowModelViewInverse;
    }

    public float[] getShadowProjectionInverse() {
        return shadowProjectionInverse;
    }

    public void addDebugText(List<String> messages) {
        messages.add("[Oculus] Shadow Maps: " + resolution + "x" + resolution);
        messages.add("[Oculus] Shadow Distance: " + halfPlaneLength);
        messages.add("[Oculus] Shadow Culling: " + shadowCullingMode + ", voxelDistance=" + voxelDistance);
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        try {
            Throwable failure = null;
            failure = destroyComputePrograms(failure, shadowComputes);
            failure = destroyComputeProgramGroups(failure, shadowCompositeComputes);
            failure = destroyClearPassFramebuffers(failure, shadowClearPasses);
            failure = destroyClearPassFramebuffers(failure, shadowClearPassesFull);
            failure = destroyShadowFramebuffer(failure, shadowFramebuffer);
            rethrowCleanupFailure(failure);
        } finally {
            destroyed = true;
        }
    }

    private void failIfDestroyed(String operation) {
        if (destroyed) {
            throw new IllegalStateException("Cannot " + operation + " after the shadow renderer was destroyed");
        }
    }

    private static void destroyComputePrograms(ComputeProgram[] computes) {
        rethrowCleanupFailure(destroyComputePrograms(null, computes));
    }

    private static Throwable destroyComputePrograms(Throwable failure, ComputeProgram[] computes) {
        if (computes == null) {
            return failure;
        }

        for (ComputeProgram compute : computes) {
            if (compute != null) {
                try {
                    compute.destroy();
                } catch (RuntimeException | Error exception) {
                    failure = addCleanupFailure(failure, exception);
                }
            }
        }
        return failure;
    }

    private static Throwable destroyComputeProgramGroups(Throwable failure, ComputeProgram[][] groups) {
        if (groups == null) {
            return failure;
        }

        for (ComputeProgram[] group : groups) {
            failure = destroyComputePrograms(failure, group);
        }
        return failure;
    }

    private Throwable destroyClearPassFramebuffers(Throwable failure, List<ClearPass> clearPasses) {
        if (clearPasses == null) {
            return failure;
        }

        for (ClearPass clearPass : clearPasses) {
            if (clearPass != null) {
                failure = runCleanup(failure, () -> shadowMap.destroyFramebuffer(clearPass.getFramebuffer()));
            }
        }
        return failure;
    }

    private Throwable destroyShadowFramebuffer(Throwable failure, GlFramebuffer framebuffer) {
        if (framebuffer == null) {
            return failure;
        }
        return runCleanup(failure, () -> shadowMap.destroyFramebuffer(framebuffer));
    }

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
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

    private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure) {
        if (primary != null && cleanupFailure != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
    }
}
