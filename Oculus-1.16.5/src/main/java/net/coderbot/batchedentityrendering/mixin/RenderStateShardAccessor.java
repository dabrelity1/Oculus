package net.coderbot.batchedentityrendering.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.RenderStateShard;

@Mixin(RenderStateShard.class)
public interface RenderStateShardAccessor {
	@Accessor("NO_TRANSPARENCY")
	static RenderStateShard.TransparencyStateShard getNO_TRANSPARENCY() {
		throw new AssertionError();
	}

	@Accessor("GLINT_TRANSPARENCY")
	static RenderStateShard.TransparencyStateShard getGLINT_TRANSPARENCY() {
		throw new AssertionError();
	}

	@Accessor("CRUMBLING_TRANSPARENCY")
	static RenderStateShard.TransparencyStateShard getCRUMBLING_TRANSPARENCY() {
		throw new AssertionError();
	}
}
