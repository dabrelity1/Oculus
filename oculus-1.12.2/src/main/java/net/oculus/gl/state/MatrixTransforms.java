package net.oculus.gl.state;

import net.minecraft.util.math.MathHelper;

/**
 * Helper utilities for creating common transformation matrices and applying them to vectors.
 */
public final class MatrixTransforms {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private MatrixTransforms() {
    }

    public static void rotationX(float degrees, float[] target) {
        MatrixMath.setIdentity(target);
        float radians = degrees * DEG_TO_RAD;
        float cos = MathHelper.cos(radians);
        float sin = MathHelper.sin(radians);
        target[5] = cos;
        target[6] = sin;
        target[9] = -sin;
        target[10] = cos;
    }

    public static void rotationY(float degrees, float[] target) {
        MatrixMath.setIdentity(target);
        float radians = degrees * DEG_TO_RAD;
        float cos = MathHelper.cos(radians);
        float sin = MathHelper.sin(radians);
        target[0] = cos;
        target[2] = -sin;
        target[8] = sin;
        target[10] = cos;
    }

    public static void rotationZ(float degrees, float[] target) {
        MatrixMath.setIdentity(target);
        float radians = degrees * DEG_TO_RAD;
        float cos = MathHelper.cos(radians);
        float sin = MathHelper.sin(radians);
        target[0] = cos;
        target[1] = sin;
        target[4] = -sin;
        target[5] = cos;
    }

    public static void translation(float x, float y, float z, float[] target) {
        MatrixMath.setIdentity(target);
        target[12] = x;
        target[13] = y;
        target[14] = z;
    }

    public static void transform(float[] matrix, float x, float y, float z, float w, float[] dest) {
        dest[0] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12] * w;
        dest[1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13] * w;
        dest[2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14] * w;
    }
}
