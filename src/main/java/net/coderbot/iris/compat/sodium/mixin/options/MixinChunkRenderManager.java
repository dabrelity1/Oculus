package net.coderbot.iris.compat.sodium.mixin.options;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.embeddedt.embeddium.client.gui.SodiumGameOptions;
import org.embeddedt.embeddium.client.render.chunk.ChunkRenderManager;
import net.coderbot.iris.Iris;

/**
 * Disables fog occlusion when a shader pack is enabled, since shaders are not guaranteed to actually implement fog.
 */
@Mixin(ChunkRenderManager.class)
public class MixinChunkRenderManager {
	@Redirect(method = "setup", remap = false,
			at = @At(value = "FIELD",
				target = "org/embeddedt/embeddium/client/gui/SodiumGameOptions$AdvancedSettings.useFogOcclusion : Z",
				remap = false))
	private boolean iris$disableFogOcclusion(SodiumGameOptions.AdvancedSettings settings) {
		if (Iris.getCurrentPack().isPresent()) {
			return false;
		} else {
			return settings.useFogOcclusion;
		}
	}
}
