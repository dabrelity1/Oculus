package com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer;

import java.util.Arrays;

/**
 * Thin wrapper that pairs a {@link GlFramebuffer} with the logical draw buffer configuration used
 * by the shader pipeline. Future steps will add helpers for state tracking (viewport, blend mode,
 * etc.), but for now it simply exposes the relevant OpenGL object handles.
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
        return handle;
    }

    public int[] getDrawBuffers() {
        return drawBuffers.clone();
    }

    public void bind() {
        handle.bind();
    }

    public void bindForRead() {
        handle.bindAsReadBuffer();
    }

    public void bindForWrite() {
        handle.bindAsDrawBuffer();
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        handle.destroy();
    }

    @Override
    public String toString() {
        return "Framebuffer{" +
            "id=" + handle.getId() +
            ", drawBuffers=" + Arrays.toString(drawBuffers) +
            '}';
    }
}
