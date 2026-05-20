package net.oculus.mixin.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public abstract class FramebufferStateMixin {
    @Inject(method = "bindFramebuffer(Z)V", at = @At("RETURN"))
    private void oculus$onBindFramebuffer(boolean setViewport, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline == null) {
            return;
        }

        pipeline.getRenderTargetStateListener().setIsMainBound(this == (Object) minecraft.getFramebuffer());
    }
}
