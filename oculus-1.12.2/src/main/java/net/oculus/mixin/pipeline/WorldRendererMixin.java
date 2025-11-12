package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.pipeline.PipelineManager;

/**
 * Hooks the Sodium world renderer so the Oculus shader pipeline can bracket terrain drawing
 * without interfering with sky, clouds, or hand rendering.
 */
@Mixin(RenderGlobal.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderWorld", at = @At("HEAD"), remap = false)
    private void oculus$beginWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineManager.INSTANCE.beginWorldRendering(partialTicks);
    }

    @Inject(method = "renderWorld", at = @At("RETURN"), remap = false)
    private void oculus$endWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineManager.INSTANCE.endWorldRendering();
    }
}
