package com.mojang.blaze3d.platform;

/**
 * Minimal stub of Mojang's {@code Framebuffer}. The 1.12.2 backport only needs the
 * type to exist so the translated RenderSystem API can compile; no behaviour is
 * implemented yet.
 */
public class Framebuffer {
    private int width;
    private int height;

    public Framebuffer(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void bindWrite(boolean setViewport) {
    }

    public void bindRead() {
    }

    public void resize(int width, int height, boolean getError) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getColorTextureId() {
        return 0;
    }

    public int getDepthTextureId() {
        return 0;
    }
}
