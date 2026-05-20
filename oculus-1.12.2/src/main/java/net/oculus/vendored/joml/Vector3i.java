package net.oculus.vendored.joml;

/**
 * Minimal stand-in for JOML's {@code Vector3i}. Only the functionality required
 * by the ported shader metadata pipeline is implemented.
 */
public final class Vector3i {
    private final int x;
    private final int y;
    private final int z;

    public Vector3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }
}
