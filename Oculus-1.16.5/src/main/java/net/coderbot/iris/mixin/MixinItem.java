package net.coderbot.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.irisshaders.iris.api.v0.item.IrisItemLightProvider;
import net.minecraft.world.item.Item;

@Mixin(Item.class)
public class MixinItem implements IrisItemLightProvider {
}
