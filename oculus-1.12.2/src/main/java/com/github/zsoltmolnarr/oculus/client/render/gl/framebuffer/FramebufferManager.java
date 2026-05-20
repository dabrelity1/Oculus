package com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives.RenderTargetSettings;
import net.oculus.texture.TextureLifecycleTracker;
import net.oculus.vendored.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Creates and caches framebuffer objects for the shader pipeline, including the
 * render-target textures and shared depth texture used by gbuffer passes.
 */
public final class FramebufferManager {
    private static final ByteBuffer NULL_BUFFER = null;

    private final PackDirectives directives;
    private final PackRenderTargetDirectives renderTargetDirectives;
    private final Map<Integer, RenderTarget> renderTargets = new HashMap<>();
    private final List<Framebuffer> ownedFramebuffers = new ArrayList<>();

    private int depthTexture;
    private int depthTextureVersion;
    private int width;
    private int height;
    private boolean destroyed;

    public FramebufferManager(PackDirectives directives) {
        this.directives = Objects.requireNonNull(directives, "directives");
        this.renderTargetDirectives = directives.getRenderTargetDirectives();
    }

    public void initialize() {
        requireLive("initialize framebuffer manager");
        Minecraft minecraft = Minecraft.getMinecraft();
        int displayWidth = minecraft != null ? Math.max(1, minecraft.displayWidth) : 1;
        int displayHeight = minecraft != null ? Math.max(1, minecraft.displayHeight) : 1;

        try {
            rebuildRenderTargets(displayWidth, displayHeight);
            rebuildDepthTexture(displayWidth, displayHeight);
        } catch (PostInstallCleanupException exception) {
            throw exception;
        } catch (RuntimeException | Error exception) {
            try {
                destroy();
            } catch (RuntimeException | Error cleanupException) {
                if (cleanupException != exception) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }
    }

    public void resizeIfNeeded(int newWidth, int newHeight) {
        requireLive("resize framebuffer manager");
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }

        if (newWidth == width && newHeight == height) {
            return;
        }

        int previousWidth = width;
        int previousHeight = height;
        try {
            rebuildRenderTargets(newWidth, newHeight);
            rebuildDepthTexture(newWidth, newHeight);
        } catch (PostInstallCleanupException exception) {
            throw exception;
        } catch (RuntimeException | Error exception) {
            if (previousWidth > 0 && previousHeight > 0) {
                try {
                    rebuildRenderTargets(previousWidth, previousHeight);
                } catch (RuntimeException | Error rollbackException) {
                    if (rollbackException != exception) {
                        exception.addSuppressed(rollbackException);
                    }
                }
            }
            this.width = previousWidth;
            this.height = previousHeight;
            throw exception;
        }
    }

