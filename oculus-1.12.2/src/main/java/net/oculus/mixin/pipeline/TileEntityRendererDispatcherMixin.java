package net.oculus.mixin.pipeline;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.oculus.uniforms.CapturedRenderingState;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityRendererDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("HEAD"))
    private void oculus$captureBlockEntity(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(resolveBlockEntityId(tileEntity));
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At("RETURN"))
    private void oculus$clearBlockEntity(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(-1);
    }

    private static int resolveBlockEntityId(TileEntity tileEntity) {
        if (tileEntity == null) {
            return -1;
        }

        Block block = tileEntity.getBlockType();
        return block != null ? Block.getIdFromBlock(block) : -1;
    }
}
