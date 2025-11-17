package com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer;

import java.nio.Buffer;
import java.nio.IntBuffer;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.oculus.gl.GlResource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Lightweight port of the Iris {@code GlFramebuffer} helper. Only the lifecycle and high-level
 * attachment wiring is implemented for now; the more complex validation logic will be filled in
 * once the rest of the rendering pipeline lands.
 */
public class GlFramebuffer extends GlResource {
    private final Int2IntMap attachments = new Int2IntArrayMap();
    private final int maxDrawBuffers;
    private final int maxColorAttachments;
    private boolean hasDepthAttachment;

    public GlFramebuffer() {
        super(GL30.glGenFramebuffers());
        this.maxDrawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        this.maxColorAttachments = GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
    }

    public void addDepthAttachment(int texture) {
        bind();
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
        hasDepthAttachment = true;
    }

    public void addColorAttachment(int index, int texture) {
        bind();
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + index, GL11.GL_TEXTURE_2D, texture, 0);
        attachments.put(index, texture);
    }

    public void noDrawBuffers() {
        bind();
        IntBuffer buffer = BufferUtils.createIntBuffer(1);
    buffer.put(GL11.GL_NONE);
    ((Buffer) buffer).flip();
        GL20.glDrawBuffers(buffer);
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
            buffer.put(GL30.GL_COLOR_ATTACHMENT0 + bufferIndex);
    }
    ((Buffer) buffer).flip();
        bind();
        GL20.glDrawBuffers(buffer);
    }

    public void readBuffer(int buffer) {
        bind();
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + buffer);
    }

    public int getColorAttachment(int index) {
        return attachments.getOrDefault(index, 0);
    }

    public boolean hasDepthAttachment() {
        return hasDepthAttachment;
    }

    public void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getGlId());
    }

    public void bindAsReadBuffer() {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, getGlId());
    }

    public void bindAsDrawBuffer() {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, getGlId());
    }

    @Override
    protected void destroyInternal() {
        GL30.glDeleteFramebuffers(getGlId());
    }

    public boolean isComplete() {
        bind();
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        return status == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    public int getId() {
        return getGlId();
    }
}