    public Framebuffer createFramebuffer(boolean useAltBuffers, int[] drawBuffers) {
        requireLive("create framebuffer");
        Objects.requireNonNull(drawBuffers, "drawBuffers");
        requireValidDepthTexture("Framebuffer creation");
        GlFramebuffer glFramebuffer = new GlFramebuffer();
        Framebuffer framebuffer = null;

        try {
            if (drawBuffers.length == 0) {
                RenderTarget target = getRenderTarget(0);
                glFramebuffer.addDepthAttachment(depthTexture);
                glFramebuffer.addColorAttachment(0, target.getMainTexture());
                glFramebuffer.noDrawBuffers();
            } else {
                for (int attachmentIndex = 0; attachmentIndex < drawBuffers.length; attachmentIndex++) {
                    int targetIndex = drawBuffers[attachmentIndex];
                    RenderTarget target = getRenderTarget(targetIndex);
                    int textureId = useAltBuffers ? target.getAltTexture() : target.getMainTexture();
                    glFramebuffer.addColorAttachment(attachmentIndex, textureId);
                }

                int[] logicalBuffers = new int[drawBuffers.length];
                for (int i = 0; i < drawBuffers.length; i++) {
                    logicalBuffers[i] = i;
                }
                glFramebuffer.drawBuffers(logicalBuffers);
                glFramebuffer.readBuffer(0);

                glFramebuffer.addDepthAttachment(depthTexture);
            }

            if (!glFramebuffer.isComplete()) {
                throw new IllegalStateException("Framebuffer creation produced incomplete framebuffer for draw buffers "
                    + Arrays.toString(drawBuffers));
            }

            framebuffer = new Framebuffer(glFramebuffer, drawBuffers);
            ownedFramebuffers.add(framebuffer);
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            try {
                if (framebuffer != null) {
                    try {
                        framebuffer.destroy();
                    } finally {
                        ownedFramebuffers.remove(framebuffer);
                    }
                } else {
                    glFramebuffer.destroy();
                }
            } catch (RuntimeException | Error cleanupException) {
                if (cleanupException != exception) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }
    }

    private void requireValidDepthTexture(String operation) {
        if (depthTexture <= 0) {
            throw new IllegalStateException(operation + " requires an initialized framebuffer depth texture");
        }
    }

    public RenderTarget getRenderTarget(int index) {
        requireLive("read render target");
        RenderTarget target = renderTargets.get(index);
        if (target == null) {
            throw new IllegalArgumentException("Unknown render target index " + index);
        }
        return target;
    }

    public void destroyFramebuffer(Framebuffer framebuffer) {
        requireLive("destroy owned framebuffer");
        if (framebuffer == null) {
            return;
        }
        try {
            framebuffer.destroy();
        } finally {
            ownedFramebuffers.remove(framebuffer);
        }
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        try {
            Throwable failure = null;
            for (Framebuffer framebuffer : ownedFramebuffers) {
                failure = runCleanup(failure, framebuffer::destroy);
            }

            for (RenderTarget target : renderTargets.values()) {
                failure = runCleanup(failure, target::destroy);
            }

            failure = runCleanup(failure, () -> deleteTexture(depthTexture));
            rethrowCleanupFailure(failure);
        } finally {
            ownedFramebuffers.clear();
            renderTargets.clear();
            depthTexture = 0;
            depthTextureVersion = 0;
            width = 0;
            height = 0;
            destroyed = true;
        }
    }

    public int getWidth() {
        requireLive("read framebuffer manager width");
        return width;
    }

    public int getHeight() {
        requireLive("read framebuffer manager height");
        return height;
    }

    public int getDepthTexture() {
        requireLive("read framebuffer manager depth texture");
        return depthTexture;
    }

    public int getDepthTextureVersion() {
        requireLive("read framebuffer manager depth texture version");
        return depthTextureVersion;
    }

    private void requireLive(String operation) {
        if (destroyed) {
            throw new IllegalStateException("Cannot " + operation + " after framebuffer manager was destroyed");
        }
    }

    private void rebuildRenderTargets(int targetWidth, int targetHeight) {
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

        this.width = targetWidth;
        this.height = targetHeight;
    }

    private void rebuildDepthTexture(int targetWidth, int targetHeight) {
        int previousDepthTexture = depthTexture;
        int newDepthTexture = createDepthTexture(targetWidth, targetHeight);
        try {
            reattachOwnedDepthTexture(newDepthTexture);
            depthTexture = newDepthTexture;
            depthTextureVersion++;
        } catch (RuntimeException | Error exception) {
            Throwable failure = null;
            failure = runCleanup(failure, () -> reattachOwnedDepthTexture(previousDepthTexture));
            failure = runCleanup(failure, () -> deleteTexture(newDepthTexture));
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }
        throwPostInstallCleanupFailure(runCleanup(null, () -> deleteTexture(previousDepthTexture)));
    }

    private void reattachOwnedDepthTexture(int replacementDepthTexture) {
        if (replacementDepthTexture == 0) {
            return;
        }

        for (Framebuffer framebuffer : ownedFramebuffers) {
            GlFramebuffer handle = framebuffer.getHandle();
            if (handle.hasDepthAttachment()) {
                handle.addDepthAttachment(replacementDepthTexture);
            }
        }
    }

    private static int createDepthTexture(int width, int height) {
        int texture = createTexture("framebuffer depth texture");
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, NULL_BUFFER);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            });
            return texture;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, runCleanup(null, () -> deleteTexture(texture)));
            throw exception;
        }
    }

    private static int createTexture(String context) {
        int texture = GL11.glGenTextures();
        if (texture <= 0) {
            throw new IllegalStateException("Failed to create " + context);
        }
        return texture;
    }

    private static void deleteTexture(int texture) {
        if (texture <= 0) {
            return;
        }

        Throwable failure = null;
        try {
            GL11.glDeleteTextures(texture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        } finally {
            try {
                TextureLifecycleTracker.onDeleteTexture(texture);
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }

        rethrowCleanupFailure(failure);
    }

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable addCleanupFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }
        if (exception != failure) {
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure) {
        if (primary != null && cleanupFailure != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private static void rethrowCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }

    private static void throwPostInstallCleanupFailure(Throwable failure) {
        if (failure != null) {
            throw new PostInstallCleanupException("Failed to delete replaced framebuffer depth texture", failure);
        }
    }

    private static final class PostInstallCleanupException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private PostInstallCleanupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
