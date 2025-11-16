package net.coderbot.iris.mixin.shadows;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

@Mixin(LevelRenderer.RenderChunkInfo.class)
public interface ChunkInfoAccessor {
	@Accessor("chunk")
	ChunkRenderDispatcher.RenderChunk getChunk();
}
