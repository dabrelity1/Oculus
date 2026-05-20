package net.oculus.mixin.pipeline;

import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;

@Mixin(GuiIngame.class)
public abstract class GuiIngameOverlayMixin {
    @Inject(method = "renderVignette(FLnet/minecraft/client/gui/ScaledResolution;)V", at = @At("HEAD"), cancellable = true)
    private void oculus$disableVignette(float lightLevel, ScaledResolution resolution, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline == null || pipeline.shouldRenderVignette()) {
            return;
        }

        GlStateManager.enableDepth();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        ci.cancel();
    }
}
