package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.pipeline.shadow.ShadowRenderingState;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.IdMapUniforms;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityRendererDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("HEAD"), cancellable = true)
    private void oculus$captureBlockEntity(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (!ShadowRenderingState.shouldRenderBlockEntities()) {
            ci.cancel();
            return;
        }
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(IdMapUniforms.resolveBlockEntityId(tileEntity));
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("RETURN"))
    private void oculus$clearBlockEntity(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(-1);
    }
}
