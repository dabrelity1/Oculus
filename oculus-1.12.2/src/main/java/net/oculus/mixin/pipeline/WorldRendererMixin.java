package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.oculus.Oculus;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;

/**
 * Hooks the Sodium world renderer so the Oculus shader pipeline can bracket terrain drawing
 * without interfering with sky, clouds, or hand rendering.
 */
@Mixin(RenderGlobal.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"))
    private void oculus$beginSky(float partialTicks, int pass, CallbackInfo ci) {
        Oculus.LOGGER.info("[Oculus] intercepting renderSky (pass={})", pass);
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

    @Inject(method = "renderSky(FI)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;MOON_PHASES_TEXTURES:Lnet/minecraft/util/ResourceLocation;", opcode = Opcodes.GETSTATIC))
    private void oculus$renderMoon(float partialTicks, int pass, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.MOON);
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
        setPhase(WorldRenderingPhase.fromBlockRenderLayer(layer));
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
            if (pipeline != null) {
                Oculus.LOGGER.info("[Oculus] intercepting translucent renderBlockLayer -> beginTranslucents");
                pipeline.beginTranslucents();
            }
        }
    }

    @Inject(method = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void oculus$endBlockLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity, CallbackInfoReturnable<Integer> cir) {
        setPhase(WorldRenderingPhase.NONE);
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
            if (pipeline != null) {
                Oculus.LOGGER.info("[Oculus] intercepting translucent renderBlockLayer -> end");
            }
        }
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", at = @At("HEAD"))
    private void oculus$beginEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.ENTITIES);
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", at = @At("RETURN"))
    private void oculus$endEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
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
        setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher;drawBatch(I)V", shift = At.Shift.AFTER, remap = false))
    private void oculus$endBlockEntities(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        setPhase(WorldRenderingPhase.NONE);
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

    private static void setPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }
}
