package net.oculus.mixin.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.oculus.Oculus;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.ShaderWorldRenderingPipeline;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;

/**
 * Hooks RenderGlobal so the Oculus shader pipeline can bracket terrain drawing
 * without interfering with sky, clouds, entities, or hand rendering.
 */
@Mixin(RenderGlobal.class)
public abstract class WorldRendererMixin {
    @Unique
    private boolean oculus$restoreCull;
    @Unique
    private boolean oculus$previousCull;
    @Unique
    private boolean oculus$restoreBlockEntityDrawState;
    @Unique
    private boolean oculus$previousBlockEntityDepthMask;
    @Unique
    private boolean oculus$previousBlockEntityBlend;

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"))
    private void oculus$beginSky(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.CUSTOM_SKY);
    }

    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void oculus$endSky(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;isSurfaceWorld()Z", shift = At.Shift.AFTER))
    private void oculus$beginVanillaSky(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;calcSunriseSunsetColors(FF)[F"))
    private void oculus$renderSunset(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.SUNSET);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;SUN_TEXTURES:Lnet/minecraft/util/ResourceLocation;", opcode = Opcodes.GETSTATIC))
    private void oculus$renderSun(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.SUN);
    }

    @Inject(method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getCelestialAngle(F)F", ordinal = 1),
        require = 1,
        allow = 1)
    private void oculus$tiltSunPath(float partialTicks, int pass, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline == null) {
            return;
        }

        float rotation = pipeline.getSunPathRotation();
        if (rotation != 0.0F) {
            GlStateManager.rotate(rotation, 0.0F, 0.0F, 1.0F);
        }
    }

    @Inject(method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;SUN_TEXTURES:Lnet/minecraft/util/ResourceLocation;", opcode = Opcodes.GETSTATIC),
            to = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;MOON_PHASES_TEXTURES:Lnet/minecraft/util/ResourceLocation;", opcode = Opcodes.GETSTATIC)),
        require = 1,
        allow = 1)
    private void oculus$beforeDrawSun(float partialTicks, int pass, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null && !pipeline.shouldRenderSun()) {
            emptySkyBuffer();
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;MOON_PHASES_TEXTURES:Lnet/minecraft/util/ResourceLocation;", opcode = Opcodes.GETSTATIC))
    private void oculus$renderMoon(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.MOON);
    }

    @Inject(method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;MOON_PHASES_TEXTURES:Lnet/minecraft/util/ResourceLocation;", opcode = Opcodes.GETSTATIC),
            to = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F")),
        require = 1,
        allow = 1)
    private void oculus$beforeDrawMoon(float partialTicks, int pass, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null && !pipeline.shouldRenderMoon()) {
            emptySkyBuffer();
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F"))
    private void oculus$renderStars(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.STARS);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;sky2VBO:Lnet/minecraft/client/renderer/vertex/VertexBuffer;", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void oculus$beginVoidVbo(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.VOID);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;sky2VBO:Lnet/minecraft/client/renderer/vertex/VertexBuffer;", opcode = Opcodes.GETFIELD, ordinal = 1))
    private void oculus$endVoidVbo(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;glSkyList2:I", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void oculus$beginVoidDisplayList(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.VOID);
    }

    @Inject(method = "renderSky(FI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;glSkyList2:I", opcode = Opcodes.GETFIELD, ordinal = 1))
    private void oculus$endVoidDisplayList(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = "renderClouds(FIDDD)V", at = @At("HEAD"))
    private void oculus$beginClouds(float partialTicks, int pass, double camX, double camY, double camZ, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.CLOUDS);
    }

    @Inject(method = "renderClouds(FIDDD)V", at = @At("RETURN"))
    private void oculus$endClouds(float partialTicks, int pass, double camX, double camY, double camZ, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("HEAD"))
    private void oculus$beginBlockLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity, CallbackInfoReturnable<Integer> cir) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockLayer.head." + layer);
        setPhase(WorldRenderingPhase.fromBlockRenderLayer(layer));
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockLayer.afterSetPhase." + layer);
        applyTerrainCullDirective(layer);
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockLayer.afterCullDirective." + layer);
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
            if (pipeline != null) {
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.translucent.beforeHand");
                oculus$renderShaderHandBeforeTranslucents(partialTicks, pass, pipeline);
                OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.translucent.renderHand");
                pipeline.beginTranslucents();
            }
        }
    }

    @Unique
    private void oculus$renderShaderHandBeforeTranslucents(double partialTicks, int pass,
                                                          WorldRenderingPipeline pipeline) {
        if (!(pipeline instanceof ShaderWorldRenderingPipeline) || pipeline.isRenderingShadowPass()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityRenderer renderer = minecraft == null ? null : minecraft.entityRenderer;
        if (!(renderer instanceof EntityRendererAccessor)) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            ((EntityRendererAccessor) renderer).oculus$invokeRenderHand((float) partialTicks, pass);
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(previousMatrixMode);
        }
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void oculus$endBlockLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity, CallbackInfoReturnable<Integer> cir) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockLayer.return." + layer);
        restoreTerrainCullState();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockLayer.afterCullRestore." + layer);
        setPhase(WorldRenderingPhase.NONE);
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockLayer.afterSetPhaseNone." + layer);
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", at = @At("HEAD"))
    private void oculus$beginEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.entities.head");
        setPhase(WorldRenderingPhase.ENTITIES);
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.entities.afterSetPhase");
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", at = @At("RETURN"))
    private void oculus$endEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.entities.return");
        setPhase(WorldRenderingPhase.NONE);
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.entities.afterSetPhaseNone");
    }

    @Inject(method = "renderEntityOutlineFramebuffer()V", at = @At("HEAD"))
    private void oculus$beginOutlines(CallbackInfo ci) {
        setPhase(WorldRenderingPhase.OUTLINE);
    }

    @Inject(method = "renderEntityOutlineFramebuffer()V", at = @At("RETURN"))
    private void oculus$endOutlines(CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderWorldBorder(Lnet/minecraft/entity/Entity;F)V", at = @At("HEAD"))
    private void oculus$beginWorldBorder(Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.WORLD_BORDER);
    }

    @Inject(method = "renderWorldBorder(Lnet/minecraft/entity/Entity;F)V", at = @At("RETURN"))
    private void oculus$endWorldBorder(Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
        at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=blockentities"))
    private void oculus$beginBlockEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockEntities.beforeSetPhase");
        setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockEntities.afterSetPhase");
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher;preDrawBatch()V", remap = false))
    private void oculus$beginVanillaBlockEntityBatch(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockEntities.beforeVanillaBatchState");
        oculus$applyBlockEntityDrawState();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockEntities.afterVanillaBatchState");
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher;drawBatch(I)V", shift = At.Shift.AFTER, remap = false))
    private void oculus$endBlockEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockEntities.beforeSetPhaseNone");
        oculus$closeBlockEntityPhase();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("worldRenderer.blockEntities.afterSetPhaseNone");
    }

    @Inject(method = "renderSkyEnd()V", at = @At("HEAD"))
    private void oculus$beginEndSky(CallbackInfo ci) {
        setPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = "renderSkyEnd()V", at = @At("RETURN"))
    private void oculus$endEndSky(CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
    }

    @Inject(method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V", at = @At("HEAD"))
    private void oculus$beginBlockDestroy(Tessellator tessellator, BufferBuilder bufferBuilder, Entity entity, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.DESTROY);
    }

    @Inject(method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V", at = @At("RETURN"))
    private void oculus$endBlockDestroy(Tessellator tessellator, BufferBuilder bufferBuilder, Entity entity, float partialTicks, CallbackInfo ci) {
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
    private static void emptySkyBuffer() {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.reset();
    }

    @Unique
    private void applyTerrainCullDirective(BlockRenderLayer layer) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline == null || pipeline.isRenderingShadowPass()) {
            return;
        }

        oculus$previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        oculus$restoreCull = true;
        if (pipeline.shouldRenderTerrainBackFaces(layer)) {
            GlStateManager.disableCull();
        } else {
            GlStateManager.enableCull();
        }
    }

    @Unique
    private void restoreTerrainCullState() {
        if (!oculus$restoreCull) {
            return;
        }

        oculus$restoreCull = false;
        if (oculus$previousCull) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
    }

    @Unique
    private void oculus$applyBlockEntityDrawState() {
        oculus$restoreBlockEntityDrawState = false;
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (!(pipeline instanceof ShaderWorldRenderingPipeline) || pipeline.isRenderingShadowPass()) {
            return;
        }

        oculus$previousBlockEntityDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        oculus$previousBlockEntityBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        oculus$restoreBlockEntityDrawState = true;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
    }

    @Unique
    private void oculus$restoreBlockEntityDrawState() {
        if (!oculus$restoreBlockEntityDrawState) {
            return;
        }

        oculus$restoreBlockEntityDrawState = false;
        GlStateManager.depthMask(oculus$previousBlockEntityDepthMask);
        if (oculus$previousBlockEntityBlend) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
    }

    @Unique
    private void oculus$closeBlockEntityPhase() {
        setPhase(WorldRenderingPhase.NONE);
        oculus$restoreBlockEntityDrawState();
    }
}
