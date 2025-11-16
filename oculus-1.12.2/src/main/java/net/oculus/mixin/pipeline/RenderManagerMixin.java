package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.uniforms.CapturedRenderingState;

@Mixin(RenderManager.class)
public abstract class RenderManagerMixin {
    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V", at = @At("HEAD"))
    private void oculus$captureEntity(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                      boolean renderHitBoxes, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentEntity(entity != null ? entity.getEntityId() : -1);
    }

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V", at = @At("RETURN"))
    private void oculus$clearCapturedEntity(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                            boolean renderHitBoxes, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
    }
}
