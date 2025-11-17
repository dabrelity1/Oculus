package com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives.RenderTargetSettings;
import net.oculus.vendored.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Creates and caches framebuffer objects for the shader pipeline. The implementation mirrors the
 * Iris {@code RenderTargets} manager but with several TODOs where the more advanced logic will be
 * filled in later.
 */
public final class FramebufferManager {
    private static final ByteBuffer NULL_BUFFER = null;

    private final PackDirectives directives;
    private final PackRenderTargetDirectives renderTargetDirectives;
    private final Map<Integer, RenderTarget> renderTargets = new HashMap<>();
    private final List<Framebuffer> ownedFramebuffers = new ArrayList<>();

    private int depthTexture;
    private int width;
    private int height;

    public FramebufferManager(PackDirectives directives) {
        this.directives = Objects.requireNonNull(directives, "directives");
        this.renderTargetDirectives = directives.getRenderTargetDirectives();
    }

    public void initialize() {
        Minecraft minecraft = Minecraft.getMinecraft();
        int displayWidth = minecraft != null ? Math.max(1, minecraft.displayWidth) : 1;
        int displayHeight = minecraft != null ? Math.max(1, minecraft.displayHeight) : 1;

        rebuildRenderTargets(displayWidth, displayHeight);
        rebuildDepthTexture(displayWidth, displayHeight);
    }

    public void resizeIfNeeded(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }

        if (newWidth == width && newHeight == height) {
            return;
        }

        rebuildRenderTargets(newWidth, newHeight);
        rebuildDepthTexture(newWidth, newHeight);
    }

    public Framebuffer createFramebuffer(boolean useAltBuffers, int[] drawBuffers) {
        Objects.requireNonNull(drawBuffers, "drawBuffers");
        GlFramebuffer glFramebuffer = new GlFramebuffer();

        for (int attachmentIndex = 0; attachmentIndex < drawBuffers.length; attachmentIndex++) {
            int targetIndex = drawBuffers[attachmentIndex];
            RenderTarget target = getRenderTarget(targetIndex);
            int textureId = useAltBuffers ? target.getAltTexture() : target.getMainTexture();
            glFramebuffer.addColorAttachment(attachmentIndex, textureId);
        }

        if (drawBuffers.length == 0) {
            glFramebuffer.noDrawBuffers();
        } else {
            int[] logicalBuffers = new int[drawBuffers.length];
            for (int i = 0; i < drawBuffers.length; i++) {
                logicalBuffers[i] = i;
            }
            glFramebuffer.drawBuffers(logicalBuffers);
            glFramebuffer.readBuffer(0);
        }

        if (depthTexture != 0) {
            glFramebuffer.addDepthAttachment(depthTexture);
        }

        Framebuffer framebuffer = new Framebuffer(glFramebuffer, drawBuffers);
        ownedFramebuffers.add(framebuffer);
        return framebuffer;
    }

    public RenderTarget getRenderTarget(int index) {
        RenderTarget target = renderTargets.get(index);
        if (target == null) {
            throw new IllegalArgumentException("Unknown render target index " + index);
        }
        return target;
    }

    public void destroyFramebuffer(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return;
        }
        framebuffer.destroy();
        ownedFramebuffers.remove(framebuffer);
    }

    public void destroy() {
        ownedFramebuffers.forEach(Framebuffer::destroy);
        ownedFramebuffers.clear();

        renderTargets.values().forEach(RenderTarget::destroy);
        renderTargets.clear();

        deleteTexture(depthTexture);
        depthTexture = 0;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepthTexture() {
        return depthTexture;
    }

    private void rebuildRenderTargets(int targetWidth, int targetHeight) {
        this.width = targetWidth;
        this.height = targetHeight;

        Map<Integer, RenderTargetSettings> settings = renderTargetDirectives.getRenderTargetSettings();

        settings.forEach((index, renderTargetSettings) -> {
            Vector2i dimensions = directives.getTextureScaleOverride(index, targetWidth, targetHeight);
            RenderTarget target = renderTargets.computeIfAbsent(index, ignored -> RenderTarget.builder()
                .setInternalFormat(renderTargetSettings.getInternalFormat())
                .setPixelFormat(renderTargetSettings.getInternalFormat().getPixelFormat())
                .setDimensions(Math.max(1, dimensions.x()), Math.max(1, dimensions.y()))
                .build());

            target.resize(Math.max(1, dimensions.x()), Math.max(1, dimensions.y()));
        });
    }

    private void rebuildDepthTexture(int targetWidth, int targetHeight) {
        deleteTexture(depthTexture);
        depthTexture = createDepthTexture(targetWidth, targetHeight);
    }

    private static int createDepthTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, NULL_BUFFER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static void deleteTexture(int texture) {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
        }
    }
}
