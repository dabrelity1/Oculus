package net.oculus.mixin.vertexformat;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.oculus.pipeline.BlockContextHolder;
import net.oculus.pipeline.vertex.FluidSeparateAoTracker;
import net.oculus.pipeline.vertex.OculusBlockSensitiveBufferBuilder;
import net.oculus.pipeline.vertex.OculusExtendedDataHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockFluidRenderer.class)
public abstract class BlockFluidRendererBlockContextMixin {
    @Inject(method = "renderFluid", at = @At("HEAD"))
    private void oculus$beginFluidContext(IBlockAccess world, IBlockState state, BlockPos pos, BufferBuilder buffer,
                                          CallbackInfoReturnable<Boolean> cir) {
        FluidSeparateAoTracker.clear();

        if (buffer instanceof OculusBlockSensitiveBufferBuilder) {
            short block = BlockContextHolder.getActiveStateId(state);
            byte blockEmission = BlockContextHolder.getBlockEmission(state);
            ((OculusBlockSensitiveBufferBuilder) buffer).oculus$beginBlock(block,
                OculusExtendedDataHelper.FLUID_RENDER_TYPE, blockEmission,
                pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
    }

    @Redirect(method = "renderFluid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/color/BlockColors;colorMultiplier(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;I)I"))
    private int oculus$captureFluidTint(BlockColors blockColors, IBlockState state, IBlockAccess world, BlockPos pos,
                                        int tintIndex) {
        int color = blockColors.colorMultiplier(state, world, pos, tintIndex);
        FluidSeparateAoTracker.captureTint(color);
        return color;
    }

    @Inject(method = "renderFluid", at = @At("RETURN"))
    private void oculus$endFluidContext(IBlockAccess world, IBlockState state, BlockPos pos, BufferBuilder buffer,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (buffer instanceof OculusBlockSensitiveBufferBuilder) {
            ((OculusBlockSensitiveBufferBuilder) buffer).oculus$endBlock();
        }

        FluidSeparateAoTracker.clear();
    }
}
