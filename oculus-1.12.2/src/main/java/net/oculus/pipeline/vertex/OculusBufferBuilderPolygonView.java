package net.oculus.pipeline.vertex;

import net.oculus.pipeline.vertex.geometry.QuadView;

import java.nio.ByteBuffer;

public final class OculusBufferBuilderPolygonView implements QuadView {
    private ByteBuffer buffer;
    private int startVertex;
    private int stride;
    private int positionOffset;
    private int uOffset;
    private int vOffset;

    public void setup(ByteBuffer buffer, int startVertex, int stride, int positionOffset, int uOffset, int vOffset) {
        this.buffer = buffer;
        this.startVertex = startVertex;
        this.stride = stride;
        this.positionOffset = positionOffset;
        this.uOffset = uOffset;
        this.vOffset = vOffset;
    }

    @Override
    public float x(int index) {
        return buffer.getFloat(vertexBase(index) + positionOffset);
    }

    @Override
    public float y(int index) {
        return buffer.getFloat(vertexBase(index) + positionOffset + 4);
    }

    @Override
    public float z(int index) {
        return buffer.getFloat(vertexBase(index) + positionOffset + 8);
    }

    @Override
    public float u(int index) {
        return buffer.getFloat(vertexBase(index) + uOffset);
    }

    @Override
    public float v(int index) {
        return buffer.getFloat(vertexBase(index) + vOffset);
    }

    private int vertexBase(int index) {
        return (startVertex + index) * stride;
    }
}
