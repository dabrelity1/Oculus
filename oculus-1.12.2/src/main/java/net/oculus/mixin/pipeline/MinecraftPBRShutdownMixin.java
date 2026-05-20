package net.oculus.mixin.pipeline;

import net.minecraft.client.Minecraft;
import net.oculus.texture.pbr.PBRTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftPBRShutdownMixin {
    @Inject(
        method = "shutdownMinecraftApplet()V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/Display;destroy()V",
            remap = false))
    private void oculus$onShutdownMinecraftApplet(CallbackInfo ci) {
        PBRTextureManager.INSTANCE.close();
    }
}
