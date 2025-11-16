package net.oculus.gl.program;

import java.nio.IntBuffer;

import net.oculus.Oculus;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.vendored.joml.Vector2f;
import net.oculus.vendored.joml.Vector3i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

/**
 * Compute program wrapper that mirrors the Iris implementation closely enough for shader
 * packs to rely on work group helpers.
 */
public final class ComputeProgram extends Program {
    private final int[] localSize = new int[] {1, 1, 1};
    private Vector3i absoluteWorkGroups;
    private Vector2f relativeWorkGroups;
    private Vector3i cachedWorkGroups;
    private float cachedWidth = -1.0F;
    private float cachedHeight = -1.0F;

    ComputeProgram(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
        super(program, uniforms, samplers, images);
        readLocalWorkGroupSize(program);
    }

    private void readLocalWorkGroupSize(int programId) {
        if (!OculusRenderSystem.supportsCompute()) {
            return;
        }

        IntBuffer buffer = BufferUtils.createIntBuffer(3);
        OculusRenderSystem.getProgramiv(programId, GL43.GL_COMPUTE_WORK_GROUP_SIZE, buffer);
        for (int i = 0; i < 3; i++) {
            int value = buffer.get(i);
            localSize[i] = value > 0 ? value : 1;
        }
    }

    public void setWorkGroupInfo(Vector2f relativeWorkGroups, Vector3i absoluteWorkGroups) {
        this.relativeWorkGroups = relativeWorkGroups;
        this.absoluteWorkGroups = absoluteWorkGroups;
        this.cachedWorkGroups = null;
    }

    public Vector3i getWorkGroups(float width, float height) {
        if (cachedWorkGroups != null && cachedWidth == width && cachedHeight == height) {
            return cachedWorkGroups;
        }

        cachedWidth = width;
        cachedHeight = height;

        if (absoluteWorkGroups != null) {
            cachedWorkGroups = new Vector3i(absoluteWorkGroups.x(), absoluteWorkGroups.y(), absoluteWorkGroups.z());
        } else if (relativeWorkGroups != null) {
            int groupsX = (int) Math.ceil(Math.ceil(width * relativeWorkGroups.x()) / localSize[0]);
            int groupsY = (int) Math.ceil(Math.ceil(height * relativeWorkGroups.y()) / localSize[1]);
            cachedWorkGroups = new Vector3i(groupsX, groupsY, 1);
        } else {
            int groupsX = (int) Math.ceil(width / localSize[0]);
            int groupsY = (int) Math.ceil(height / localSize[1]);
            cachedWorkGroups = new Vector3i(groupsX, groupsY, 1);
        }

        return cachedWorkGroups;
    }

    public void dispatch(float width, float height) {
        if (!OculusRenderSystem.supportsCompute()) {
            Oculus.LOGGER.warn("Attempted to dispatch compute program {}, but this platform does not support compute shaders.", getProgramId());
            return;
        }

        OculusRenderSystem.glUseProgram(getProgramId());
        getUniforms().update();
        getSamplers().update();
        getImages().update();

        OculusRenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        Vector3i workGroups = getWorkGroups(width, height);
        OculusRenderSystem.dispatchCompute(workGroups);
    }
}
