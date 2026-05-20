package net.oculus.colorspace;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.ComputeProgram;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.texture.InternalTextureFormat;
import org.lwjgl.opengl.GL42;

/**
 * Compute-shader color-space converter used when the 1.12.2 OpenGL context can
 * bind image uniforms. This follows the 1.16.5 compute path and writes directly
 * into Minecraft's main framebuffer color texture.
 */
public final class ColorSpaceComputeConverter implements ColorSpaceConverter {
    private int width;
    private int height;
    private ColorSpace colorSpace = ColorSpace.SRGB;
    private ComputeProgram program;
    private int targetTexture;

    public ColorSpaceComputeConverter(int width, int height, ColorSpace colorSpace) {
        rebuildProgram(width, height, colorSpace);
    }

    @Override
    public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
        destroyResources();

        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.colorSpace = colorSpace == null ? ColorSpace.SRGB : colorSpace;

        ProgramBuilder builder = ProgramBuilder.beginCompute(
            "colorSpaceCompute",
            ColorSpaceShaderSource.createComputeSource(this.colorSpace)
        );
        builder.addTextureImage(() -> targetTexture, InternalTextureFormat.RGBA8, "readImage");
        this.program = builder.buildCompute();
    }

    @Override
    public void process(int targetTexture) {
        if (this.colorSpace == ColorSpace.SRGB || this.program == null || targetTexture <= 0) {
            return;
        }

        this.targetTexture = targetTexture;
        Throwable failure = null;
        try {
            program.use();
            OculusRenderSystem.dispatchCompute(width / 8, height / 8, 1);
            OculusRenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            this.targetTexture = 0;
            try {
                ComputeProgram.unbind();
            } catch (RuntimeException | Error cleanupException) {
                if (failure != null) {
                    suppressCleanupFailure(failure, cleanupException);
                } else {
                    throw cleanupException;
                }
            }
        }
    }

    @Override
    public void destroy() {
        destroyResources();
    }

    private void destroyResources() {
        ComputeProgram oldProgram = program;
        program = null;
        targetTexture = 0;

        if (oldProgram != null) {
            oldProgram.destroy();
        }
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
