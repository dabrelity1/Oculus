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

    public int getWidth() {
        return cachedWidth;
    }

    public int getHeight() {
        return cachedHeight;
    }

    /**
     * Creates a framebuffer that writes to the main textures of the specified color attachments.
     */
    public Framebuffer createColorFramebuffer(ImmutableSet<Integer> flipped, int[] drawBuffers) {
        int framebufferId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);

        for (int i = 0; i < drawBuffers.length; i++) {
            int bufferIndex = drawBuffers[i];
            RenderTarget target = get(bufferIndex);
            if (target == null) {
                continue;
            }

            int texture = flipped.contains(bufferIndex) ? target.getAltTexture() : target.getMainTexture();
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, GL11.GL_TEXTURE_2D, texture, 0);
        }

        // Set up draw buffers
        if (drawBuffers.length > 0) {
            int[] attachments = new int[drawBuffers.length];
            for (int i = 0; i < drawBuffers.length; i++) {
                attachments[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
            }
            java.nio.IntBuffer buffer = org.lwjgl.BufferUtils.createIntBuffer(attachments.length);
            buffer.put(attachments);
            buffer.flip();
            GL20.glDrawBuffers(buffer);
        }

        // Add depth attachment
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, currentDepthTexture, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete: " + status);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        // We need to wrap this in our Framebuffer class
        // For now, return null and we'll implement this properly later
        return null;
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
