package net.oculus.mixin;

import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.oculus.mixin.extensions.IChunkBuildBuffers;
import net.oculus.pipeline.BlockContextHolder;
import net.oculus.pipeline.vertex.OculusExtendedDataHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkRenderRebuildTask.class, remap = false)
public abstract class ChunkRenderRebuildTaskMixin {
    @Unique
    private BlockContextHolder oculus_contextHolder;

    @Inject(method = "performBuild", at = @At("HEAD"), remap = false)
    private void oculus$grabContext(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult<?>> cir) {
        if (buffers instanceof IChunkBuildBuffers) {
            this.oculus_contextHolder = ((IChunkBuildBuffers) buffers).oculus_getContextHolder();
        } else {
            this.oculus_contextHolder = null;
        }
    }

    @Redirect(method = "performBuild", at = @At(value = "INVOKE", target = "me/jellysquid/mods/sodium/client/render/pipeline/BlockRenderer.renderModel(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lme/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuffers;ZJ)Z"), remap = false)
    private boolean oculus$onRenderBlock(BlockRenderer renderer, IBlockAccess world, IBlockState state, BlockPos pos, IBakedModel model, ChunkModelBuffers modelBuffers, boolean cull, long seed, ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        if (this.oculus_contextHolder != null) {
            this.oculus_contextHolder.setLocalPos(pos.getX(), pos.getY(), pos.getZ());
            this.oculus_contextHolder.set(state, OculusExtendedDataHelper.BLOCK_RENDER_TYPE);
        }

        try {
            return renderer.renderModel(world, state, pos, model, modelBuffers, cull, seed);
        } finally {
            if (this.oculus_contextHolder != null) {
                this.oculus_contextHolder.reset();
            }
        }
    }

    @Redirect(method = "performBuild", at = @At(value = "INVOKE", target = "me/jellysquid/mods/sodium/client/render/pipeline/FluidRenderer.render(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lme/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuffers;)Z"), remap = false)
    private boolean oculus$onRenderFluid(FluidRenderer renderer, IBlockAccess world, IBlockState state, BlockPos pos, ChunkModelBuffers modelBuffers, ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        if (this.oculus_contextHolder != null) {
            this.oculus_contextHolder.setLocalPos(pos.getX(), pos.getY(), pos.getZ());
            this.oculus_contextHolder.set(state, OculusExtendedDataHelper.FLUID_RENDER_TYPE);
        }

        try {
            return renderer.render(world, state, pos, modelBuffers);
        } finally {
            if (this.oculus_contextHolder != null) {
                this.oculus_contextHolder.reset();
            }
        }
    }
}
