package net.oculus.mixin.pipeline;

import java.util.Map;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.GlStateManager;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.ShaderWorldRenderingPipeline;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class RelictiumTileEntityRenderMixin {
    @Unique
    private boolean oculus$relictiumTileEntityScope;
    @Unique
    private boolean oculus$restoreRelictiumTileEntityDrawState;
    @Unique
    private boolean oculus$previousRelictiumTileEntityDepthMask;
    @Unique
    private boolean oculus$previousRelictiumTileEntityBlend;

    @Inject(method = "renderTileEntities", at = @At("HEAD"), remap = false)
    private void oculus$beginRelictiumTileEntities(float partialTicks,
                                                  Map<Integer, DestroyBlockProgress> damagedBlocks,
                                                  CallbackInfo ci) {
        oculus$relictiumTileEntityScope = false;
        oculus$restoreRelictiumTileEntityDrawState = false;

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (!(pipeline instanceof ShaderWorldRenderingPipeline)) {
            return;
        }

        oculus$relictiumTileEntityScope = true;
        pipeline.setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("relictium.tileEntities.afterSetPhase");

        if (pipeline.isRenderingShadowPass()) {
            return;
        }

        oculus$previousRelictiumTileEntityDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        oculus$previousRelictiumTileEntityBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        oculus$restoreRelictiumTileEntityDrawState = true;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("relictium.tileEntities.afterDrawState");
    }

    @Inject(method = "renderTileEntities", at = @At("RETURN"), remap = false)
    private void oculus$endRelictiumTileEntities(float partialTicks,
                                                Map<Integer, DestroyBlockProgress> damagedBlocks,
                                                CallbackInfo ci) {
        if (!oculus$relictiumTileEntityScope) {
            return;
        }

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }

        oculus$restoreRelictiumTileEntityDrawState();
        oculus$relictiumTileEntityScope = false;
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("relictium.tileEntities.afterCleanup");
    }

    @Unique
    private void oculus$restoreRelictiumTileEntityDrawState() {
        if (!oculus$restoreRelictiumTileEntityDrawState) {
            return;
        }

        oculus$restoreRelictiumTileEntityDrawState = false;
        GlStateManager.depthMask(oculus$previousRelictiumTileEntityDepthMask);
        if (oculus$previousRelictiumTileEntityBlend) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
    }
}
