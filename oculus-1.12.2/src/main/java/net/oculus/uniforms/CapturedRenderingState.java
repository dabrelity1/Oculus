package net.oculus.uniforms;

import java.nio.FloatBuffer;
import java.util.Arrays;

import net.oculus.gl.state.GameDataSuppliers;
import net.oculus.gl.state.MatrixMath;
import net.oculus.gl.state.MatrixState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Captures per-frame rendering data so shader uniforms can mirror the Iris pipeline.
 * Tracks current and previous matrices, camera positions, entity IDs, and fog state
 * for use by shader uniforms.
 */
public final class CapturedRenderingState {
    private static final Logger LOGGER = LogManager.getLogger(CapturedRenderingState.class);
    public static final CapturedRenderingState INSTANCE = new CapturedRenderingState();

    private final float[] gbufferModelView = MatrixMath.createIdentity();
    private final float[] gbufferProjection = MatrixMath.createIdentity();
    private final float[] previousModelView = MatrixMath.createIdentity();
    private final float[] previousProjection = MatrixMath.createIdentity();
    private final float[] modelViewInverse = MatrixMath.createIdentity();
    private final float[] projectionInverse = MatrixMath.createIdentity();
    private final float[] modelViewProjection = MatrixMath.createIdentity();

    private final float[] fogColor = new float[3];
    private final float[] fogColorVec4 = new float[4];

    private final double[] cameraPosition = new double[3];
    private final double[] previousCameraPosition = new double[3];
    private final float[] cameraPositionVec = new float[3];
    private final float[] previousCameraPositionVec = new float[3];
    private final double[] unshiftedCameraPosition = new double[3];

    private float tickDelta;
    private float nearPlane = 0.05F;
    private float farPlane = 0.0F;
    private float eyeAltitude = 0.0F;

    private int currentEntity = -1;
    private int currentBlockEntity = -1;

    private final CameraPositionTracker cameraTracker = new CameraPositionTracker();
    private boolean debugLogging = Boolean.getBoolean("oculus.debug.capturedState");
    private int frameCount = 0;

    private CapturedRenderingState() {
        if (debugLogging) {
            LOGGER.info("CapturedRenderingState initialized with debug logging enabled");
        }
    }

    public void beginFrame(float partialTicks) {
        tickDelta = partialTicks;
        cameraTracker.update(partialTicks);
        System.arraycopy(cameraTracker.getCurrent(), 0, cameraPosition, 0, cameraPosition.length);
        System.arraycopy(cameraTracker.getPrevious(), 0, previousCameraPosition, 0, previousCameraPosition.length);
        System.arraycopy(cameraTracker.getLastUnshifted(), 0, unshiftedCameraPosition, 0, unshiftedCameraPosition.length);
        toFloatVector(cameraPosition, cameraPositionVec);
        toFloatVector(previousCameraPosition, previousCameraPositionVec);
        nearPlane = cameraTracker.getNearPlane();
        farPlane = cameraTracker.getFarPlane();
        eyeAltitude = cameraPositionVec[1];
        currentEntity = -1;
        currentBlockEntity = -1;

        captureMatrices();
        captureFogColor();

        if (debugLogging && (frameCount % 100 == 0)) {
            LOGGER.debug("Frame {}: Camera at ({}, {}, {}), Previous ({}, {}, {}), Near={}, Far={}",
                frameCount, 
                cameraPositionVec[0], cameraPositionVec[1], cameraPositionVec[2],
                previousCameraPositionVec[0], previousCameraPositionVec[1], previousCameraPositionVec[2],
                nearPlane, farPlane);
        }
        frameCount++;
    }

    public float[] getGbufferModelView() {
        return gbufferModelView;
    }

    public float[] getGbufferProjection() {
        return gbufferProjection;
    }

    public float[] getPreviousModelView() {
        return previousModelView;
    }

    public float[] getPreviousProjection() {
        return previousProjection;
    }

    public float[] getModelViewInverse() {
        return modelViewInverse;
    }

    public float[] getProjectionInverse() {
        return projectionInverse;
    }

    public float[] getModelViewProjection() {
        return modelViewProjection;
    }

    public double[] getCameraPosition() {
        return cameraPosition;
    }

    public float[] getCameraPositionVec() {
        return cameraPositionVec;
    }

    public double[] getPreviousCameraPosition() {
        return previousCameraPosition;
    }

    public float[] getPreviousCameraPositionVec() {
        return previousCameraPositionVec;
    }

    public double[] getUnshiftedCameraPosition() {
        return unshiftedCameraPosition;
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public float getNearPlane() {
        return nearPlane;
    }

    public float getFarPlane() {
        return farPlane;
    }

    public float getEyeAltitude() {
        return eyeAltitude;
    }

    public float[] getFogColor() {
        return fogColor;
    }

    public float[] getFogColorVec4() {
        return fogColorVec4;
    }

    public void setCurrentEntity(int entityId) {
        currentEntity = entityId;
        if (debugLogging && entityId >= 0) {
            LOGGER.debug("Rendering entity ID: {}", entityId);
        }
    }

    public int getCurrentEntity() {
        return currentEntity;
    }

    public void setCurrentBlockEntity(int entityId) {
        currentBlockEntity = entityId;
        if (debugLogging && entityId >= 0) {
            LOGGER.debug("Rendering block entity ID: {}", entityId);
        }
    }

    public int getCurrentBlockEntity() {
        return currentBlockEntity;
    }

    private void captureMatrices() {
        MatrixMath.copy(gbufferModelView, previousModelView);
        MatrixMath.copy(gbufferProjection, previousProjection);

        FloatBuffer modelViewBuffer = MatrixState.updateModelViewMatrix();
        FloatBuffer projectionBuffer = MatrixState.updateProjectionMatrix();

        MatrixMath.copyFromBuffer(modelViewBuffer, gbufferModelView);
        MatrixMath.copyFromBuffer(projectionBuffer, gbufferProjection);

        MatrixMath.invert(gbufferModelView, modelViewInverse);
        MatrixMath.invert(gbufferProjection, projectionInverse);
        MatrixMath.multiply(gbufferProjection, gbufferModelView, modelViewProjection);

        if (debugLogging && (frameCount % 100 == 0)) {
            LOGGER.debug("Captured matrices - ModelView[0]={}, Projection[0]={}", 
                gbufferModelView[0], gbufferProjection[0]);
        }
    }

    private void captureFogColor() {
        FloatBuffer fog = GameDataSuppliers.fogColor().get();
        if (fog == null || fog.limit() < 3) {
            Arrays.fill(fogColor, 0.0F);
            return;
        }

        setFogColor(fog.get(0), fog.get(1), fog.get(2));
    }

    public void setFogColor(float red, float green, float blue) {
        fogColor[0] = red;
        fogColor[1] = green;
        fogColor[2] = blue;
        fogColorVec4[0] = red;
        fogColorVec4[1] = green;
        fogColorVec4[2] = blue;
        fogColorVec4[3] = 1.0F;
    }

    private void toFloatVector(double[] source, float[] target) {
        for (int i = 0; i < target.length; i++) {
            target[i] = (float) source[i];
        }
    }
}
