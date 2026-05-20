package com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer;

import java.nio.Buffer;
import java.nio.IntBuffer;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.client.renderer.OpenGlHelper;
import net.oculus.gl.GlResource;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.texture.DepthBufferFormat;
import net.oculus.texture.TextureInfoCache;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Lightweight port of the Iris {@code GlFramebuffer} helper used for lifecycle,
 * attachment wiring, draw-buffer selection, and completeness checks.
 */
public class GlFramebuffer extends GlResource {
    private final Int2IntMap attachments = new Int2IntArrayMap();
    private final int maxDrawBuffers;
    private final int maxColorAttachments;
    private boolean hasDepthAttachment;
    private int depthAttachment;
    private int depthTexture;

    public GlFramebuffer() {
        super(createFramebuffer());
        this.maxDrawBuffers = OculusRenderSystem.getMaxDrawBuffers();
        this.maxColorAttachments = OculusRenderSystem.getMaxColorAttachments();
    }

    private static int createFramebuffer() {
        int framebuffer = OpenGlHelper.glGenFramebuffers();
        if (framebuffer <= 0) {
            throw new IllegalStateException("Failed to create framebuffer");
        }
        return framebuffer;
    }

    public void addDepthAttachment(int texture) {
        int internalFormat = TextureInfoCache.INSTANCE.getInfo(texture).getInternalFormat();
        DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(internalFormat);
        int attachment = depthBufferFormat.isCombinedStencil()
            ? GL30.GL_DEPTH_STENCIL_ATTACHMENT
            : OpenGlHelper.GL_DEPTH_ATTACHMENT;

        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        boolean previousHasDepthAttachment = hasDepthAttachment;
        int previousDepthAttachment = depthAttachment;
        int previousDepthTexture = depthTexture;
        boolean framebufferBound = false;
        Throwable failure = null;
        try {
            bind();
            framebufferBound = true;
            if (hasDepthAttachment && depthAttachment != attachment) {
                OpenGlHelper.glFramebufferTexture2D(
                    OpenGlHelper.GL_FRAMEBUFFER,
                    depthAttachment,
                    GL11.GL_TEXTURE_2D,
                    0,
                    0);
            }
            OpenGlHelper.glFramebufferTexture2D(
                OpenGlHelper.GL_FRAMEBUFFER,
                attachment,
                GL11.GL_TEXTURE_2D,
                texture,
                0);
            hasDepthAttachment = true;
            depthAttachment = attachment;
            depthTexture = texture;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            if (framebufferBound) {
                addSuppressedCleanupFailure(exception, rollbackDepthAttachment(null, previousHasDepthAttachment,
                    previousDepthAttachment, previousDepthTexture));
            }
            throw exception;
        } finally {
            Throwable cleanupFailure = restoreFramebufferBindings(null, previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private Throwable rollbackDepthAttachment(Throwable failure, boolean previousHasDepthAttachment,
                                              int previousDepthAttachment, int previousDepthTexture) {
        if (!previousHasDepthAttachment || previousDepthTexture <= 0) {
            hasDepthAttachment = false;
            depthAttachment = 0;
            depthTexture = 0;
            return failure;
        }

        try {
            OpenGlHelper.glFramebufferTexture2D(
                OpenGlHelper.GL_FRAMEBUFFER,
                previousDepthAttachment,
                GL11.GL_TEXTURE_2D,
                previousDepthTexture,
                0);
            hasDepthAttachment = true;
            depthAttachment = previousDepthAttachment;
            depthTexture = previousDepthTexture;
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    public void addColorAttachment(int index, int texture) {
        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        boolean previousHasColorAttachment = attachments.containsKey(index);
        int previousColorTexture = attachments.getOrDefault(index, 0);
        boolean framebufferBound = false;
        Throwable failure = null;
        try {
            bind();
            framebufferBound = true;
            OpenGlHelper.glFramebufferTexture2D(
                OpenGlHelper.GL_FRAMEBUFFER,
                OpenGlHelper.GL_COLOR_ATTACHMENT0 + index,
                GL11.GL_TEXTURE_2D,
                texture,
                0);
            attachments.put(index, texture);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            if (framebufferBound) {
                addSuppressedCleanupFailure(exception,
                    rollbackColorAttachment(null, index, previousHasColorAttachment, previousColorTexture));
            }
            throw exception;
        } finally {
            Throwable cleanupFailure = restoreFramebufferBindings(null, previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private Throwable rollbackColorAttachment(Throwable failure, int index, boolean previousHasColorAttachment,
                                              int previousColorTexture) {
        int attachment = OpenGlHelper.GL_COLOR_ATTACHMENT0 + index;
        int texture = previousHasColorAttachment ? previousColorTexture : 0;
        try {
            OpenGlHelper.glFramebufferTexture2D(
                OpenGlHelper.GL_FRAMEBUFFER,
                attachment,
                GL11.GL_TEXTURE_2D,
                texture,
                0);
            if (previousHasColorAttachment) {
                attachments.put(index, previousColorTexture);
            } else {
                attachments.remove(index);
            }
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    public void noDrawBuffers() {
        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        Throwable failure = null;
        try {
            bind();
            IntBuffer buffer = BufferUtils.createIntBuffer(1);
            buffer.put(GL11.GL_NONE);
            ((Buffer) buffer).flip();
            OculusRenderSystem.drawBuffers(buffer);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = restoreFramebufferBindings(null, previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    public void drawBuffers(int[] buffers) {
        if (buffers.length > maxDrawBuffers) {
            throw new IllegalArgumentException("Cannot write to more than " + maxDrawBuffers + " buffers on this GPU");
        }

        IntBuffer buffer = BufferUtils.createIntBuffer(buffers.length);
        for (int bufferIndex : buffers) {
            if (bufferIndex >= maxColorAttachments) {
                throw new IllegalArgumentException("Color attachment index " + bufferIndex + " exceeds the GPU limit of " + maxColorAttachments);
            }
            buffer.put(OpenGlHelper.GL_COLOR_ATTACHMENT0 + bufferIndex);
        }
        ((Buffer) buffer).flip();
        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        Throwable failure = null;
        try {
            bind();
            OculusRenderSystem.drawBuffers(buffer);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = restoreFramebufferBindings(null, previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    public void readBuffer(int buffer) {
        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        Throwable failure = null;
        try {
            bind();
            GL11.glReadBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0 + buffer);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = restoreFramebufferBindings(null, previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    public int getColorAttachment(int index) {
        assertValid();
        return attachments.getOrDefault(index, 0);
    }

    public boolean hasDepthAttachment() {
        assertValid();
        return hasDepthAttachment;
    }

    public void bind() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, getGlId());
    }

    public void bindAsReadBuffer() {
        OculusRenderSystem.bindReadFramebuffer(getGlId());
    }

    public void bindAsDrawBuffer() {
        OculusRenderSystem.bindDrawFramebuffer(getGlId());
    }

    @Override
    protected void destroyInternal() {
        OpenGlHelper.glDeleteFramebuffers(getGlId());
    }

    public boolean isComplete() {
        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        Throwable failure = null;
        try {
            bind();
            int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
            return status == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = restoreFramebufferBindings(null, previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    public int getId() {
        return getGlId();
    }

    private static Throwable restoreFramebufferBindings(
            Throwable failure,
            int previousFramebuffer,
            int previousReadFramebuffer,
            int previousDrawFramebuffer) {
        try {
            OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
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
