package net.oculus.mixin.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.Oculus;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.ShaderWorldRenderingPipeline;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.pipeline.particle.PhasedParticleManager;
import net.oculus.pipeline.particle.ParticleRenderingPhase;
import net.oculus.shaderpack.ParticleRenderingOrder;
import net.oculus.uniforms.SystemTimeUniforms;

/**
 * Bridges the vanilla world render loop to the Oculus shader pipeline. The 1.16.5 mixin
 * targeted {@code LevelRenderer}; on 1.12.2 the equivalent entrypoint is
 * {@code EntityRenderer#renderWorld}.
 */
@Mixin(EntityRenderer.class)
public abstract class LevelRendererMixin {
    private static boolean debugLogged = false;
    private static final ICamera NON_CULLING_CAMERA = new NonCullingCamera();

    @Unique
    private boolean oculus$shadowTerrainRenderedThisFrame;

    @Shadow
    @Final
    private Minecraft mc;

    @Shadow
    private boolean debugView;

    @Shadow
    public abstract void enableLightmap();

    @Shadow
    public abstract void disableLightmap();

    @Shadow
    private void setupFog(int startCoords, float partialTicks) {
    }

    @Shadow
    private void renderHand(float partialTicks, int pass) {
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
    private void oculus$beginWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!debugLogged) {
            Oculus.LOGGER.info("[Oculus] Pipeline active - intercepting world rendering");
            debugLogged = true;
        }
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());
        oculus$shadowTerrainRenderedThisFrame = false;
        PipelineManager.INSTANCE.beginWorldRendering(partialTicks);
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V",
            shift = At.Shift.AFTER
        )
    )
    private void oculus$captureCameraStateAfterCameraTransform(int pass, float partialTicks, long finishTimeNano,
                                                              CallbackInfo ci) {
        PipelineManager.INSTANCE.afterCameraSetup(partialTicks);
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V"
        )
    )
    private void oculus$setupTerrain(RenderGlobal renderGlobal, Entity entity, double partialTicks, ICamera camera, int frameCount, boolean playerSpectator) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (!oculus$shadowTerrainRenderedThisFrame && pipeline != null) {
            oculus$shadowTerrainRenderedThisFrame = true;
            pipeline.renderShadows(renderGlobal, entity, (float) partialTicks);
        }

        ICamera effectiveCamera = pipeline != null && pipeline.shouldDisableFrustumCulling()
            ? NON_CULLING_CAMERA
            : camera;

        boolean previousRenderChunksMany = mc.renderChunksMany;
        if (pipeline != null && pipeline.shouldDisableOcclusionCulling()) {
            mc.renderChunksMany = false;
        }

        try {
            renderGlobal.setupTerrain(entity, partialTicks, effectiveCamera, frameCount, playerSpectator);
        } finally {
            mc.renderChunksMany = previousRenderChunksMany;
        }
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("RETURN"))
    private void oculus$endWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.finalizeLevelRendering();
        }
        OculusRuntimeValidation.captureWorldScreenshotAfterRender(mc);
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderLitParticles(Lnet/minecraft/entity/Entity;F)V")
    )
    private void oculus$renderLitParticlesBeforeDeferred(ParticleManager particleManager, Entity entity, float partialTicks) {
        ParticleRenderingOrder order = getParticleRenderingOrder();
        if (order == ParticleRenderingOrder.AFTER) {
            return;
        }

        setParticlePhase(particleManager, order == ParticleRenderingOrder.MIXED
            ? ParticleRenderingPhase.OPAQUE
            : ParticleRenderingPhase.EVERYTHING);
        particleManager.renderLitParticles(entity, partialTicks);
        net.oculus.client.OculusRuntimeValidation.drainGlErrorsAtCheckpoint("levelRenderer.renderLitParticles");
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/entity/Entity;F)V")
    )
    private void oculus$renderParticlesBeforeDeferred(ParticleManager particleManager, Entity entity, float partialTicks) {
        ParticleRenderingOrder order = getParticleRenderingOrder();
        if (order == ParticleRenderingOrder.AFTER) {
            return;
        }

        setParticlePhase(particleManager, order == ParticleRenderingOrder.MIXED
            ? ParticleRenderingPhase.OPAQUE
            : ParticleRenderingPhase.EVERYTHING);
        particleManager.renderParticles(entity, partialTicks);
        net.oculus.client.OculusRuntimeValidation.drainGlErrorsAtCheckpoint("levelRenderer.renderParticles");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;popMatrix()V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void oculus$afterTerrainMatrixPop(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        net.oculus.client.OculusRuntimeValidation.drainGlErrorsAtCheckpoint("levelRenderer.afterTerrainMatrixPop");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void oculus$afterOpaqueEntities(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        net.oculus.client.OculusRuntimeValidation.drainGlErrorsAtCheckpoint("levelRenderer.afterOpaqueEntities");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;popMatrix()V",
            ordinal = 1,
            shift = At.Shift.AFTER
        )
    )
    private void oculus$afterEntityMatrixPop(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        net.oculus.client.OculusRuntimeValidation.drainGlErrorsAtCheckpoint("levelRenderer.afterEntityMatrixPop");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V",
            shift = At.Shift.AFTER
        )
    )
    private void oculus$afterBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        net.oculus.client.OculusRuntimeValidation.drainGlErrorsAtCheckpoint("levelRenderer.afterBlockDamage");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V",
            shift = At.Shift.AFTER
        )
    )
    private void oculus$afterWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        net.oculus.client.OculusRuntimeValidation.drainGlErrorsAtCheckpoint("levelRenderer.afterWeather");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderWorldBorder(Lnet/minecraft/entity/Entity;F)V",
            shift = At.Shift.AFTER
        )
    )
    private void oculus$afterWorldBorder(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        net.oculus.client.OculusRuntimeValidation.drainGlErrorsAtCheckpoint("levelRenderer.afterWorldBorder");
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;clear(I)V",
            ordinal = 1)
    )
    private void oculus$skipLateShaderHandDepthClear(int mask) {
        if (!oculus$usesEarlyShaderHand()) {
            GlStateManager.clear(mask);
        }
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V")
    )
    private void oculus$skipLateVanillaHand(EntityRenderer renderer, float partialTicks, int pass) {
        if (!oculus$usesEarlyShaderHand()) {
            renderHand(partialTicks, pass);
        }
    }

    @Unique
    private boolean oculus$usesEarlyShaderHand() {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        return pipeline instanceof ShaderWorldRenderingPipeline && !pipeline.isRenderingShadowPass();
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;setRenderPass(I)V",
            ordinal = 3,
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void oculus$renderDelayedParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (debugView || mc == null || mc.effectRenderer == null) {
            return;
        }

        ParticleRenderingOrder order = getParticleRenderingOrder();
        if (order == ParticleRenderingOrder.BEFORE) {
            setParticlePhase(mc.effectRenderer, ParticleRenderingPhase.EVERYTHING);
            return;
        }

        Entity cameraEntity = mc.getRenderViewEntity();
        if (cameraEntity == null) {
            return;
        }

        ParticleRenderingPhase delayedPhase = order == ParticleRenderingOrder.MIXED
            ? ParticleRenderingPhase.TRANSLUCENT
            : ParticleRenderingPhase.EVERYTHING;

        setParticlePhase(mc.effectRenderer, delayedPhase);
        try {
            enableLightmap();
            RenderHelper.disableStandardItemLighting();

            if (order == ParticleRenderingOrder.AFTER) {
                mc.profiler.endStartSection("delayed_lit_particles");
                mc.effectRenderer.renderLitParticles(cameraEntity, partialTicks);
            }

            setupFog(0, partialTicks);
            mc.profiler.endStartSection(order == ParticleRenderingOrder.MIXED ? "translucent_particles" : "delayed_particles");
            mc.effectRenderer.renderParticles(cameraEntity, partialTicks);
        } finally {
            disableLightmap();
            setParticlePhase(mc.effectRenderer, ParticleRenderingPhase.EVERYTHING);
        }
    }

    @ModifyArg(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;depthMask(Z)V",
            ordinal = 0
        )
    )
    private boolean oculus$writeRainAndSnowToDepthBuffer(boolean depthMaskEnabled) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null && pipeline.shouldWriteRainAndSnowToDepthBuffer()) {
            return true;
        }

        return depthMaskEnabled;
    }

    @Inject(method = "renderRainSnow(F)V", at = @At("HEAD"))
    private void oculus$beginWeather(float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.RAIN_SNOW);
    }

    @Inject(method = "renderRainSnow(F)V", at = @At("RETURN"))
    private void oculus$endWeather(float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderHand(FI)V", at = @At("HEAD"))
    private void oculus$beginHand(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.HAND_SOLID);
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.beginHand();
        }
    }

    @Inject(method = "renderHand(FI)V", at = @At("RETURN"))
    private void oculus$endHand(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Unique
    private static void setPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }

    @Unique
    private static ParticleRenderingOrder getParticleRenderingOrder() {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline == null) {
            return ParticleRenderingOrder.BEFORE;
        }
        return pipeline.getParticleRenderingOrder();
    }

    @Unique
    private static void setParticlePhase(ParticleManager particleManager, ParticleRenderingPhase phase) {
        if (particleManager instanceof PhasedParticleManager) {
            ((PhasedParticleManager) particleManager).setParticleRenderingPhase(phase);
        }
    }

    private static final class NonCullingCamera implements ICamera {
        @Override
        public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
            return true;
        }

        @Override
        public void setPosition(double x, double y, double z) {
        }
    }
}
