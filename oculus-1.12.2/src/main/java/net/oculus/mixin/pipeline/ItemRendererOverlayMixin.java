package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererOverlayMixin {
    @Inject(method = "renderWaterOverlayTexture(F)V", at = @At("HEAD"), cancellable = true)
    private void oculus$disableUnderwaterOverlay(float partialTicks, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null && !pipeline.shouldRenderUnderwaterOverlay()) {
            ci.cancel();
        }
    }
}
