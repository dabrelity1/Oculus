package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.pipeline.PipelineManager;

/**
 * Bridges the vanilla world render loop to the Oculus shader pipeline. The 1.16.5 mixin
 * targeted {@code LevelRenderer}; on 1.12.2 the equivalent entrypoint is
 * {@code EntityRenderer#renderWorld}.
 */
@Mixin(EntityRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
    private void oculus$beginWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineManager.INSTANCE.beginWorldRendering(partialTicks);
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("RETURN"))
    private void oculus$endWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineManager.INSTANCE.endWorldRendering();
    }
}
