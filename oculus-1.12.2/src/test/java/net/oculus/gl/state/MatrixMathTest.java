package net.oculus.gl.state;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class MatrixMathTest {
    @Test
    public void transposeSupportsDistinctTarget() {
        float[] source = {
            0.0F, 1.0F, 2.0F, 3.0F,
            4.0F, 5.0F, 6.0F, 7.0F,
            8.0F, 9.0F, 10.0F, 11.0F,
            12.0F, 13.0F, 14.0F, 15.0F
        };
        float[] target = new float[16];

        MatrixMath.transpose(source, target);

        assertArrayEquals(new float[] {
            0.0F, 4.0F, 8.0F, 12.0F,
            1.0F, 5.0F, 9.0F, 13.0F,
            2.0F, 6.0F, 10.0F, 14.0F,
            3.0F, 7.0F, 11.0F, 15.0F
        }, target, 0.0F);
    }

    @Test
    public void transposeSupportsInPlaceTarget() {
        float[] matrix = {
            0.0F, 1.0F, 2.0F, 3.0F,
            4.0F, 5.0F, 6.0F, 7.0F,
            8.0F, 9.0F, 10.0F, 11.0F,
            12.0F, 13.0F, 14.0F, 15.0F
        };

        MatrixMath.transpose(matrix, matrix);

        assertArrayEquals(new float[] {
            0.0F, 4.0F, 8.0F, 12.0F,
            1.0F, 5.0F, 9.0F, 13.0F,
            2.0F, 6.0F, 10.0F, 14.0F,
            3.0F, 7.0F, 11.0F, 15.0F
        }, matrix, 0.0F);
    }
}
