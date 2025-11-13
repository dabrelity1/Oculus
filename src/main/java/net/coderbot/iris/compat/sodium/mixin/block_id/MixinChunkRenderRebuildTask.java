package net.coderbot.iris.compat.sodium.mixin.block_id;

import net.coderbot.iris.compat.sodium.impl.block_context.ChunkBuildBuffersExt;
import net.coderbot.iris.vertices.ExtendedDataHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.render.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.client.renderer.chunk.VisGraph;

/**
 * Passes additional information indirectly to the vertex writer to support the mc_Entity and at_midBlock parts of the vertex format.
 */
@Mixin(ChunkRenderRebuildTask.class)
public class MixinChunkRenderRebuildTask {
	@Unique
	private ChunkBuildBuffersExt iris$contextBuffers;

	@Unique
	private IBlockState iris$currentState;

	@Inject(method = "performBuild", at = @At("HEAD"), remap = false)
	private void iris$cacheBuffers(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers,
								 CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult<?>> cir) {
		this.iris$contextBuffers = buffers instanceof ChunkBuildBuffersExt ? (ChunkBuildBuffersExt) buffers : null;
		this.iris$currentState = null;
	}

	@Inject(method = "performBuild", at = @At(value = "INVOKE",
			target = "net/minecraft/block/state/IBlockState.getRenderType()" +
				"Lnet/minecraft/util/EnumBlockRenderType;"),
			locals = LocalCapture.CAPTURE_FAILHARD)
	private void iris$setLocalPos(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers,
								  CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult<?>> cir,
								  ChunkRenderData.Builder renderData, VisGraph occluder, ChunkRenderBounds.Builder bounds,
								  WorldSlice slice, int baseX, int baseY, int baseZ,
								  BlockPos.MutableBlockPos pos, BlockPos renderOffset,
								  int relY, int relZ, int relX, IBlockState blockState) {
		if (this.iris$contextBuffers != null) {
			this.iris$contextBuffers.iris$setLocalPos(relX, relY, relZ);
			this.iris$contextBuffers.iris$setMaterialId(blockState, ExtendedDataHelper.BLOCK_RENDER_TYPE);
		}

		this.iris$currentState = blockState;
	}

	@Redirect(method = "performBuild", at = @At(value = "INVOKE",
			target = "Lme/jellysquid/mods/sodium/client/util/MathUtil;hashPos(Lnet/minecraft/util/math/BlockPos;)J", remap = false))
	private long iris$wrapHash(BlockPos pos, ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers) {
		if (this.iris$contextBuffers != null && this.iris$currentState != null) {
			this.iris$contextBuffers.iris$setMaterialId(this.iris$currentState, ExtendedDataHelper.BLOCK_RENDER_TYPE);
		}

		return MathUtil.hashPos(pos);
	}

	@Redirect(method = "performBuild", at = @At(value = "INVOKE",
			target = "Lme/jellysquid/mods/sodium/client/render/pipeline/FluidRenderer;render(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lme/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuffers;)Z", remap = false))
	private boolean iris$wrapGetFluidLayer(FluidRenderer renderer, IBlockAccess world, IBlockState fluidState, BlockPos pos, ChunkModelBuffers modelBuffers, ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers) {
		if (this.iris$contextBuffers != null) {
			this.iris$contextBuffers.iris$setMaterialId(fluidState, ExtendedDataHelper.FLUID_RENDER_TYPE);
		}

		return renderer.render(world, fluidState, pos, modelBuffers);
	}

	@Redirect(method = "performBuild", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/state/IBlockState;getActualState(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"), remap = false)
	private IBlockState iris$updateMaterialAfterActualState(IBlockState state, IBlockAccess world, BlockPos pos, ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers) {
		IBlockState updated = state.getActualState(world, pos);

		if (this.iris$contextBuffers != null) {
			this.iris$contextBuffers.iris$setMaterialId(updated, ExtendedDataHelper.BLOCK_RENDER_TYPE);
		}

		this.iris$currentState = updated;

		return updated;
	}

	@Inject(method = "performBuild",
			at = @At(value = "INVOKE", target = "net/minecraft/block/Block.hasTileEntity(Lnet/minecraft/block/state/IBlockState;)Z"), remap = false)
	private void iris$resetContext(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers,
							  CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult<?>> cir) {
		if (this.iris$contextBuffers != null) {
			this.iris$contextBuffers.iris$resetBlockContext();
		}

		this.iris$currentState = null;
	}
}
