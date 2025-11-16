package net.coderbot.batchedentityrendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
	@Accessor("transparencyState")
	RenderStateShard.TransparencyStateShard getTransparency();
}
