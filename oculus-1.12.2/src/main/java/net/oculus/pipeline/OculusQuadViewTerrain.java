package net.oculus.pipeline;

import net.oculus.pipeline.vertex.geometry.QuadView;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryUtil;

/**
 * Provides read-only access to the most recently written quad inside the chunk vertex buffer.
 */
abstract class OculusQuadViewTerrain implements QuadView {
    long pointer;
    int stride;

    @Override
    public float x(int index) {
        return normalizePosition(getShort(pointer - stride * (3L - index)));
    }

    @Override
    public float y(int index) {
        return normalizePosition(getShort(pointer + 2 - stride * (3L - index)));
    }

    @Override
    public float z(int index) {
        return normalizePosition(getShort(pointer + 4 - stride * (3L - index)));
    }

    @Override
    public float u(int index) {
        return normalizeTexture(getShort(pointer + 12 - stride * (3L - index)));
    }

    @Override
    public float v(int index) {
        return normalizeTexture(getShort(pointer + 14 - stride * (3L - index)));
    }

    abstract short getShort(long address);

    private static float normalizePosition(short value) {
        return (value & 0xFFFF) * (1.0f / 65535.0f);
    }

    private static float normalizeTexture(short value) {
        return (value & 0xFFFF) * (1.0f / 32768.0f);
    }

    static final class UnsafeView extends OculusQuadViewTerrain {
        void setup(long pointer, int stride) {
            this.pointer = pointer;
            this.stride = stride;
        }

        @Override
        short getShort(long address) {
            return MemoryUtil.memGetShort(address);
        }
    }

    static final class NioView extends OculusQuadViewTerrain {
        private ByteBuffer buffer;

        void setup(ByteBuffer buffer, int pointer, int stride) {
            this.buffer = buffer;
            this.pointer = pointer;
            this.stride = stride;
        }

        @Override
        short getShort(long address) {
            return buffer.getShort((int) address);
        }
    }
}
