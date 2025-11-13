package net.oculus.vendored.joml;

/**
 * Minimal stand-in for JOML's {@code Vector4f}, used solely for directive
 * placeholders.
 */
public final class Vector4f {
    private final float x;
    private final float y;
    private final float z;
    private final float w;

    public Vector4f(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    public float w() {
        return w;
    }
}
