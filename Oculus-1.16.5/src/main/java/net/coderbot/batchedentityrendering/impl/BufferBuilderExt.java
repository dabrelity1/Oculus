package net.coderbot.batchedentityrendering.impl;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.vertex.BufferBuilder;

public interface BufferBuilderExt {
    void setupBufferSlice(ByteBuffer buffer, BufferBuilder.DrawState drawState);
    void teardownBufferSlice();
    void splitStrip();
}
