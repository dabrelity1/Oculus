package net.oculus.pipeline;

import me.jellysquid.mods.sodium.client.model.vertex.fallback.VertexWriterFallback;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.minecraft.client.renderer.BufferBuilder;
import net.oculus.pipeline.vertex.OculusBlockSensitiveBufferBuilder;

public final class OculusTerrainVertexWriterFallback extends VertexWriterFallback implements ModelVertexSink, ContextAwareVertexWriter {
    private BlockContextHolder contextHolder;

    public OculusTerrainVertexWriterFallback(BufferBuilder consumer) {
        super(consumer);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int light) {
        boolean pushedContext = false;
        if (consumer instanceof OculusBlockSensitiveBufferBuilder && this.contextHolder != null) {
            BlockContextHolder context = this.contextHolder;
            ((OculusBlockSensitiveBufferBuilder) consumer).oculus$beginBlock(context.blockId, context.renderType,
                context.blockEmission, context.localPosX, context.localPosY, context.localPosZ);
            pushedContext = true;
        }

        try {
            consumer.pos(x, y, z);
            consumer.color(
                ColorABGR.unpackRed(color),
                ColorABGR.unpackGreen(color),
                ColorABGR.unpackBlue(color),
                ColorABGR.unpackAlpha(color));
            consumer.tex(u, v);
            consumer.lightmap(light & 0xFFFF, (light >>> 16) & 0xFFFF);
            consumer.endVertex();
        } finally {
            if (pushedContext) {
                ((OculusBlockSensitiveBufferBuilder) consumer).oculus$endBlock();
            }
        }
    }

    @Override
    public void setContextHolder(BlockContextHolder holder) {
        this.contextHolder = holder;
    }

    public BlockContextHolder getContextHolder() {
        return contextHolder;
    }
}
