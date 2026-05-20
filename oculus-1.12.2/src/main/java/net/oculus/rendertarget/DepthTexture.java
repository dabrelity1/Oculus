package net.oculus.rendertarget;

import java.nio.ByteBuffer;

import net.oculus.gl.GlResource;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.texture.DepthBufferFormat;
import net.oculus.texture.TextureLifecycleTracker;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class DepthTexture extends GlResource {
    DepthTexture(int width, int height, DepthBufferFormat format) {
        super(createTexture("depth render target texture"));

        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                allocate(width, height, format);
                OculusRenderSystem.texParameteri(getGlId(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                OculusRenderSystem.texParameteri(getGlId(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                OculusRenderSystem.texParameteri(getGlId(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texParameteri(getGlId(), GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            });
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, runCleanup(null, this::destroy));
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

    void resize(int width, int height, DepthBufferFormat format) {
        OculusRenderSystem.withDefaultTextureBindingRestored(() -> allocate(width, height, format));
    }

    private void allocate(int width, int height, DepthBufferFormat format) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        OculusRenderSystem.texImage2D(
            getGlId(),
            GL11.GL_TEXTURE_2D,
            0,
            format.getGlInternalFormat(),
            safeWidth,
            safeHeight,
            0,
            format.getGlPixelFormat(),
            format.getGlPixelType(),
            (ByteBuffer) null
        );
    }

    public int getTextureId() {
        return getGlId();
    }

    @Override
    protected void destroyInternal() {
        int texture = getGlId();
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
}
