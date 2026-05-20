package com.mojang.blaze3d.platform;

/**
 * Compatibility shell for copied modern-name code that still mentions Mojang's
 * {@code Framebuffer}. Active 1.12 runtime framebuffer work is owned by Minecraft's
 * framebuffer class and the net.oculus framebuffer packages, not by this shell.
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
