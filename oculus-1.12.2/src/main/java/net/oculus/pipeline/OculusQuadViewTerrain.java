package net.oculus.pipeline;

import net.oculus.pipeline.vertex.geometry.QuadView;

import java.nio.ByteBuffer;

/**
 * Provides read-only access to the most recently written quad inside the chunk vertex buffer.
 */
abstract class OculusQuadViewTerrain implements QuadView {
    private static final int POSITION_X_OFFSET = 0;
    private static final int POSITION_Y_OFFSET = 4;
    private static final int POSITION_Z_OFFSET = 8;
    private static final int UV_U_OFFSET = 16;
    private static final int UV_V_OFFSET = 20;

    int pointer;
    int stride;

    @Override
    public float x(int index) {
        return readFloat(vertexOffset(index) + POSITION_X_OFFSET);
    }

    @Override
    public float y(int index) {
        return readFloat(vertexOffset(index) + POSITION_Y_OFFSET);
    }

    @Override
    public float z(int index) {
        return readFloat(vertexOffset(index) + POSITION_Z_OFFSET);
    }

    @Override
    public float u(int index) {
        return readFloat(vertexOffset(index) + UV_U_OFFSET);
    }

    @Override
    public float v(int index) {
        return readFloat(vertexOffset(index) + UV_V_OFFSET);
    }

    private int vertexOffset(int index) {
        return this.pointer - this.stride * (3 - index);
    }

    protected abstract float readFloat(int address);

    static final class NioView extends OculusQuadViewTerrain {
        private ByteBuffer buffer;

        void setup(ByteBuffer buffer, int pointer, int stride) {
            this.buffer = buffer;
            this.pointer = pointer;
            this.stride = stride;
        }

        @Override
        protected float readFloat(int address) {
            return this.buffer.getFloat(address);
        }
    }
}
