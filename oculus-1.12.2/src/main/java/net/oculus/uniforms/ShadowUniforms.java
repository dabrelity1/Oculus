package net.oculus.uniforms;

import net.oculus.gl.state.MatrixMath;
import net.oculus.gl.state.MatrixTransforms;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackShadowDirectives;

/**
 * Supplies OptiFine-compatible shadow matrices for shader uniforms.
 */
public final class ShadowUniforms {
    private static final float NEAR = 0.05F;
    private static final float FAR = 256.0F;

    private static final float[] SHADOW_MODEL_VIEW = MatrixMath.createIdentity();
    private static final float[] SHADOW_MODEL_VIEW_INVERSE = MatrixMath.createIdentity();
    private static final float[] SHADOW_PROJECTION = MatrixMath.createIdentity();
    private static final float[] SHADOW_PROJECTION_INVERSE = MatrixMath.createIdentity();
    private static final float[] ROTATION_MATRIX = MatrixMath.createIdentity();
    private static final float[] TRANSLATION_MATRIX = MatrixMath.createIdentity();
    private static final float[] SNAP_TRANSLATION = MatrixMath.createIdentity();

    private static float sunPathRotation;
    private static float shadowIntervalSize = 2.0F;
    private static float shadowDistance = 160.0F;
    private static Float shadowFov;
    private static boolean projectionDirty = true;
    private static int lastModelViewFrame = -1;

    private ShadowUniforms() {
    }

    public static void configure(PackDirectives directives) {
        sunPathRotation = directives != null ? directives.getSunPathRotation() : 0.0F;
        PackShadowDirectives shadow = directives != null ? directives.getShadowDirectives() : null;
        if (shadow != null) {
            shadowIntervalSize = shadow.getIntervalSize();
            shadowDistance = shadow.getDistance();
            shadowFov = shadow.getFov();
        } else {
            shadowIntervalSize = 2.0F;
            shadowDistance = 160.0F;
            shadowFov = null;
        }
        projectionDirty = true;
        lastModelViewFrame = -1;
    }

    public static float[] getShadowProjection() {
        updateProjectionIfNeeded();
        return SHADOW_PROJECTION;
    }

    public static float[] getShadowProjectionInverse() {
        updateProjectionIfNeeded();
        return SHADOW_PROJECTION_INVERSE;
    }

    public static float[] getShadowModelView() {
        updateModelViewIfNeeded();
        return SHADOW_MODEL_VIEW;
    }

    public static float[] getShadowModelViewInverse() {
        updateModelViewIfNeeded();
        return SHADOW_MODEL_VIEW_INVERSE;
    }

    private static void updateProjectionIfNeeded() {
        if (!projectionDirty) {
            return;
        }

        if (shadowFov != null) {
            createPerspective(shadowFov.floatValue(), SHADOW_PROJECTION);
        } else {
            createOrthographic(shadowDistance, SHADOW_PROJECTION);
        }

        MatrixMath.invert(SHADOW_PROJECTION, SHADOW_PROJECTION_INVERSE);
        projectionDirty = false;
    }

    private static void updateModelViewIfNeeded() {
        int frame = SystemTimeUniforms.COUNTER.getAsInt();
        if (frame == lastModelViewFrame) {
            return;
        }

        lastModelViewFrame = frame;
        MatrixMath.setIdentity(SHADOW_MODEL_VIEW);
        applyTranslation(SHADOW_MODEL_VIEW, 0.0F, 0.0F, -100.0F);
        applyRotationX(SHADOW_MODEL_VIEW, 90.0F);
        float skyAngle = toSkyAngle(CelestialUniforms.getShadowAngle());
        applyRotationZ(SHADOW_MODEL_VIEW, -skyAngle * 360.0F);
        applyRotationX(SHADOW_MODEL_VIEW, sunPathRotation);
        snapModelViewToGrid();
        MatrixMath.invert(SHADOW_MODEL_VIEW, SHADOW_MODEL_VIEW_INVERSE);
    }

    private static float toSkyAngle(float shadowAngle) {
        return shadowAngle < 0.25F ? shadowAngle + 0.75F : shadowAngle - 0.25F;
    }

    private static void snapModelViewToGrid() {
        float interval = shadowIntervalSize;
        if (Math.abs(interval) < 1.0e-6F) {
            return;
        }

        double[] camera = CapturedRenderingState.INSTANCE.getUnshiftedCameraPosition();
        float offsetX = wrapToInterval((float) camera[0], interval);
        float offsetY = wrapToInterval((float) camera[1], interval);
        float offsetZ = wrapToInterval((float) camera[2], interval);

        MatrixTransforms.translation(offsetX, offsetY, offsetZ, SNAP_TRANSLATION);
        MatrixMath.multiply(SHADOW_MODEL_VIEW, SNAP_TRANSLATION, SHADOW_MODEL_VIEW);
    }

    private static float wrapToInterval(float value, float interval) {
        float offset = value % interval;
        float half = interval * 0.5F;
        return offset - half;
    }

    private static void applyTranslation(float[] matrix, float x, float y, float z) {
        MatrixTransforms.translation(x, y, z, TRANSLATION_MATRIX);
        MatrixMath.multiply(matrix, TRANSLATION_MATRIX, matrix);
    }

    private static void applyRotationX(float[] matrix, float degrees) {
        MatrixTransforms.rotationX(degrees, ROTATION_MATRIX);
        MatrixMath.multiply(matrix, ROTATION_MATRIX, matrix);
    }

    private static void applyRotationZ(float[] matrix, float degrees) {
        MatrixTransforms.rotationZ(degrees, ROTATION_MATRIX);
        MatrixMath.multiply(matrix, ROTATION_MATRIX, matrix);
    }

    private static void createOrthographic(float halfPlaneLength, float[] target) {
        MatrixMath.setIdentity(target);
        float safeHalf = Math.max(Math.abs(halfPlaneLength), 0.0001F);
        target[0] = 1.0F / safeHalf;
        target[5] = 1.0F / safeHalf;
        target[10] = 2.0F / (NEAR - FAR);
        target[14] = -(FAR + NEAR) / (FAR - NEAR);
    }

    private static void createPerspective(float fovDegrees, float[] target) {
        MatrixMath.setIdentity(target);
        float radians = (float) Math.toRadians(fovDegrees * 0.5F);
        float yScale = (float) (1.0D / Math.tan(radians));
        target[0] = yScale;
        target[5] = yScale;
        target[10] = (FAR + NEAR) / (NEAR - FAR);
        target[11] = -1.0F;
        target[14] = (2.0F * FAR * NEAR) / (NEAR - FAR);
        target[15] = 1.0F;
    }
}
