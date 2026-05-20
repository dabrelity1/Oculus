package com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer;

import java.util.Arrays;

/**
 * Thin wrapper that pairs a {@link GlFramebuffer} with the logical draw buffer configuration used
 * by the shader pipeline.
 */
public final class Framebuffer {
    private final GlFramebuffer handle;
    private final int[] drawBuffers;
    private boolean destroyed;

    public Framebuffer(GlFramebuffer handle, int[] drawBuffers) {
        this.handle = handle;
        this.drawBuffers = drawBuffers == null ? new int[0] : drawBuffers.clone();
    }

    public GlFramebuffer getHandle() {
        requireLive("read framebuffer handle");
        return handle;
    }

    public int[] getDrawBuffers() {
        requireLive("read framebuffer draw buffers");
        return drawBuffers.clone();
    }

    public void bind() {
        requireLive("bind framebuffer");
        handle.bind();
    }

    public void bindForRead() {
        requireLive("bind framebuffer for read");
        handle.bindAsReadBuffer();
    }

    public void bindForWrite() {
        requireLive("bind framebuffer for write");
        handle.bindAsDrawBuffer();
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        try {
            handle.destroy();
        } finally {
            destroyed = true;
        }
    }

    private void requireLive(String operation) {
        if (destroyed) {
            throw new IllegalStateException("Cannot " + operation + " after framebuffer was destroyed");
        }
    }

    @Override
    public String toString() {
        return "Framebuffer{" +
            "id=" + handle.getId() +
            ", drawBuffers=" + Arrays.toString(drawBuffers) +
            '}';
    }
}
