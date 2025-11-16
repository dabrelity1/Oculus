package net.coderbot.batchedentityrendering.mixin;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.coderbot.batchedentityrendering.impl.MemoryTrackingBuffer;

@Mixin(BufferBuilder.class)
public class MixinBufferBuilder implements MemoryTrackingBuffer {
	@Shadow
	private ByteBuffer buffer;

	@Override
	public int getAllocatedSize() {
		return buffer.capacity();
	}

	@Override
	public int getUsedSize() {
		return buffer.position();
	}
}
