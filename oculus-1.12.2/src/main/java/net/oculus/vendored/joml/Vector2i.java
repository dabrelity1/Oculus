package net.oculus.vendored.joml;

/**
 * Minimal integer vector used by the shader metadata port. Only the accessors
 * required by directive handling are implemented.
 */
public final class Vector2i {
    private final int x;
    private final int y;

    public Vector2i(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }
}
