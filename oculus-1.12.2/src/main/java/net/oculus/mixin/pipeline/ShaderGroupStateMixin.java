package net.oculus.mixin.pipeline;

import net.minecraft.client.shader.ShaderGroup;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderGroup.class)
public abstract class ShaderGroupStateMixin {
    @Inject(method = "render(F)V", at = @At("HEAD"))
    private void oculus$beginPostChain(float partialTicks, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.getRenderTargetStateListener().beginPostChain();
        }
    }

    @Inject(method = "render(F)V", at = @At("RETURN"))
    private void oculus$endPostChain(float partialTicks, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.getRenderTargetStateListener().endPostChain();
        }
    }
}
