package net.oculus.pipeline;

import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import net.oculus.pipeline.math.Vector3f;
import net.oculus.pipeline.vertex.OculusExtendedDataHelper;
import net.oculus.pipeline.vertex.OculusNormalHelper;

import java.nio.ByteBuffer;

import static net.oculus.pipeline.OculusTerrainVertexType.STRIDE;

/**
 * Buffered vertex writer that targets Java NIO buffers while producing Oculus-formatted terrain vertices.
 */
public final class OculusTerrainVertexBufferWriterNio extends VertexBufferWriterNio implements ModelVertexSink, ContextAwareVertexWriter {
    private static final int OFFSET_POSITION_X = 0;
    private static final int OFFSET_POSITION_Y = 4;
    private static final int OFFSET_POSITION_Z = 8;
    private static final int OFFSET_COLOR = 12;
    private static final int OFFSET_U = 16;
    private static final int OFFSET_V = 20;
    private static final int OFFSET_LIGHT_BLOCK = 24;
    private static final int OFFSET_LIGHT_SKY = 26;
    private static final int OFFSET_NORMAL_X = 28;
    private static final int OFFSET_NORMAL_Y = 29;
    private static final int OFFSET_NORMAL_Z = 30;
    private static final int OFFSET_NORMAL_W = 31;
    private static final int OFFSET_MATERIAL_ID = 32;
    private static final int OFFSET_RENDER_TYPE = 34;
    private static final int OFFSET_MID_U = 36;
    private static final int OFFSET_MID_V = 40;
    private static final int OFFSET_TANGENT = 44;
    private static final int OFFSET_MID_BLOCK = 48;
    private static final int OFFSET_FINAL_PADDING = 51;

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

        this.uSum += u;
        this.vSum += v;

        writeQuadInternal(
                x,
                y,
                z,
                color,
                u,
                v,
                light,
                ctx.blockId,
                ctx.renderType,
                OculusExtendedDataHelper.computeMidBlock(x, y, z, ctx.localPosX, ctx.localPosY, ctx.localPosZ)
        );
    }

    private void writeQuadInternal(float x, float y, float z, int color, float u, float v, int light,
                                   short materialId, short renderType, int packedMidBlock) {
        ByteBuffer buffer = this.byteBuffer;
        int offset = this.writeOffset;

        this.vertexCount++;

        buffer.putFloat(offset + OFFSET_POSITION_X, x);
        buffer.putFloat(offset + OFFSET_POSITION_Y, y);
        buffer.putFloat(offset + OFFSET_POSITION_Z, z);

        buffer.putInt(offset + OFFSET_COLOR, color);

        buffer.putFloat(offset + OFFSET_U, u);
        buffer.putFloat(offset + OFFSET_V, v);

        buffer.putShort(offset + OFFSET_LIGHT_BLOCK, (short) (light & 0xFFFF));
        buffer.putShort(offset + OFFSET_LIGHT_SKY, (short) ((light >> 16) & 0xFFFF));

        buffer.putShort(offset + OFFSET_MATERIAL_ID, materialId);
        buffer.putShort(offset + OFFSET_RENDER_TYPE, renderType);

        writeMidBlock(buffer, offset, packedMidBlock);

        if (this.vertexCount == 4) {
            this.vertexCount = 0;

            float midU = this.uSum * 0.25f;
            float midV = this.vSum * 0.25f;

            propagateFloat(buffer, offset, midU, OFFSET_MID_U);
            propagateFloat(buffer, offset, midV, OFFSET_MID_V);

            this.uSum = 0.0f;
            this.vSum = 0.0f;

            this.quadView.setup(buffer, offset, STRIDE);
            OculusNormalHelper.computeFaceNormal(this.normal, this.quadView);
            int packedNormal = OculusNormalHelper.packNormal(this.normal, 0.0f);
            propagatePackedNormal(buffer, offset, packedNormal);

            int tangent = OculusNormalHelper.computeTangent(this.normal.x, this.normal.y, this.normal.z, this.quadView);
            propagateInt(buffer, offset, tangent, OFFSET_TANGENT);
        }

        this.advance();
    }

    private static void writeMidBlock(ByteBuffer buffer, int offset, int packedMidBlock) {
        buffer.put(offset + OFFSET_MID_BLOCK, (byte) (packedMidBlock & 0xFF));
        buffer.put(offset + OFFSET_MID_BLOCK + 1, (byte) ((packedMidBlock >> 8) & 0xFF));
        buffer.put(offset + OFFSET_MID_BLOCK + 2, (byte) ((packedMidBlock >> 16) & 0xFF));
        buffer.put(offset + OFFSET_FINAL_PADDING, (byte) 0);
    }

    private static void propagateFloat(ByteBuffer buffer, int offset, float value, int relativeOffset) {
        for (int i = 0; i < 4; i++) {
            int base = offset - (STRIDE * i);
            buffer.putFloat(base + relativeOffset, value);
        }
    }

    private static void propagateInt(ByteBuffer buffer, int offset, int value, int relativeOffset) {
        for (int i = 0; i < 4; i++) {
            int base = offset - (STRIDE * i);
            buffer.putInt(base + relativeOffset, value);
        }
    }

    private static void propagatePackedNormal(ByteBuffer buffer, int offset, int packedNormal) {
        byte nx = (byte) (packedNormal & 0xFF);
        byte ny = (byte) ((packedNormal >> 8) & 0xFF);
        byte nz = (byte) ((packedNormal >> 16) & 0xFF);
        byte nw = (byte) ((packedNormal >> 24) & 0xFF);

        for (int i = 0; i < 4; i++) {
            int base = offset - (STRIDE * i);
            buffer.put(base + OFFSET_NORMAL_X, nx);
            buffer.put(base + OFFSET_NORMAL_Y, ny);
            buffer.put(base + OFFSET_NORMAL_Z, nz);
            buffer.put(base + OFFSET_NORMAL_W, nw);
        }
    }

    @Override
    public void setContextHolder(BlockContextHolder holder) {
        this.contextHolder = holder;
    }
}
