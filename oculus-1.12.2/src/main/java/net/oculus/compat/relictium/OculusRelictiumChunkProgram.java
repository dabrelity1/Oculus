package net.oculus.compat.relictium;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.function.Function;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderFogComponent;
import net.minecraft.util.ResourceLocation;
import net.oculus.gl.state.MatrixMath;
import net.oculus.gl.state.MatrixState;
import net.oculus.gl.program.Program;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

public final class OculusRelictiumChunkProgram extends ChunkProgram {
    private final Program oculusProgram;
    private final int irisModelViewMatrixLocation;
    private final int irisProjectionMatrixLocation;
    private final int uModelViewProjectionMatrixLocation;
    private final int irisModelViewProjectionMatrixLocation;
    private final int irisNormalMatrixLocation;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final float[] currentModelView = MatrixMath.createIdentity();
    private final float[] currentProjection = MatrixMath.createIdentity();
    private final float[] currentModelViewProjection = MatrixMath.createIdentity();
    private final float[] currentModelViewInverse = MatrixMath.createIdentity();
    private final float[] currentNormalMatrix = MatrixMath.createIdentity();

    public OculusRelictiumChunkProgram(RenderDevice device,
                                       ResourceLocation name,
                                       int handle,
                                       Function<ChunkProgram, ChunkShaderFogComponent> fogFactory,
                                       Program oculusProgram) {
        super(device, name, handle, fogFactory == null ? ChunkShaderFogComponent.None::new : fogFactory);
        this.oculusProgram = oculusProgram;
        this.irisModelViewMatrixLocation = findUniformLocation(handle, "iris_ModelViewMatrix");
        this.irisProjectionMatrixLocation = findUniformLocation(handle, "iris_ProjectionMatrix");
        this.uModelViewProjectionMatrixLocation = findUniformLocation(handle, "u_ModelViewProjectionMatrix");
        this.irisModelViewProjectionMatrixLocation = findUniformLocation(handle, "iris_ModelViewProjectionMatrix");
        this.irisNormalMatrixLocation = findUniformLocation(handle, "iris_NormalMatrix");
    }

    @Override
    public int getUniformLocation(String name) {
        if ("iris_BlockTex".equals(name) || "iris_LightTex".equals(name)) {
            return -1;
        }

        try {
            return super.getUniformLocation(name);
        } catch (NullPointerException exception) {
            return -1;
        }
    }

    @Override
    public void setup(float modelScale, float textureScale) {
        try {
            if (oculusProgram != null) {
                oculusProgram.bindUniforms();
            }

            super.setup(modelScale, textureScale);

            if (oculusProgram != null) {
                oculusProgram.bindSamplers();
                oculusProgram.bindImages();
            }

            uploadIrisMatrices();
        } catch (RuntimeException exception) {
            cleanupAfterSetupFailure(exception);
            throw exception;
        } catch (Error error) {
            cleanupAfterSetupFailure(error);
            throw error;
        }
    }

    @Override
    public void delete() {
        Throwable failure = null;
        if (oculusProgram != null) {
            try {
                oculusProgram.destroy();
            } catch (RuntimeException | Error exception) {
                failure = exception;
            }
        }

        try {
            super.delete();
        } catch (RuntimeException | Error exception) {
            if (failure != null) {
                suppressCleanupFailure(exception, failure);
            }
            throw exception;
        }

        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    private void uploadIrisMatrices() {
        if (irisModelViewMatrixLocation < 0
                && irisProjectionMatrixLocation < 0
                && uModelViewProjectionMatrixLocation < 0
                && irisModelViewProjectionMatrixLocation < 0
                && irisNormalMatrixLocation < 0) {
            return;
        }

        MatrixMath.copyFromBuffer(MatrixState.updateModelViewMatrix(), currentModelView);
        MatrixMath.copyFromBuffer(MatrixState.updateProjectionMatrix(), currentProjection);
        MatrixMath.multiply(currentProjection, currentModelView, currentModelViewProjection);
        MatrixMath.invert(currentModelView, currentModelViewInverse);
        MatrixMath.transpose(currentModelViewInverse, currentNormalMatrix);

        if (irisModelViewMatrixLocation >= 0) {
            uploadMatrix(irisModelViewMatrixLocation, currentModelView);
        }

        if (irisProjectionMatrixLocation >= 0) {
            uploadMatrix(irisProjectionMatrixLocation, currentProjection);
        }

        if (uModelViewProjectionMatrixLocation >= 0) {
            uploadMatrix(uModelViewProjectionMatrixLocation, currentModelViewProjection);
        }

        if (irisModelViewProjectionMatrixLocation >= 0) {
            uploadMatrix(irisModelViewProjectionMatrixLocation, currentModelViewProjection);
        }

        if (irisNormalMatrixLocation >= 0) {
            uploadMatrix(irisNormalMatrixLocation, currentNormalMatrix);
        }
    }

    private void uploadMatrix(int location, float[] matrix) {
        if (matrix == null) {
            return;
        }

        ((Buffer) matrixBuffer).clear();
        matrixBuffer.put(matrix, 0, Math.min(16, matrix.length));
        ((Buffer) matrixBuffer).flip();
        GL20.glUniformMatrix4(location, false, matrixBuffer);
    }

    private void cleanupAfterSetupFailure(Throwable failure) {
        if (oculusProgram != null) {
            Program.cleanupAfterActivationFailure(failure);
        }
    }

    private static int findUniformLocation(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
