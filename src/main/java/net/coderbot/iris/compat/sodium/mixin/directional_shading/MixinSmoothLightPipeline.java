package net.coderbot.iris.compat.sodium.mixin.directional_shading;

import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.util.EnumFacing;

@Mixin(SmoothLightPipeline.class)
public class MixinSmoothLightPipeline {
	@Inject(method = "applySidedBrightness", at = @At("HEAD"), cancellable = true, remap = false)
	private void iris$disableDirectionalShading(QuadLightData out, EnumFacing face, boolean shade, CallbackInfo ci) {
		if (BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading()) {
			ci.cancel();
		}
	}
}
