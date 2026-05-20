package net.oculus.mixin.pipeline;

import net.minecraft.client.shader.Framebuffer;
import net.oculus.rendertarget.MinecraftFramebufferExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public abstract class FramebufferVersionMixin implements MinecraftFramebufferExt {
    @Shadow
    public int framebufferTexture;

    @Shadow
    public int depthBuffer;

    @Unique
    private int oculus$depthBufferVersion;

    @Unique
    private int oculus$colorBufferVersion;

    @Inject(method = "deleteFramebuffer()V", at = @At("HEAD"))
    private void oculus$onDeleteFramebuffer(CallbackInfo ci) {
        if (framebufferTexture > -1) {
            oculus$colorBufferVersion++;
        }
        if (depthBuffer > -1) {
            oculus$depthBufferVersion++;
        }
    }

    @Override
    public int oculus$getDepthBufferVersion() {
        return oculus$depthBufferVersion;
    }

    @Override
    public int oculus$getColorBufferVersion() {
        return oculus$colorBufferVersion;
    }
}
