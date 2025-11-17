package net.oculus.gl.state;

import java.nio.Buffer;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Caches access to the OpenGL model-view and projection matrices for shader uniforms.
 * LWJGL 2 only exposes these through glGetFloat, so we reuse shared buffers to avoid
 * per-frame allocations while keeping the data accessible to callers.
 */
public final class MatrixState {
    private static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);

    private MatrixState() {
    }

    public static FloatBuffer updateModelViewMatrix() {
        ((Buffer) MODEL_VIEW).clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
        ((Buffer) MODEL_VIEW).rewind();
        return MODEL_VIEW;
    }

    public static FloatBuffer updateProjectionMatrix() {
        ((Buffer) PROJECTION).clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        ((Buffer) PROJECTION).rewind();
        return PROJECTION;
    }
}
