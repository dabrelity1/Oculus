package net.oculus.mixin.pipeline;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.oculus.pipeline.BlockRenderingSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockRenderLayerOverrideMixin {
    @Inject(method = "canRenderInLayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void oculus$checkShaderPackRenderLayer(IBlockState state, BlockRenderLayer layer,
                                                   CallbackInfoReturnable<Boolean> cir) {
        BlockRenderLayer override = BlockRenderingSettings.INSTANCE.getRenderLayerOverride((Block) (Object) this);
        if (override != null) {
            cir.setReturnValue(override == layer);
        }
    }

    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void oculus$getShaderPackRenderLayer(CallbackInfoReturnable<BlockRenderLayer> cir) {
        BlockRenderLayer override = BlockRenderingSettings.INSTANCE.getRenderLayerOverride((Block) (Object) this);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
