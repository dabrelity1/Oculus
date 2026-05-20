package net.oculus.pipeline;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Objects;
import java.util.function.IntSupplier;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.vendored.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class ClearPass {
    private final Vector4f color;
    private final IntSupplier viewportWidth;
    private final IntSupplier viewportHeight;
    private final GlFramebuffer framebuffer;
    private final int clearFlags;

    public ClearPass(Vector4f color, IntSupplier viewportWidth, IntSupplier viewportHeight,
                     GlFramebuffer framebuffer, int clearFlags) {
        this.color = color;
        this.viewportWidth = Objects.requireNonNull(viewportWidth, "viewportWidth");
        this.viewportHeight = Objects.requireNonNull(viewportHeight, "viewportHeight");
        this.framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
        this.clearFlags = clearFlags;
    }

    public void execute(Vector4f defaultClearColor) {
        Vector4f clearColor = color == null ? Objects.requireNonNull(defaultClearColor, "defaultClearColor") : color;
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);
        FloatBuffer previousClearColor = BufferUtils.createFloatBuffer(16);
        IntBuffer previousViewport = BufferUtils.createIntBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
        GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();

        Throwable failure = null;
        try {
            GL11.glViewport(0, 0, viewportWidth.getAsInt(), viewportHeight.getAsInt());
            framebuffer.bind();
            GlStateManager.colorMask(true, true, true, true);
            GL11.glClearColor(clearColor.x(), clearColor.y(), clearColor.z(), clearColor.w());
            GL11.glClear(clearFlags);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            cleanupFailure = runCleanup(cleanupFailure, () -> restoreColorMask(previousColorMask));
            cleanupFailure = runCleanup(cleanupFailure, () -> GL11.glClearColor(
                previousClearColor.get(0),
                previousClearColor.get(1),
                previousClearColor.get(2),
                previousClearColor.get(3)));
            cleanupFailure = runCleanup(cleanupFailure, () -> OculusRenderSystem.restoreFramebufferBindings(
                previousFramebuffer, previousReadFramebuffer, previousDrawFramebuffer));
            cleanupFailure = runCleanup(cleanupFailure, () -> GL11.glViewport(
                previousViewport.get(0),
                previousViewport.get(1),
                previousViewport.get(2),
                previousViewport.get(3)));
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    public GlFramebuffer getFramebuffer() {
        return framebuffer;
    }

    private static void restoreColorMask(ByteBuffer previousColorMask) {
        GlStateManager.colorMask(
            previousColorMask.get(0) != 0,
            previousColorMask.get(1) != 0,
            previousColorMask.get(2) != 0,
            previousColorMask.get(3) != 0);
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
