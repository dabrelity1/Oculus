package net.coderbot.iris.compat.sodium.impl.shader_overrides;

import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;

public interface ChunkRenderBackendExt {
	void iris$begin(BlockRenderPass pass);
}
