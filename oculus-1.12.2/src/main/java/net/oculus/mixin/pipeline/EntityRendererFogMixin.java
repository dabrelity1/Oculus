package net.oculus.mixin.pipeline;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.gl.state.GameDataSuppliers;
import net.oculus.uniforms.CapturedRenderingState;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererFogMixin {
    @Inject(method = "updateFogColor(F)V", at = @At("RETURN"))
    private void oculus$captureFogColor(float partialTicks, CallbackInfo ci) {
        FloatBuffer fog = GameDataSuppliers.fogColor().get();
        if (fog == null || fog.limit() < 3) {
            return;
        }

        CapturedRenderingState.INSTANCE.setFogColor(fog.get(0), fog.get(1), fog.get(2));
    }
}
