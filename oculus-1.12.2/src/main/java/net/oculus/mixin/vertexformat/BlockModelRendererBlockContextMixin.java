package net.oculus.mixin.vertexformat;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.oculus.pipeline.BlockContextHolder;
import net.oculus.pipeline.vertex.OculusBlockSensitiveBufferBuilder;
import net.oculus.pipeline.vertex.OculusExtendedDataHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererBlockContextMixin {
    @Inject(method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;ZJ)Z",
        at = @At("HEAD"))
    private void oculus$beginBlockContext(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                                          BufferBuilder buffer, boolean checkSides, long rand,
                                          CallbackInfoReturnable<Boolean> cir) {
        oculus$beginBlock(buffer, state, pos);
    }

    @Inject(method = "renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;ZJ)Z",
        at = @At("RETURN"))
    private void oculus$endBlockContext(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                                        BufferBuilder buffer, boolean checkSides, long rand,
                                        CallbackInfoReturnable<Boolean> cir) {
        oculus$endBlock(buffer);
    }

    @Inject(method = "renderModelSmooth", at = @At("HEAD"))
    private void oculus$beginSmoothBlockContext(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                                                BufferBuilder buffer, boolean checkSides, long rand,
                                                CallbackInfoReturnable<Boolean> cir) {
        oculus$beginBlock(buffer, state, pos);
    }

    @Inject(method = "renderModelSmooth", at = @At("RETURN"))
    private void oculus$endSmoothBlockContext(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                                              BufferBuilder buffer, boolean checkSides, long rand,
                                              CallbackInfoReturnable<Boolean> cir) {
        oculus$endBlock(buffer);
    }

    @Inject(method = "renderModelFlat", at = @At("HEAD"))
    private void oculus$beginFlatBlockContext(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                                              BufferBuilder buffer, boolean checkSides, long rand,
                                              CallbackInfoReturnable<Boolean> cir) {
        oculus$beginBlock(buffer, state, pos);
    }

    @Inject(method = "renderModelFlat", at = @At("RETURN"))
    private void oculus$endFlatBlockContext(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                                            BufferBuilder buffer, boolean checkSides, long rand,
                                            CallbackInfoReturnable<Boolean> cir) {
        oculus$endBlock(buffer);
    }

    private static void oculus$beginBlock(BufferBuilder buffer, IBlockState state, BlockPos pos) {
        if (buffer instanceof OculusBlockSensitiveBufferBuilder) {
            short block = BlockContextHolder.getActiveStateId(state);
            byte blockEmission = BlockContextHolder.getBlockEmission(state);
            ((OculusBlockSensitiveBufferBuilder) buffer).oculus$beginBlock(block,
                OculusExtendedDataHelper.BLOCK_RENDER_TYPE, blockEmission,
                pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
    }

    private static void oculus$endBlock(BufferBuilder buffer) {
        if (buffer instanceof OculusBlockSensitiveBufferBuilder) {
            ((OculusBlockSensitiveBufferBuilder) buffer).oculus$endBlock();
        }
    }
}
