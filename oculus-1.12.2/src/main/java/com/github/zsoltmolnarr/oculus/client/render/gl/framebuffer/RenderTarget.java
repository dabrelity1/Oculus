package com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer;

import java.nio.ByteBuffer;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.gl.texture.PixelFormat;
import net.oculus.gl.texture.PixelType;
import net.oculus.texture.TextureLifecycleTracker;
import net.oculus.vendored.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Port of the Iris {@code RenderTarget} helper. Each target owns a pair of textures so shader packs
 * can request flipped buffers during composite passes.
 */
public class RenderTarget {
    private static final ByteBuffer NULL_BUFFER = null;

    private final InternalTextureFormat internalFormat;
    private final PixelFormat format;
    private final PixelType type;
    private int width;
    private int height;

    private boolean valid;
    private final int mainTexture;
    private final int altTexture;

    private RenderTarget(Builder builder) {
        this.internalFormat = builder.internalFormat;
        this.format = builder.pixelFormat;
        this.type = builder.pixelType;
        this.width = builder.width;
        this.height = builder.height;
        this.valid = true;

        int createdMainTexture = 0;
        int createdAltTexture = 0;
        try {
            createdMainTexture = createTexture("main render target texture");
            createdAltTexture = createTexture("alternate render target texture");
            final int setupMainTexture = createdMainTexture;
            final int setupAltTexture = createdAltTexture;
            boolean allowsLinear = !builder.internalFormat.getPixelFormat().isInteger();
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                setupTexture(setupMainTexture, builder.width, builder.height, allowsLinear);
                setupTexture(setupAltTexture, builder.width, builder.height, allowsLinear);
            });
        } catch (RuntimeException | Error exception) {
            Throwable failure = null;
            final int failedMainTexture = createdMainTexture;
            final int failedAltTexture = createdAltTexture;
            failure = runCleanup(failure, () -> deleteTexture(failedMainTexture));
            failure = runCleanup(failure, () -> deleteTexture(failedAltTexture));
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        this.mainTexture = createdMainTexture;
        this.altTexture = createdAltTexture;
    }

    private static int createTexture(String context) {
        int texture = GL11.glGenTextures();
        if (texture <= 0) {
            throw new IllegalStateException("Failed to create " + context);
        }
        return texture;
    }

    private void setupTexture(int texture, int width, int height, boolean allowsLinear) {
        resizeTexture(texture, width, height);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
            allowsLinear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
            allowsLinear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private void resizeTexture(int texture, int width, int height) {
        OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, internalFormat.getGlFormat(),
            width, height, 0, format.getGlFormat(), type.getGlFormat(), NULL_BUFFER);
    }

    public void resize(Vector2i override) {
        resize(override.x(), override.y());
    }

    public void resize(int width, int height) {
        requireValid();
        OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
            resizeTexture(mainTexture, width, height);
            resizeTexture(altTexture, width, height);
        });
        this.width = width;
        this.height = height;
    }

    public int getMainTexture() {
        requireValid();
        return mainTexture;
    }

    public int getAltTexture() {
        requireValid();
        return altTexture;
    }

    public int getWidth() {
        requireValid();
        return width;
    }

    public int getHeight() {
        requireValid();
        return height;
    }

    public InternalTextureFormat getInternalFormat() {
        requireValid();
        return internalFormat;
    }

    public void destroy() {
        if (!valid) {
            return;
        }
        valid = false;
        Throwable failure = null;
        failure = runCleanup(failure, () -> deleteTexture(mainTexture));
        failure = runCleanup(failure, () -> deleteTexture(altTexture));
        rethrowCleanupFailure(failure);
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

    private void requireValid() {
        if (!valid) {
            throw new IllegalStateException("Attempted to use a destroyed render target");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private InternalTextureFormat internalFormat = InternalTextureFormat.RGBA8;
        private PixelFormat pixelFormat = PixelFormat.RGBA;
        private PixelType pixelType = PixelType.UNSIGNED_BYTE;
        private int width = 1;
        private int height = 1;

        private Builder() {
        }

        public Builder setInternalFormat(InternalTextureFormat format) {
            this.internalFormat = format;
            return this;
        }

        public Builder setPixelFormat(PixelFormat pixelFormat) {
            this.pixelFormat = pixelFormat;
            return this;
        }

        public Builder setPixelType(PixelType pixelType) {
            this.pixelType = pixelType;
            return this;
        }

        public Builder setDimensions(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Render target dimensions must be positive");
            }
            this.width = width;
            this.height = height;
            return this;
        }

        public RenderTarget build() {
            return new RenderTarget(this);
        }
    }
}
