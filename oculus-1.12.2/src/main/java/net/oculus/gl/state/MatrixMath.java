package net.oculus.gl.state;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Utility helpers for 4x4 matrix math using column-major float arrays.
 */
public final class MatrixMath {
    private MatrixMath() {
    }

    public static float[] createIdentity() {
        float[] matrix = new float[16];
        setIdentity(matrix);
        return matrix;
    }

    public static void setIdentity(float[] matrix) {
        Arrays.fill(matrix, 0.0F);
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1.0F;
    }

    public static void copy(float[] source, float[] target) {
        System.arraycopy(source, 0, target, 0, 16);
    }

    public static void copyFromBuffer(FloatBuffer source, float[] target) {
        if (source == null) {
            return;
        }

        ((Buffer) source).rewind();
        source.get(target, 0, 16);
        ((Buffer) source).rewind();
    }

    public static boolean invert(float[] source, float[] target) {
        float[] inv = new float[16];

        inv[0] = source[5] * source[10] * source[15] -
                 source[5] * source[11] * source[14] -
                 source[9] * source[6] * source[15] +
                 source[9] * source[7] * source[14] +
                 source[13] * source[6] * source[11] -
                 source[13] * source[7] * source[10];

        inv[4] = -source[4] * source[10] * source[15] +
                  source[4] * source[11] * source[14] +
                  source[8] * source[6] * source[15] -
                  source[8] * source[7] * source[14] -
                  source[12] * source[6] * source[11] +
                  source[12] * source[7] * source[10];

        inv[8] = source[4] * source[9] * source[15] -
                 source[4] * source[11] * source[13] -
                 source[8] * source[5] * source[15] +
                 source[8] * source[7] * source[13] +
                 source[12] * source[5] * source[11] -
                 source[12] * source[7] * source[9];

        inv[12] = -source[4] * source[9] * source[14] +
                   source[4] * source[10] * source[13] +
                   source[8] * source[5] * source[14] -
                   source[8] * source[6] * source[13] -
                   source[12] * source[5] * source[10] +
                   source[12] * source[6] * source[9];

        inv[1] = -source[1] * source[10] * source[15] +
                  source[1] * source[11] * source[14] +
                  source[9] * source[2] * source[15] -
                  source[9] * source[3] * source[14] -
                  source[13] * source[2] * source[11] +
                  source[13] * source[3] * source[10];

        inv[5] = source[0] * source[10] * source[15] -
                 source[0] * source[11] * source[14] -
                 source[8] * source[2] * source[15] +
                 source[8] * source[3] * source[14] +
                 source[12] * source[2] * source[11] -
                 source[12] * source[3] * source[10];

        inv[9] = -source[0] * source[9] * source[15] +
                  source[0] * source[11] * source[13] +
                  source[8] * source[1] * source[15] -
                  source[8] * source[3] * source[13] -
                  source[12] * source[1] * source[11] +
                  source[12] * source[3] * source[9];

        inv[13] = source[0] * source[9] * source[14] -
                  source[0] * source[10] * source[13] -
                  source[8] * source[1] * source[14] +
                  source[8] * source[2] * source[13] +
                  source[12] * source[1] * source[10] -
                  source[12] * source[2] * source[9];

        inv[2] = source[1] * source[6] * source[15] -
                 source[1] * source[7] * source[14] -
                 source[5] * source[2] * source[15] +
                 source[5] * source[3] * source[14] +
                 source[13] * source[2] * source[7] -
                 source[13] * source[3] * source[6];

        inv[6] = -source[0] * source[6] * source[15] +
                  source[0] * source[7] * source[14] +
                  source[4] * source[2] * source[15] -
                  source[4] * source[3] * source[14] -
                  source[12] * source[2] * source[7] +
                  source[12] * source[3] * source[6];

        inv[10] = source[0] * source[5] * source[15] -
                  source[0] * source[7] * source[13] -
                  source[4] * source[1] * source[15] +
                  source[4] * source[3] * source[13] +
                  source[12] * source[1] * source[7] -
                  source[12] * source[3] * source[5];

        inv[14] = -source[0] * source[5] * source[14] +
                   source[0] * source[6] * source[13] +
                   source[4] * source[1] * source[14] -
                   source[4] * source[2] * source[13] -
                   source[12] * source[1] * source[6] +
                   source[12] * source[2] * source[5];

        inv[3] = -source[1] * source[6] * source[11] +
                  source[1] * source[7] * source[10] +
                  source[5] * source[2] * source[11] -
                  source[5] * source[3] * source[10] -
                  source[9] * source[2] * source[7] +
                  source[9] * source[3] * source[6];

        inv[7] = source[0] * source[6] * source[11] -
                 source[0] * source[7] * source[10] -
                 source[4] * source[2] * source[11] +
                 source[4] * source[3] * source[10] +
                 source[8] * source[2] * source[7] -
                 source[8] * source[3] * source[6];

        inv[11] = -source[0] * source[5] * source[11] +
                   source[0] * source[7] * source[9] +
                   source[4] * source[1] * source[11] -
                   source[4] * source[3] * source[9] -
                   source[8] * source[1] * source[7] +
                   source[8] * source[3] * source[5];

        inv[15] = source[0] * source[5] * source[10] -
                  source[0] * source[6] * source[9] -
                  source[4] * source[1] * source[10] +
                  source[4] * source[2] * source[9] +
                  source[8] * source[1] * source[6] -
                  source[8] * source[2] * source[5];

        float det = source[0] * inv[0] + source[1] * inv[4] + source[2] * inv[8] + source[3] * inv[12];

        if (Math.abs(det) < 1.0e-6F) {
            setIdentity(target);
            return false;
        }

        det = 1.0F / det;
        for (int i = 0; i < 16; i++) {
            target[i] = inv[i] * det;
        }

        return true;
    }

    public static void multiply(float[] left, float[] right, float[] target) {
        float[] result = (target == left || target == right) ? new float[16] : target;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0.0F;
                for (int k = 0; k < 4; k++) {
                    sum += left[row + k * 4] * right[k + col * 4];
                }
                result[row + col * 4] = sum;
            }
        }

        if (result != target) {
            System.arraycopy(result, 0, target, 0, 16);
        }
    }
}
