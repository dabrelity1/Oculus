package net.oculus.vendored.joml;

/**
 * Minimal stand-in for JOML's {@code Vector2f}. Only the behaviour exercised by
 * the shader metadata scaffolding is implemented.
 */
public final class Vector2f {
    private final float x;
    private final float y;

    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }
}
