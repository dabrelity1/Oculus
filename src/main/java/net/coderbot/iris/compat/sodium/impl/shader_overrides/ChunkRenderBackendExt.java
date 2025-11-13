package net.coderbot.iris.compat.sodium.impl.shader_overrides;

import org.embeddedt.embeddium.client.render.chunk.passes.BlockRenderPass;

public interface ChunkRenderBackendExt {
	void iris$begin(BlockRenderPass pass);
}
