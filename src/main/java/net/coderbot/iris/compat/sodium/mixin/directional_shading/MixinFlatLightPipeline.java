package net.coderbot.iris.compat.sodium.mixin.directional_shading;

import me.jellysquid.mods.sodium.client.model.light.flat.FlatLightPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

@Mixin(FlatLightPipeline.class)
public class MixinFlatLightPipeline {
	@Redirect(method = "calculate", at = @At(value = "INVOKE",
			target = "net/minecraft/world/IBlockAccess.getBrightness (Lnet/minecraft/util/EnumFacing;Z)F"))
	private float iris$getBrightness(IBlockAccess level, EnumFacing direction, boolean shaded) {
		if (BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading()) {
			return 1.0F;
		} else {
			return level.getBrightness(direction, shaded);
		}
	}
}
