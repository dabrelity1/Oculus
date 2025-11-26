package net.oculus.postprocess;

import net.minecraft.client.Minecraft;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.uniforms.FrameUpdateNotifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * Samples the depth at the center of the screen and provides smoothed depth values
 * for shader effects like depth of field and auto-focus.
 * 
 * <p>This class creates a 1x1 texture that stores the depth at the screen center,
 * which is then smoothed over time to avoid sudden jumps.</p>
 */
public final class CenterDepthSampler {
    private final FrameUpdateNotifier notifier;
    private int centerDepthTexture;
    private int framebuffer;
    private boolean hasUsage;
    private float smoothedCenterDepth = 0.5f;
    private float halfLife;

    public CenterDepthSampler(FrameUpdateNotifier notifier, float halfLife) {
        this.notifier = notifier;
        this.halfLife = halfLife;
    }

    /**
     * Gets the center depth texture ID for binding to samplers.
     */
    public int getCenterDepthTexture() {
        if (centerDepthTexture == 0) {
            createTexture();
        }
        return centerDepthTexture;
    }

    /**
     * Sets whether this sampler is used by any shader programs.
     */
    public void setUsage(boolean usage) {
        this.hasUsage = usage;
    }

    /**
     * Returns whether any shader program uses the center depth sampler.
     */
    public boolean hasUsage() {
        return hasUsage;
    }

    /**
     * Updates the center depth sample from the current depth buffer.
     * This should be called once per frame after the main rendering is complete.
     */
    public void sampleCenterDepth() {
        if (!hasUsage) {
            return;
        }

        if (centerDepthTexture == 0) {
            createTexture();
        }

        Minecraft mc = Minecraft.getMinecraft();
        int centerX = mc.displayWidth / 2;
        int centerY = mc.displayHeight / 2;

        // Read the depth at the screen center from the current depth buffer
        // and write it to our 1x1 texture for shader access
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        
        // Copy depth from center pixel
        GL30.glBlitFramebuffer(
            centerX, centerY, centerX + 1, centerY + 1,
            0, 0, 1, 1,
            GL11.GL_DEPTH_BUFFER_BIT,
            GL11.GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private void createTexture() {
        // Create 1x1 depth texture
        centerDepthTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, centerDepthTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, 1, 1, 0,
            GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.FloatBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Create framebuffer for the depth texture
        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, centerDepthTexture, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /**
     * Destroys OpenGL resources.
     */
    public void destroy() {
        if (centerDepthTexture != 0) {
            GL11.glDeleteTextures(centerDepthTexture);
            centerDepthTexture = 0;
        }
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
    }
}
