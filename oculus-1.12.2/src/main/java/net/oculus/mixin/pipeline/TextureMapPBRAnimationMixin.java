package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.texture.TextureMap;
import net.oculus.texture.pbr.PBRTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
public abstract class TextureMapPBRAnimationMixin {
    @Inject(method = "updateAnimations()V", at = @At("RETURN"))
    private void oculus$updatePbrAtlasAnimations(CallbackInfo ci) {
        PBRTextureManager.INSTANCE.updateAtlasAnimations((TextureMap) (Object) this);
    }
}
