package net.oculus.rendertarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.Framebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.FramebufferManager;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import com.google.common.collect.ImmutableSet;
import net.oculus.gl.texture.DepthBufferFormat;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.vendored.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Manages the render targets (colortex0-15) used by shader packs.
 * Each target has a main and alternate texture for ping-pong buffering.
 */
public class RenderTargets {
    private final RenderTarget[] targets;
    private int currentDepthTexture;
    private DepthBufferFormat currentDepthFormat;

    private final List<Framebuffer> ownedFramebuffers;

    private int cachedWidth;
    private int cachedHeight;
    private boolean fullClearRequired;

    public RenderTargets(int width, int height, int depthTexture, DepthBufferFormat depthFormat,
                         Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargets,
                         PackDirectives packDirectives) {
        // Find the maximum target index to size the array
        int maxIndex = renderTargets.keySet().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(-1);
        
        targets = new RenderTarget[maxIndex + 1];

        renderTargets.forEach((index, settings) -> {
            Vector2i dimensions = packDirectives.getTextureScaleOverride(index, width, height);
            targets[index] = RenderTarget.builder()
                .setDimensions(dimensions.x(), dimensions.y())
                .setInternalFormat(settings.getInternalFormat())
                .setPixelFormat(settings.getInternalFormat().getPixelFormat())
                .build();
        });

        this.currentDepthTexture = depthTexture;
        this.currentDepthFormat = depthFormat;

        this.cachedWidth = width;
        this.cachedHeight = height;

        this.ownedFramebuffers = new ArrayList<>();
        this.fullClearRequired = true;
    }

    public void destroy() {
        for (Framebuffer owned : ownedFramebuffers) {
            owned.destroy();
        }

        for (RenderTarget target : targets) {
            if (target != null) {
                target.destroy();
            }
        }
    }

    public int getRenderTargetCount() {
        return targets.length;
    }

    public RenderTarget get(int index) {
        if (index < 0 || index >= targets.length) {
            return null;
        }
        return targets[index];
    }

    public int getCurrentDepthTexture() {
        return currentDepthTexture;
    }

    public DepthBufferFormat getCurrentDepthFormat() {
        return currentDepthFormat;
    }

    /**
     * Returns a dummy RenderTarget wrapper for the depth texture.
     * Used by FinalPassRenderer to bind depth as a sampler.
     */
    public RenderTarget getDepthTexture() {
        // Return the first target if available (for accessing depth through it)
        // In practice, shaders access depthtex0 via the currentDepthTexture
        return null; // Depth is handled separately via getCurrentDepthTexture()
    }

    public int getWidth() {
        return cachedWidth;
    }

    public int getHeight() {
        return cachedHeight;
    }

    /**
     * Creates a framebuffer that writes to the main textures of the specified color attachments.
     */
    public com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> flipped, int[] drawBuffers) {
        com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer framebuffer = 
            new com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer();

        for (int i = 0; i < drawBuffers.length; i++) {
            int bufferIndex = drawBuffers[i];
            RenderTarget target = get(bufferIndex);
            if (target == null) {
                continue;
            }

            int texture = flipped.contains(bufferIndex) ? target.getAltTexture() : target.getMainTexture();
            framebuffer.addColorAttachment(i, texture);
        }

        // Add depth attachment
        framebuffer.addDepthAttachment(currentDepthTexture);

        // Set draw buffers
        framebuffer.drawBuffers(drawBuffers);

        return framebuffer;
    }

    /**
     * Creates a framebuffer using a Set instead of ImmutableSet.
     */
    public com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer createColorFramebuffer(java.util.Set<Integer> flipped, int[] drawBuffers) {
        com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer framebuffer = 
            new com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer();

        for (int i = 0; i < drawBuffers.length; i++) {
            int bufferIndex = drawBuffers[i];
            RenderTarget target = get(bufferIndex);
            if (target == null) {
                continue;
            }

            int texture = flipped.contains(bufferIndex) ? target.getAltTexture() : target.getMainTexture();
            framebuffer.addColorAttachment(i, texture);
        }

        // Add depth attachment
        framebuffer.addDepthAttachment(currentDepthTexture);

        // Set draw buffers
        framebuffer.drawBuffers(drawBuffers);

        return framebuffer;
    }

    public void destroyFramebuffer(Framebuffer framebuffer) {
        if (framebuffer != null) {
            ownedFramebuffers.remove(framebuffer);
            framebuffer.destroy();
        }
    }

    /**
     * Resizes all render targets.
     */
    public void resize(int width, int height) {
        if (width == cachedWidth && height == cachedHeight) {
            return;
        }

        this.cachedWidth = width;
        this.cachedHeight = height;

        for (RenderTarget target : targets) {
            if (target != null) {
                target.resize(width, height);
            }
        }

        fullClearRequired = true;
    }

    public boolean isFullClearRequired() {
        return fullClearRequired;
    }

    public void onFullClear() {
        fullClearRequired = false;
    }
}
