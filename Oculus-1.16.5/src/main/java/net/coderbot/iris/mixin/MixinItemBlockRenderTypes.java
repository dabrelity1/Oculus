package net.coderbot.iris.mixin;

import java.util.Map;

import net.minecraftforge.registries.IRegistryDelegate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ItemBlockRenderTypes.class)
public class MixinItemBlockRenderTypes {
	@Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true)
	private static void iris$setCustomRenderType(BlockState arg, CallbackInfoReturnable<RenderType> cir) {
		Map<IRegistryDelegate<Block>, RenderType> idMap = BlockRenderingSettings.INSTANCE.getBlockTypeIds();
		if (idMap != null) {
			RenderType type = idMap.get(arg.getBlock().delegate);
			if (type != null) {
				cir.setReturnValue(type);
			}
		}
	}

	@Inject(method = "canRenderInLayer(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/RenderType;)Z", at = @At("HEAD"), cancellable = true)
	private static void iris$checkCustomRenderType(BlockState arg, RenderType type, CallbackInfoReturnable<Boolean> cir) {
		Map<IRegistryDelegate<Block>, RenderType> idMap = BlockRenderingSettings.INSTANCE.getBlockTypeIds();
		if (idMap != null) {
			RenderType irisType = idMap.get(arg.getBlock().delegate);
			if (irisType != null) {
				// If we have an override registered for this block, only allow rendering precisely that type
				cir.setReturnValue(type.equals(irisType));
			}
		}
	}
}
