package net.oculus.uniforms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShadowUniformsMatrixTest {
    private static final float EPSILON = 0.000001F;

    @Test
    public void orthographicProjectionMatchesIrisShadowMatrices() {
        float[] matrix = new float[16];

        ShadowUniforms.createOrthographic(32.0F, matrix);

        assertArrayEquals(new float[] {
            0.03125F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.03125F, 0.0F, 0.0F,
            0.0F, 0.0F, -0.007814026F, 0.0F,
            0.0F, 0.0F, -1.0003908F, 1.0F
        }, matrix, EPSILON);
    }

    @Test
    public void orthographicProjectionUsesRawShadowDistanceDirective() {
        float[] matrix = new float[16];

        ShadowUniforms.createOrthographic(-32.0F, matrix);

        assertEquals(-0.03125F, matrix[0], EPSILON);
        assertEquals(-0.03125F, matrix[5], EPSILON);
    }

    @Test
    public void orthographicProjectionDoesNotClampZeroShadowDistance() {
        float[] matrix = new float[16];

        ShadowUniforms.createOrthographic(0.0F, matrix);

        assertTrue(Float.isInfinite(matrix[0]));
        assertTrue(Float.isInfinite(matrix[5]));
    }

    @Test
    public void perspectiveProjectionMatchesIrisShadowMatrices() {
        float[] matrix = new float[16];

        ShadowUniforms.createPerspective(90.0F, matrix);

        assertArrayEquals(new float[] {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, -1.0003908F, -1.0F,
            0.0F, 0.0F, -0.10001954F, 1.0F
        }, matrix, EPSILON);
    }

    @Test
    public void renderProjectionUsesPerspectiveWhenShadowFovIsPresent() {
        float[] matrix = new float[16];

        ShadowUniforms.createRenderProjection(32.0F, 90.0F, matrix);

        assertArrayEquals(new float[] {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, -1.0003908F, -1.0F,
            0.0F, 0.0F, -0.10001954F, 1.0F
        }, matrix, EPSILON);
    }

    @Test
    public void renderProjectionFallsBackToOrthographicWhenShadowFovIsAbsent() {
        float[] matrix = new float[16];

        ShadowUniforms.createRenderProjection(32.0F, null, matrix);

        assertArrayEquals(new float[] {
            0.03125F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.03125F, 0.0F, 0.0F,
            0.0F, 0.0F, -0.007814026F, 0.0F,
            0.0F, 0.0F, -1.0003908F, 1.0F
        }, matrix, EPSILON);
    }

    @Test
    public void modelViewMatrixMatchesIrisShadowMatricesAtDawn() {
        float[] matrix = new float[16];

        ShadowUniforms.createModelView(
            0.03451777F,
            2.0F,
            0.0F,
            0.646045982837677D,
            82.53274536132812D,
            -514.0264282226562D,
            matrix);

        assertArrayEquals(new float[] {
            0.2154504F, 0.000000058204815F, 0.9765147F, 0.0F,
            -0.97651476F, 0.000000012841845F, 0.21545039F, 0.0F,
            0.0F, -0.99999994F, 0.000000059604645F, 0.0F,
            0.3800215F, 1.0264281F, -100.44631F, 1.0F
        }, matrix, 0.0005F);
    }

    @Test
    public void gridSnapOnlySkipsExactZeroInterval() {
        assertFalse(ShadowUniforms.shouldApplyGridSnap(0.0F));
        assertFalse(ShadowUniforms.shouldApplyGridSnap(-0.0F));
        assertTrue(ShadowUniforms.shouldApplyGridSnap(1.0e-7F));
        assertTrue(ShadowUniforms.shouldApplyGridSnap(-2.0F));
    }

    @Test
    public void gridSnapUsesJavaRemainderForNegativeCameraCoordinates() {
        assertEquals(-18.0F, ShadowUniforms.wrapToInterval(-2.0F, 32.0F), EPSILON);
        assertEquals(-14.0F, ShadowUniforms.wrapToInterval(34.0F, 32.0F), EPSILON);
    }
}
