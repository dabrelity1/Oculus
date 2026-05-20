package net.oculus.rendertarget;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.texture.DepthBufferFormat;
import org.lwjgl.opengl.GL11;

/**
 * Chooses the depth-copy path that can preserve the source format on the 1.12.2
 * OpenGlHelper framebuffer backend.
 */
public interface DepthCopyStrategy {
    boolean needsDestFramebuffer();

    void copy(GlFramebuffer sourceFramebuffer, int sourceTexture, GlFramebuffer destinationFramebuffer,
              int destinationTexture, int width, int height);

    static DepthCopyStrategy fastest(DepthBufferFormat format) {
        DepthBufferFormat safeFormat = format == null ? DepthBufferFormat.DEPTH : format;
        if (OculusRenderSystem.supportsCopyImageSubData()) {
            return CopyImage.INSTANCE;
        }

        if (safeFormat.isCombinedStencil()) {
            if (OculusRenderSystem.supportsFramebufferBlit()) {
                return BlitFramebuffer.INSTANCE;
            }
            return UnsupportedCombinedDepthStencil.INSTANCE;
        }

        return CopyTexture.INSTANCE;
    }

    final class CopyTexture implements DepthCopyStrategy {
        private static final CopyTexture INSTANCE = new CopyTexture();

        private CopyTexture() {
        }

        @Override
        public boolean needsDestFramebuffer() {
            return false;
        }

        @Override
        public void copy(GlFramebuffer sourceFramebuffer, int sourceTexture, GlFramebuffer destinationFramebuffer,
                         int destinationTexture, int width, int height) {
            sourceFramebuffer.bindAsReadBuffer();
            OculusRenderSystem.copyTexSubImage2D(
                destinationTexture,
                GL11.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                0,
                width,
                height);
        }
    }

    final class BlitFramebuffer implements DepthCopyStrategy {
        private static final BlitFramebuffer INSTANCE = new BlitFramebuffer();

        private BlitFramebuffer() {
        }

        @Override
        public boolean needsDestFramebuffer() {
            return true;
        }

        @Override
        public void copy(GlFramebuffer sourceFramebuffer, int sourceTexture, GlFramebuffer destinationFramebuffer,
                         int destinationTexture, int width, int height) {
            if (destinationFramebuffer == null) {
                throw new IllegalStateException("Combined depth-stencil depth copies require a destination framebuffer.");
            }

            OculusRenderSystem.blitFramebuffer(
                sourceFramebuffer.getId(),
                destinationFramebuffer.getId(),
                0,
                0,
                width,
                height,
                0,
                0,
                width,
                height,
                GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT,
                GL11.GL_NEAREST);
        }
    }

    final class CopyImage implements DepthCopyStrategy {
        private static final CopyImage INSTANCE = new CopyImage();

        private CopyImage() {
        }

        @Override
        public boolean needsDestFramebuffer() {
            return false;
        }

        @Override
        public void copy(GlFramebuffer sourceFramebuffer, int sourceTexture, GlFramebuffer destinationFramebuffer,
                         int destinationTexture, int width, int height) {
            OculusRenderSystem.copyImageSubData(
                sourceTexture,
                GL11.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                destinationTexture,
                GL11.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                width,
                height,
                1);
        }
    }

    final class UnsupportedCombinedDepthStencil implements DepthCopyStrategy {
        private static final UnsupportedCombinedDepthStencil INSTANCE = new UnsupportedCombinedDepthStencil();

        private UnsupportedCombinedDepthStencil() {
        }

        @Override
        public boolean needsDestFramebuffer() {
            return true;
        }

        @Override
        public void copy(GlFramebuffer sourceFramebuffer, int sourceTexture, GlFramebuffer destinationFramebuffer,
                         int destinationTexture, int width, int height) {
            throw new IllegalStateException("Combined depth-stencil depth copies require GL 4.3, ARB_copy_image, or framebuffer blit support on the 1.12.2 OpenGlHelper framebuffer backend.");
        }
    }
}
