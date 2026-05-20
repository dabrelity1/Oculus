package net.oculus.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import me.jellysquid.mods.sodium.client.gl.attribute.BufferVertexFormat;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import net.oculus.pipeline.vertex.OculusExtendedDataHelper;
import net.oculus.pipeline.vertex.OculusNormalHelper;
import org.junit.Test;

public class OculusTerrainVertexBufferWriterNioTest {
    private static final int STRIDE = OculusTerrainVertexType.STRIDE;

    @Test
    public void writesRelictiumTerrainAttributesIntoDeclared52ByteLayout() {
        ByteBuffer storage = ByteBuffer.allocate(STRIDE * 4);
        TestVertexBufferView view = new TestVertexBufferView(storage);
        OculusTerrainVertexBufferWriterNio writer = new OculusTerrainVertexBufferWriterNio(view);
        BlockContextHolder context = new BlockContextHolder();
        context.blockId = 1234;
        context.renderType = OculusExtendedDataHelper.BLOCK_RENDER_TYPE;
        context.blockEmission = 12;
        context.setLocalPos(3, 4, 5);
        writer.setContextHolder(context);

        float[] xs = {3.0F, 4.0F, 4.0F, 3.0F};
        float[] ys = {4.0F, 4.0F, 5.0F, 5.0F};
        float[] zs = {5.0F, 5.0F, 5.0F, 5.0F};
        float[] us = {0.0F, 1.0F, 1.0F, 0.0F};
        float[] vs = {0.0F, 0.0F, 1.0F, 1.0F};
        int[] colors = {0x11223344, 0x55667788, 0x99AABBCC, 0xDDEEFF00};
        int[] lights = {0x00F000F0, 0x00110022, 0x00330044, 0x00550066};

        for (int i = 0; i < 4; i++) {
            writer.writeQuad(xs[i], ys[i], zs[i], colors[i], us[i], vs[i], lights[i]);
        }

        int expectedNormal = OculusNormalHelper.packNormal(0.0F, 0.0F, 1.0F, 0.0F);
        int firstTangent = storage.getInt(44);
        assertTrue("Expected propagated tangent data", firstTangent != 0);

        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * STRIDE;
            assertEquals(xs[vertex], storage.getFloat(base), 0.0F);
            assertEquals(ys[vertex], storage.getFloat(base + 4), 0.0F);
            assertEquals(zs[vertex], storage.getFloat(base + 8), 0.0F);
            assertEquals(colors[vertex], storage.getInt(base + 12));
            assertEquals(us[vertex], storage.getFloat(base + 16), 0.0F);
            assertEquals(vs[vertex], storage.getFloat(base + 20), 0.0F);
            assertEquals((short) (lights[vertex] & 0xFFFF), storage.getShort(base + 24));
            assertEquals((short) ((lights[vertex] >> 16) & 0xFFFF), storage.getShort(base + 26));
            assertPackedNormal(storage, base + 28, expectedNormal);
            assertEquals((short) 1234, storage.getShort(base + 32));
            assertEquals(OculusExtendedDataHelper.BLOCK_RENDER_TYPE, storage.getShort(base + 34));
            assertEquals(0.5F, storage.getFloat(base + 36), 0.0F);
            assertEquals(0.5F, storage.getFloat(base + 40), 0.0F);
            assertEquals(firstTangent, storage.getInt(base + 44));
            assertMidBlock(storage, base + 48,
                OculusExtendedDataHelper.computeMidBlock(xs[vertex], ys[vertex], zs[vertex], 3, 4, 5), 12);
        }
    }

    @Test
    public void missingContextUsesDefaultMaterialRenderTypeAndEmission() {
        ByteBuffer storage = ByteBuffer.allocate(STRIDE * 4);
        OculusTerrainVertexBufferWriterNio writer =
            new OculusTerrainVertexBufferWriterNio(new TestVertexBufferView(storage));

        writer.writeQuad(0.0F, 0.0F, 0.0F, -1, 0.0F, 0.0F, 0);
        writer.writeQuad(1.0F, 0.0F, 0.0F, -1, 1.0F, 0.0F, 0);
        writer.writeQuad(1.0F, 1.0F, 0.0F, -1, 1.0F, 1.0F, 0);
        writer.writeQuad(0.0F, 1.0F, 0.0F, -1, 0.0F, 1.0F, 0);

        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * STRIDE;
            assertEquals((short) -1, storage.getShort(base + 32));
            assertEquals((short) -1, storage.getShort(base + 34));
            assertEquals((byte) 0, storage.get(base + 51));
        }
    }

    private static void assertPackedNormal(ByteBuffer storage, int offset, int packedNormal) {
        assertEquals((byte) (packedNormal & 0xFF), storage.get(offset));
        assertEquals((byte) ((packedNormal >> 8) & 0xFF), storage.get(offset + 1));
        assertEquals((byte) ((packedNormal >> 16) & 0xFF), storage.get(offset + 2));
        assertEquals((byte) ((packedNormal >> 24) & 0xFF), storage.get(offset + 3));
    }

    private static void assertMidBlock(ByteBuffer storage, int offset, int packedMidBlock, int blockEmission) {
        assertEquals((byte) (packedMidBlock & 0xFF), storage.get(offset));
        assertEquals((byte) ((packedMidBlock >> 8) & 0xFF), storage.get(offset + 1));
        assertEquals((byte) ((packedMidBlock >> 16) & 0xFF), storage.get(offset + 2));
        assertEquals((byte) blockEmission, storage.get(offset + 3));
    }

    private static final class TestVertexBufferView implements VertexBufferView {
        private final ByteBuffer storage;

        private TestVertexBufferView(ByteBuffer storage) {
            this.storage = storage;
        }

        @Override
        public boolean ensureBufferCapacity(int bytes) {
            return bytes > storage.capacity();
        }

        @Override
        public ByteBuffer getDirectBuffer() {
            return storage;
        }

        @Override
        public int getWriterPosition() {
            return 0;
        }

        @Override
        public void flush(int vertexCount, BufferVertexFormat format) {
        }

        @Override
        public BufferVertexFormat getVertexFormat() {
            return OculusTerrainVertexType.INSTANCE.getBufferVertexFormat();
        }
    }
}
