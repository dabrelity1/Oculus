package net.coderbot.iris.compat.sodium.mixin.block_id;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.embeddedt.embeddium.client.model.vertex.VertexSink;
import org.embeddedt.embeddium.client.model.vertex.buffer.VertexBufferView;
import org.embeddedt.embeddium.client.model.vertex.type.ChunkVertexType;
import org.embeddedt.embeddium.client.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.client.render.chunk.passes.BlockRenderPassManager;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.compat.sodium.impl.block_context.BlockContextHolder;
import net.coderbot.iris.compat.sodium.impl.block_context.ChunkBuildBuffersExt;
import net.coderbot.iris.compat.sodium.impl.block_context.ContextAwareVertexWriter;
import net.minecraft.block.state.IBlockState;

/**
 * Associates the block context holder with the chunk build buffers, allowing {@link MixinChunkRenderRebuildTask} to pass
 * data to {@link ContextAwareVertexWriter}.
 */
@Mixin(ChunkBuildBuffers.class)
public class MixinChunkBuildBuffers implements ChunkBuildBuffersExt {
	@Unique
	private BlockContextHolder contextHolder;

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void iris$onConstruct(ChunkVertexType vertexType, BlockRenderPassManager renderPassManager, CallbackInfo ci) {
		Object2IntMap<IBlockState> blockStateIds = BlockRenderingSettings.INSTANCE.getBlockStateIds();

		if (blockStateIds != null) {
			this.contextHolder = new BlockContextHolder(blockStateIds);
		} else {
			this.contextHolder = new BlockContextHolder();
		}
	}

	@Redirect(method = "init", remap = false, at = @At(value = "INVOKE",
			target = "org/embeddedt/embeddium/client/model/vertex/type/ChunkVertexType.createBufferWriter(" +
				"Lorg/embeddedt/embeddium/client/model/vertex/buffer/VertexBufferView;" +
				"Z" +
			")Lorg/embeddedt/embeddium/client/model/vertex/VertexSink;", remap = false))
	private VertexSink iris$redirectWriterCreation(ChunkVertexType vertexType,
												   VertexBufferView buffer, boolean direct) {
		VertexSink sink = vertexType.createBufferWriter(buffer, direct);

		if (sink instanceof ContextAwareVertexWriter) {
			((ContextAwareVertexWriter) sink).iris$setContextHolder(contextHolder);
		}

		return sink;
	}

	@Override
	public void iris$setLocalPos(int localPosX, int localPosY, int localPosZ) {
		this.contextHolder.setLocalPos(localPosX, localPosY, localPosZ);
	}

	@Override
	public void iris$setMaterialId(IBlockState state, short renderType) {
		this.contextHolder.set(state, renderType);
	}

	@Override
	public void iris$resetBlockContext() {
		this.contextHolder.reset();
	}
}
