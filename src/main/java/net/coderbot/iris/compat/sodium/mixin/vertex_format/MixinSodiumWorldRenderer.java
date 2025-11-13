package net.coderbot.iris.compat.sodium.mixin.vertex_format;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import org.embeddedt.embeddium.client.model.vertex.type.ChunkVertexType;
import org.embeddedt.embeddium.client.render.SodiumWorldRenderer;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.compat.sodium.impl.vertex_format.IrisModelVertexFormats;

/**
 * Enables usage of the extended vertex format needed by Iris.
 */
@Mixin(SodiumWorldRenderer.class)
public class MixinSodiumWorldRenderer {
	@ModifyArg(method = "initRenderer()V", remap = false,
			at = @At(value = "INVOKE", remap = false,
					target = "org/embeddedt/embeddium/client/render/SodiumWorldRenderer.createChunkRenderBackend (" +
						"Lorg/embeddedt/embeddium/client/gl/device/RenderDevice;" +
						"Lorg/embeddedt/embeddium/client/gui/SodiumGameOptions;" +
						"Lorg/embeddedt/embeddium/client/model/vertex/type/ChunkVertexType;" +
					")Lorg/embeddedt/embeddium/client/render/chunk/ChunkRenderBackend;"))
	private ChunkVertexType iris$overrideVertexType(ChunkVertexType vertexType) {
		return BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()
			? IrisModelVertexFormats.MODEL_VERTEX_XHFP : vertexType;
	}
}
