package net.oculus.vendored.joml;

/**
 * Small immutable vector used for parsed directive values.
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Vector4f)) {
            return false;
        }
        Vector4f other = (Vector4f) object;
        return Float.floatToIntBits(w) == Float.floatToIntBits(other.w)
            && Float.floatToIntBits(x) == Float.floatToIntBits(other.x)
            && Float.floatToIntBits(y) == Float.floatToIntBits(other.y)
            && Float.floatToIntBits(z) == Float.floatToIntBits(other.z);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Float.floatToIntBits(w);
        result = 31 * result + Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(z);
        return result;
    }
}
