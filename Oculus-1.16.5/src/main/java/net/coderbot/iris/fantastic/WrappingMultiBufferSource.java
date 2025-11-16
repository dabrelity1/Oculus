package net.coderbot.iris.fantastic;

import java.util.function.Function;

import net.minecraft.client.renderer.RenderType;

public interface WrappingMultiBufferSource {
	void pushWrappingFunction(Function<RenderType, RenderType> wrappingFunction);
	void popWrappingFunction();
	void assertWrapStackEmpty();
}
