package net.coderbot.batchedentityrendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.RenderType;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {
	@Accessor("sortOnUpload")
	boolean shouldSortOnUpload();
}
