package net.coderbot.iris.compat.sodium.mixin.vertex_format;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;

@Mixin(GlVertexAttributeFormat.class)
public interface GlVertexAttributeFormatAccessor {
	@Invoker(value = "<init>")
	static GlVertexAttributeFormat createGlVertexAttributeFormat(int glId, int size) {
		throw new AssertionError("accessor failure");
	}
}
