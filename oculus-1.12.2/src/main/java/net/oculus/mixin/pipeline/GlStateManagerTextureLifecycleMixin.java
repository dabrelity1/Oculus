package net.oculus.mixin.pipeline;

import java.nio.IntBuffer;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.GlStateManager;
import net.oculus.texture.TextureLifecycleTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerTextureLifecycleMixin {
    @Inject(method = "glTexImage2D(IIIIIIIILjava/nio/IntBuffer;)V", at = @At("TAIL"))
    private static void oculus$onTexImage2D(int target,
                                            int level,
                                            int internalFormat,
                                            int width,
                                            int height,
                                            int border,
                                            int format,
                                            int type,
                                            @Nullable IntBuffer pixels,
                                            CallbackInfo ci) {
        TextureLifecycleTracker.onTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Inject(method = "deleteTexture(I)V", at = @At("TAIL"))
    private static void oculus$onDeleteTexture(int textureId, CallbackInfo ci) {
        TextureLifecycleTracker.onDeleteTexture(textureId);
    }
}
