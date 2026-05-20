package net.oculus.uniforms;

import java.nio.FloatBuffer;

import net.oculus.gl.state.FanOutValueUpdateNotifier;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.gl.state.GameDataSuppliers;
import net.oculus.gl.state.MatrixMath;
import net.oculus.gl.state.MatrixState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central state capture system for shader uniforms in the Oculus 1.12.2 shader pipeline.
 *
 * <p>This class serves as the backbone for providing shader programs with accurate rendering
 * state information. It captures and maintains:</p>
 *
 * <ul>
 *   <li><b>Matrix State:</b> Current and previous frame model-view and projection matrices,
 *       their inverses, and the combined model-view-projection matrix</li>
 *   <li><b>Camera Position:</b> High-precision camera tracking with automatic shifting to
 *       maintain floating-point precision at large world coordinates</li>
 *   <li><b>Entity IDs:</b> Current entity and block entity being rendered for shader-based
 *       entity detection and custom rendering</li>
 *   <li><b>Fog State:</b> Current fog color in both RGB and RGBA formats</li>
 *   <li><b>View Frustum:</b> Near and far plane distances</li>
 * </ul>
 *
 * <p><b>Usage Pattern:</b></p>
 * <pre>
 * // Called once per frame by ShaderWorldRenderingPipeline
 * CapturedRenderingState.INSTANCE.beginFrame(partialTicks);
 *
 * // Matrices and camera data are automatically captured from OpenGL state
 * // Entity IDs are set by mixins during entity/block entity rendering
 *
 * // Shader uniforms retrieve values via getters
 * float[] modelView = CapturedRenderingState.INSTANCE.getGbufferModelView();
 * </pre>
 *
 * <p><b>Thread Safety:</b> This class is not thread-safe and should only be accessed
 * from the main render thread.</p>
 *
 * <p><b>Debugging:</b> Set system property {@code -Doculus.debug.capturedState=true}
 * to enable detailed logging of state capture.</p>
 *
 * @see CameraPositionTracker
 * @see net.oculus.pipeline.ShaderWorldRenderingPipeline#beginWorldRendering(float)
 * @see net.oculus.gl.program.ProgramBuilder#handleUniform(String, int)
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
    private final float[] normalMatrix = MatrixMath.createIdentity();

    private final float[] fogColor = new float[3];
    private final float[] fogColorVec4 = new float[4];

    private final double[] cameraPosition = new double[3];
    private final double[] previousCameraPosition = new double[3];
    private final float[] cameraPositionVec = new float[3];
    private final float[] previousCameraPositionVec = new float[3];
    private final int[] cameraPositionInt = new int[3];
    private final int[] previousCameraPositionInt = new int[3];
    private final float[] cameraPositionFract = new float[3];
    private final float[] previousCameraPositionFract = new float[3];
    private final double[] unshiftedCameraPosition = new double[3];

    private float tickDelta;
    private float nearPlane = 0.05F;
    private float farPlane = 0.0F;
    private float eyeAltitude = 0.0F;

    private int currentEntity = -1;
    private int currentBlockEntity = -1;
    private final FanOutValueUpdateNotifier entityIdNotifier = new FanOutValueUpdateNotifier();
    private final FanOutValueUpdateNotifier blockEntityIdNotifier = new FanOutValueUpdateNotifier();
    private final FanOutValueUpdateNotifier fogColorNotifier = new FanOutValueUpdateNotifier();

    private final CameraPositionTracker cameraTracker = new CameraPositionTracker();
    private boolean debugLogging = Boolean.getBoolean("oculus.debug.capturedState");
    private int frameCount = 0;

    private CapturedRenderingState() {
        if (debugLogging) {
            LOGGER.info("CapturedRenderingState initialized with debug logging enabled");
        }
    }

    /**
     * Initializes state capture for a new frame.
     *
     * <p>This method should be called once per frame before any rendering occurs, typically
     * from {@link net.oculus.pipeline.ShaderWorldRenderingPipeline#beginWorldRendering(float)}.
     * It performs the following operations:</p>
     *
     * <ol>
     *   <li>Stores the current frame's matrices as "previous" for next frame</li>
     *   <li>Captures fresh model-view and projection matrices from OpenGL state</li>
     *   <li>Computes matrix inverses and combined model-view-projection</li>
     *   <li>Updates camera position tracking with precision shifting</li>
     *   <li>Captures fog color from game state</li>
     *   <li>Resets entity/block entity IDs to -1 for the new frame</li>
     * </ol>
     *
     * @param partialTicks The interpolation factor between game ticks (0.0 to 1.0),
     *                     used for smooth camera movement and animations
     */
    public void beginFrame(float partialTicks) {
        tickDelta = partialTicks;
        cameraTracker.update(partialTicks);
        refreshCameraState();
        setCurrentEntity(-1);
        setCurrentBlockEntity(-1);

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

    public void capturePostCameraSetup(float partialTicks) {
        tickDelta = partialTicks;
        cameraTracker.updateFromActiveRenderInfo(partialTicks);
        refreshCameraState();
        captureCurrentMatrices();
    }

    /**
     * Returns the current frame's model-view matrix in column-major format.
     * Used by the {@code gbufferModelView} shader uniform.
     *
     * @return 16-element float array representing the 4x4 matrix
     */
    public float[] getGbufferModelView() {
        return gbufferModelView;
    }

    /**
     * Returns the current frame's projection matrix in column-major format.
     * Used by the {@code gbufferProjection} shader uniform.
     *
     * @return 16-element float array representing the 4x4 matrix
     */
    public float[] getGbufferProjection() {
        return gbufferProjection;
    }

    /**
     * Returns the previous frame's model-view matrix in column-major format.
     * Used by the {@code gbufferPreviousModelView} shader uniform for motion vectors and TAA.
     *
     * @return 16-element float array representing the 4x4 matrix
     */
    public float[] getPreviousModelView() {
        return previousModelView;
    }

    /**
     * Returns the previous frame's projection matrix in column-major format.
     * Used by the {@code gbufferPreviousProjection} shader uniform for motion vectors and TAA.
     *
     * @return 16-element float array representing the 4x4 matrix
     */
    public float[] getPreviousProjection() {
        return previousProjection;
    }

    /**
     * Returns the inverse of the current model-view matrix in column-major format.
     * Used by the {@code gbufferModelViewInverse} shader uniform.
     *
     * @return 16-element float array representing the 4x4 inverse matrix
     */
    public float[] getModelViewInverse() {
        return modelViewInverse;
    }

    /**
     * Returns the inverse of the current projection matrix in column-major format.
     * Used by the {@code gbufferProjectionInverse} shader uniform.
     *
     * @return 16-element float array representing the 4x4 inverse matrix
     */
    public float[] getProjectionInverse() {
        return projectionInverse;
    }

    /**
     * Returns the combined model-view-projection matrix in column-major format.
     * Computed as projection * modelView. Used by shader uniforms requiring the full transform.
     *
     * @return 16-element float array representing the 4x4 combined matrix
     */
    public float[] getModelViewProjection() {
        return modelViewProjection;
    }

    public float[] getNormalMatrix() {
        return normalMatrix;
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

    public int[] getCameraPositionInt() {
        return cameraPositionInt;
    }

    public int[] getPreviousCameraPositionInt() {
        return previousCameraPositionInt;
    }

    public float[] getCameraPositionFract() {
        return cameraPositionFract;
    }

    public float[] getPreviousCameraPositionFract() {
        return previousCameraPositionFract;
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
        if (currentEntity == entityId) {
            return;
        }

        currentEntity = entityId;
        if (debugLogging && entityId >= 0) {
            LOGGER.debug("Rendering entity ID: {}", entityId);
        }
        entityIdNotifier.notifyListeners();
    }

    public int getCurrentEntity() {
        return currentEntity;
    }

    public void setCurrentBlockEntity(int entityId) {
        if (currentBlockEntity == entityId) {
            return;
        }

        currentBlockEntity = entityId;
        if (debugLogging && entityId >= 0) {
            LOGGER.debug("Rendering block entity ID: {}", entityId);
        }
        blockEntityIdNotifier.notifyListeners();
    }

    public int getCurrentBlockEntity() {
        return currentBlockEntity;
    }

    public ValueUpdateNotifier getEntityIdNotifier() {
        return entityIdNotifier;
    }

    public ValueUpdateNotifier getBlockEntityIdNotifier() {
        return blockEntityIdNotifier;
    }

    public ValueUpdateNotifier getFogColorNotifier() {
        return fogColorNotifier;
    }

    private void captureMatrices() {
        MatrixMath.copy(gbufferModelView, previousModelView);
        MatrixMath.copy(gbufferProjection, previousProjection);
        captureCurrentMatrices();
    }

    private void captureCurrentMatrices() {
        FloatBuffer modelViewBuffer = MatrixState.updateModelViewMatrix();
        FloatBuffer projectionBuffer = MatrixState.updateProjectionMatrix();

        MatrixMath.copyFromBuffer(modelViewBuffer, gbufferModelView);
        MatrixMath.copyFromBuffer(projectionBuffer, gbufferProjection);

        MatrixMath.invert(gbufferModelView, modelViewInverse);
        MatrixMath.transpose(modelViewInverse, normalMatrix);
        MatrixMath.invert(gbufferProjection, projectionInverse);
        MatrixMath.multiply(gbufferProjection, gbufferModelView, modelViewProjection);

        if (debugLogging && (frameCount % 100 == 0)) {
            LOGGER.debug("Captured matrices - ModelView[0]={}, Projection[0]={}",
                gbufferModelView[0], gbufferProjection[0]);
        }
    }

    private void refreshCameraState() {
        System.arraycopy(cameraTracker.getCurrent(), 0, cameraPosition, 0, cameraPosition.length);
        System.arraycopy(cameraTracker.getPrevious(), 0, previousCameraPosition, 0, previousCameraPosition.length);
        System.arraycopy(cameraTracker.getLastUnshifted(), 0, unshiftedCameraPosition, 0, unshiftedCameraPosition.length);
        toFloatVector(cameraPosition, cameraPositionVec);
        toFloatVector(previousCameraPosition, previousCameraPositionVec);
        splitPosition(cameraPosition, cameraPositionInt, cameraPositionFract);
        splitPosition(previousCameraPosition, previousCameraPositionInt, previousCameraPositionFract);
        nearPlane = cameraTracker.getNearPlane();
        farPlane = cameraTracker.getFarPlane();
        eyeAltitude = cameraPositionVec[1];
    }

    private void captureFogColor() {
        FloatBuffer fog = GameDataSuppliers.fogColor().get();
        if (fog == null || fog.limit() < 3) {
            setFogColor(0.0F, 0.0F, 0.0F);
            return;
        }

        setFogColor(fog.get(0), fog.get(1), fog.get(2));
    }

    public void setFogColor(float red, float green, float blue) {
        boolean changed = fogColor[0] != red || fogColor[1] != green || fogColor[2] != blue;
        fogColor[0] = red;
        fogColor[1] = green;
        fogColor[2] = blue;
        fogColorVec4[0] = red;
        fogColorVec4[1] = green;
        fogColorVec4[2] = blue;
        fogColorVec4[3] = 1.0F;
        if (changed) {
            fogColorNotifier.notifyListeners();
        }
    }

    private void toFloatVector(double[] source, float[] target) {
        for (int i = 0; i < target.length; i++) {
            target[i] = (float) source[i];
        }
    }

    private void splitPosition(double[] source, int[] integerPart, float[] fractionalPart) {
        for (int i = 0; i < integerPart.length; i++) {
            double floor = Math.floor(source[i]);
            integerPart[i] = (int) floor;
            fractionalPart[i] = (float) (source[i] - floor);
        }
    }
}
