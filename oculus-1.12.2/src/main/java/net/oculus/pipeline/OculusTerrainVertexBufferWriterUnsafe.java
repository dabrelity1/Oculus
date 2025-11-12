package net.oculus.pipeline;

import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexUtil;
import net.oculus.pipeline.math.Vector3f;
import net.oculus.pipeline.vertex.OculusExtendedDataHelper;
import net.oculus.pipeline.vertex.OculusNormalHelper;

import org.lwjgl.system.MemoryUtil;

import static net.oculus.pipeline.OculusTerrainVertexType.STRIDE;

/**
 * Direct-memory variant of the Oculus terrain vertex writer. Mirrors the NIO implementation
 * but leverages LWJGL's unsafe memory helpers when direct buffers are available.
 */
public final class OculusTerrainVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements ModelVertexSink, ContextAwareVertexWriter {
    private final OculusQuadViewTerrain.UnsafeView quadView = new OculusQuadViewTerrain.UnsafeView();
    private final Vector3f normal = new Vector3f();

    private BlockContextHolder contextHolder;
    private int vertexCount;
    private float uSum;
    private float vSum;

    public OculusTerrainVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, OculusTerrainVertexType.INSTANCE);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int light) {
        BlockContextHolder ctx = this.contextHolder;
        if (ctx == null) {
            ctx = new BlockContextHolder();
            this.contextHolder = ctx;
        }

        uSum += u;
        vSum += v;

        writeQuadInternal(
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(x),
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(y),
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(z),
                color,
                ModelVertexUtil.denormalizeVertexTextureFloatAsShort(u),
                ModelVertexUtil.denormalizeVertexTextureFloatAsShort(v),
                light,
                ctx.blockId,
                ctx.renderType,
                OculusExtendedDataHelper.computeMidBlock(x, y, z, ctx.localPosX, ctx.localPosY, ctx.localPosZ)
        );
    }

    private void writeQuadInternal(short x, short y, short z, int color, short u, short v, int light,
                                   short materialId, short renderType, int packedMidBlock) {
        long pointer = this.writePointer;

        vertexCount++;

        MemoryUtil.memPutShort(pointer, x);
        MemoryUtil.memPutShort(pointer + 2, y);
        MemoryUtil.memPutShort(pointer + 4, z);
        MemoryUtil.memPutInt(pointer + 8, color);
        MemoryUtil.memPutShort(pointer + 12, u);
        MemoryUtil.memPutShort(pointer + 14, v);
        MemoryUtil.memPutShort(pointer + 16, (short) (light & 0xFFFF));
        MemoryUtil.memPutShort(pointer + 18, (short) ((light >> 16) & 0xFFFF));
        MemoryUtil.memPutShort(pointer + 36, materialId);
        MemoryUtil.memPutShort(pointer + 38, renderType);
        MemoryUtil.memPutInt(pointer + 40, packedMidBlock);

        if (vertexCount == 4) {
            vertexCount = 0;

            float midU = uSum * 0.25f;
            float midV = vSum * 0.25f;

            propagateFloat(pointer, midU, 20);
            propagateFloat(pointer, midV, 24);

            uSum = 0.0f;
            vSum = 0.0f;

            quadView.setup(pointer, STRIDE);
            OculusNormalHelper.computeFaceNormal(normal, quadView);
            int packedNormal = OculusNormalHelper.packNormal(normal, 0.0f);
            propagateInt(pointer, packedNormal, 32);

            int tangent = OculusNormalHelper.computeTangent(normal.x, normal.y, normal.z, quadView);
            propagateInt(pointer, tangent, 28);
        }

        this.advance();
    }

    private static void propagateFloat(long pointer, float value, int relativeOffset) {
        MemoryUtil.memPutFloat(pointer + relativeOffset, value);
        MemoryUtil.memPutFloat(pointer + relativeOffset - STRIDE, value);
        MemoryUtil.memPutFloat(pointer + relativeOffset - STRIDE * 2, value);
        MemoryUtil.memPutFloat(pointer + relativeOffset - STRIDE * 3, value);
    }

    private static void propagateInt(long pointer, int value, int relativeOffset) {
        MemoryUtil.memPutInt(pointer + relativeOffset, value);
        MemoryUtil.memPutInt(pointer + relativeOffset - STRIDE, value);
        MemoryUtil.memPutInt(pointer + relativeOffset - STRIDE * 2, value);
        MemoryUtil.memPutInt(pointer + relativeOffset - STRIDE * 3, value);
    }

    @Override
    public void setContextHolder(BlockContextHolder holder) {
        this.contextHolder = holder;
    }
}
