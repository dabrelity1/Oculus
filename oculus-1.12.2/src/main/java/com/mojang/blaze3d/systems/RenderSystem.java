package com.mojang.blaze3d.systems;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Lightweight emulation of Mojang's RenderSystem facade used by the Iris/Oculus
 * pipeline. The real 1.16+ implementation performs thread checks and defers to
 * Blaze3D state trackers; the 1.12.2 port only needs basic GL state control to
 * keep the translated code compiling. Methods implemented here should match
 * the signatures invoked by the deferred rendering pipeline and its helpers.
 */
public final class RenderSystem {
    private RenderSystem() {
    }

    public static void initializeShaderPipeline() {
        // No state to initialise yet; placeholder for future wiring.
    }

    public static void releaseShaderPipeline() {
        // Nothing to release in the stub implementation.
    }

    public static void pushMatrix() {
        GL11.glPushMatrix();
    }

    public static void popMatrix() {
        GL11.glPopMatrix();
    }

    public static void matrixMode(int mode) {
        GL11.glMatrixMode(mode);
    }

    public static void loadIdentity() {
        GL11.glLoadIdentity();
    }

    public static void translatef(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
    }

    public static void scalef(float x, float y, float z) {
        GL11.glScalef(x, y, z);
    }

    public static void color4f(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }

    public static void color3f(float r, float g, float b) {
        GL11.glColor3f(r, g, b);
    }

    public static void setShaderColor(float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
    }

    public static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }

    public static void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void enableAlphaTest() {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    public static void disableAlphaTest() {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
    }

    public static void enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public static void disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    public static void depthMask(boolean mask) {
        GL11.glDepthMask(mask);
    }

    public static void enableTexture() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void disableTexture() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    public static void enableCull() {
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    public static void disableCull() {
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    public static void activeTexture(int unit) {
        GL13.glActiveTexture(unit);
    }

    public static void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    public static void resetTextureBindings() {
        // Reset to texture unit 0 and unbind; sufficient for shader reload paths.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public static void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    public static void clear(int mask, boolean getError) {
        GL11.glClear(mask);
        if (getError) {
            GL11.glGetError();
        }
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }
}
