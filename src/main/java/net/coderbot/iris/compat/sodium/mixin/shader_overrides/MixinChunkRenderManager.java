package net.coderbot.iris.compat.sodium.mixin.shader_overrides;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.coderbot.iris.compat.sodium.impl.shader_overrides.ChunkRenderBackendExt;

@Mixin(ChunkRenderManager.class)
public class MixinChunkRenderManager {
	@Redirect(method = "renderLayer",
			at = @At(value = "INVOKE",
				target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderBackend;begin()V"))
	private void iris$backendBeginExt(ChunkRenderBackend<?> backend,
								   BlockRenderPass pass, double x, double y, double z) {
		if (backend instanceof ChunkRenderBackendExt) {
			((ChunkRenderBackendExt) backend).iris$begin(pass);
		} else {
			backend.begin();
		}
	}
}
