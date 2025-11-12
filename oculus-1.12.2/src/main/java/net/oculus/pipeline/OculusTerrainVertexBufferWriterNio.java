package net.oculus.pipeline;

import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexUtil;
import net.oculus.pipeline.math.Vector3f;
import net.oculus.pipeline.vertex.OculusExtendedDataHelper;
import net.oculus.pipeline.vertex.OculusNormalHelper;

import java.nio.ByteBuffer;

import static net.oculus.pipeline.OculusTerrainVertexType.STRIDE;

/**
 * Buffered vertex writer that targets Java NIO buffers while producing Oculus-formatted terrain vertices.
 */
public final class OculusTerrainVertexBufferWriterNio extends VertexBufferWriterNio implements ModelVertexSink, ContextAwareVertexWriter {
    private final OculusQuadViewTerrain.NioView quadView = new OculusQuadViewTerrain.NioView();
    private final Vector3f normal = new Vector3f();

    private BlockContextHolder contextHolder;

    private int vertexCount;
    private float uSum;
    private float vSum;

    public OculusTerrainVertexBufferWriterNio(VertexBufferView backingBuffer) {
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
        ByteBuffer buffer = this.byteBuffer;
        int offset = this.writeOffset;

        vertexCount++;

        buffer.putShort(offset, x);
        buffer.putShort(offset + 2, y);
        buffer.putShort(offset + 4, z);
        buffer.putInt(offset + 8, color);
        buffer.putShort(offset + 12, u);
        buffer.putShort(offset + 14, v);
        buffer.putShort(offset + 16, (short) (light & 0xFFFF));
        buffer.putShort(offset + 18, (short) ((light >> 16) & 0xFFFF));
        buffer.putShort(offset + 36, materialId);
        buffer.putShort(offset + 38, renderType);
        buffer.putInt(offset + 40, packedMidBlock);

        if (vertexCount == 4) {
            vertexCount = 0;

            float midU = uSum * 0.25f;
            float midV = vSum * 0.25f;

            propagateFloat(buffer, offset, midU, 20);
            propagateFloat(buffer, offset, midV, 24);

            uSum = 0.0f;
            vSum = 0.0f;

            quadView.setup(buffer, offset, STRIDE);
            OculusNormalHelper.computeFaceNormal(normal, quadView);
            int packedNormal = OculusNormalHelper.packNormal(normal, 0.0f);
            propagateInt(buffer, offset, packedNormal, 32);

            int tangent = OculusNormalHelper.computeTangent(normal.x, normal.y, normal.z, quadView);
            propagateInt(buffer, offset, tangent, 28);
        }

        this.advance();
    }

    private static void propagateFloat(ByteBuffer buffer, int offset, float value, int relativeOffset) {
        buffer.putFloat(offset + relativeOffset, value);
        buffer.putFloat(offset + relativeOffset - STRIDE, value);
        buffer.putFloat(offset + relativeOffset - STRIDE * 2, value);
        buffer.putFloat(offset + relativeOffset - STRIDE * 3, value);
    }

    private static void propagateInt(ByteBuffer buffer, int offset, int value, int relativeOffset) {
        buffer.putInt(offset + relativeOffset, value);
        buffer.putInt(offset + relativeOffset - STRIDE, value);
        buffer.putInt(offset + relativeOffset - STRIDE * 2, value);
        buffer.putInt(offset + relativeOffset - STRIDE * 3, value);
    }

    @Override
    public void setContextHolder(BlockContextHolder holder) {
        this.contextHolder = holder;
    }
}
