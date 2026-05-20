package net.oculus.uniforms;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.oculus.gl.state.MatrixMath;
import net.oculus.gl.state.MatrixTransforms;
import net.oculus.shaderpack.PackDirectives;

/**
 * Provides OptiFine-style celestial uniforms such as sun/moon positions and angles.
 */
public final class CelestialUniforms {
    private static final float[] WORK_MATRIX = MatrixMath.createIdentity();
    private static final float[] ROTATION_MATRIX = MatrixMath.createIdentity();
    private static final float[] SUN_POSITION = new float[3];
    private static final float[] MOON_POSITION = new float[3];
    private static final float[] SHADOW_LIGHT = new float[3];
    private static final float[] SHADOW_LIGHT_WORLD = new float[3];
    private static final float[] UP_POSITION = new float[3];

    private static float sunPathRotation;

    private CelestialUniforms() {
    }

    public static void configure(PackDirectives directives) {
        sunPathRotation = directives != null ? directives.getSunPathRotation() : 0.0F;
    }

    public static float getSunPathRotation() {
        return sunPathRotation;
    }

    public static float getSunAngle() {
        float skyAngle = getSkyAngle();
        return skyAngle < 0.75F ? skyAngle + 0.25F : skyAngle - 0.75F;
    }

    public static float getShadowAngle() {
        float shadowAngle = getSunAngle();
        if (!isDay()) {
            shadowAngle -= 0.5F;
        }
        return shadowAngle;
    }

    public static boolean isDay() {
        return getSunAngle() <= 0.5F;
    }

    public static float[] getSunPosition() {
        return getCelestialPosition(100.0F, SUN_POSITION);
    }

    public static float[] getMoonPosition() {
        return getCelestialPosition(-100.0F, MOON_POSITION);
    }

    public static float[] getShadowLightPosition() {
        float[] source = isDay() ? getSunPosition() : getMoonPosition();
        SHADOW_LIGHT[0] = source[0];
        SHADOW_LIGHT[1] = source[1];
        SHADOW_LIGHT[2] = source[2];
        return SHADOW_LIGHT;
    }

    public static float[] getShadowLightPositionInWorldSpace() {
        return getCelestialPositionInWorldSpace(isDay() ? 100.0F : -100.0F, SHADOW_LIGHT_WORLD);
    }

    public static float[] getUpPosition() {
        float[] matrix = copyModelView();
        applyRotationY(matrix, -90.0F);
        MatrixTransforms.transform(matrix, 0.0F, 100.0F, 0.0F, 0.0F, UP_POSITION);
        return UP_POSITION;
    }

    private static float[] getCelestialPosition(float y, float[] target) {
        float[] matrix = copyModelView();
        applyRotationY(matrix, -90.0F);
        applyRotationZ(matrix, sunPathRotation);
        applyRotationX(matrix, getSkyAngle() * 360.0F);
        MatrixTransforms.transform(matrix, 0.0F, y, 0.0F, 0.0F, target);
        return target;
    }

    private static float[] getCelestialPositionInWorldSpace(float y, float[] target) {
        MatrixMath.setIdentity(WORK_MATRIX);
        applyRotationY(WORK_MATRIX, -90.0F);
        applyRotationZ(WORK_MATRIX, sunPathRotation);
        applyRotationX(WORK_MATRIX, getSkyAngle() * 360.0F);
        MatrixTransforms.transform(WORK_MATRIX, 0.0F, y, 0.0F, 0.0F, target);
        return target;
    }

    private static float[] copyModelView() {
        float[] modelView = CapturedRenderingState.INSTANCE.getGbufferModelView();
        System.arraycopy(modelView, 0, WORK_MATRIX, 0, WORK_MATRIX.length);
        return WORK_MATRIX;
    }

    private static void applyRotationX(float[] matrix, float degrees) {
        MatrixTransforms.rotationX(degrees, ROTATION_MATRIX);
        MatrixMath.multiply(matrix, ROTATION_MATRIX, matrix);
    }

    private static void applyRotationY(float[] matrix, float degrees) {
        MatrixTransforms.rotationY(degrees, ROTATION_MATRIX);
        MatrixMath.multiply(matrix, ROTATION_MATRIX, matrix);
    }

    private static void applyRotationZ(float[] matrix, float degrees) {
        MatrixTransforms.rotationZ(degrees, ROTATION_MATRIX);
        MatrixMath.multiply(matrix, ROTATION_MATRIX, matrix);
    }

    private static float getSkyAngle() {
        World world = getWorld();
        if (world == null) {
            return 0.0F;
        }
        return world.getCelestialAngle(CapturedRenderingState.INSTANCE.getTickDelta());
    }

    private static World getWorld() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null ? minecraft.world : null;
    }
}
