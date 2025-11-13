package com.mojang.blaze3d.systems;

import java.util.function.BooleanSupplier;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/**
 * Translation facade for Mojang's {@code RenderSystem}. Each method mirrors the
 * 1.16.5 signature but forwards to the closest 1.12.2-era implementation so
 * existing rendering logic keeps working.
 */
public final class RenderSystem {
    private static final int MAX_TEXTURE_UNITS = 16;

    private RenderSystem() {
    }

    public static void initializeShaderPipeline() {
        // 1.12.2 has no concept of the shader pipeline bootstrap.
    }

    public static void releaseShaderPipeline() {
        // Nothing to release in the legacy pipeline.
    }

    public static void pushMatrix() {
        GlStateManager.pushMatrix();
    }

    public static void popMatrix() {
        GlStateManager.popMatrix();
    }

    public static void matrixMode(int mode) {
        GlStateManager.matrixMode(mode);
    }

    public static void loadIdentity() {
        GlStateManager.loadIdentity();
    }

    public static void translatef(float x, float y, float z) {
        GlStateManager.translate(x, y, z);
    }

    public static void scalef(float x, float y, float z) {
        GlStateManager.scale(x, y, z);
    }

    public static void color4f(float red, float green, float blue, float alpha) {
        GlStateManager.color(red, green, blue, alpha);
    }

    public static void color3f(float red, float green, float blue) {
        GlStateManager.color(red, green, blue, 1.0F);
    }

    public static void setShaderColor(float red, float green, float blue, float alpha) {
        GlStateManager.color(red, green, blue, alpha);
    }

    public static void blendColor(float red, float green, float blue, float alpha) {
        GL14.glBlendColor(red, green, blue, alpha);
    }

    public static void enableBlend() {
        GlStateManager.enableBlend();
    }

    public static void disableBlend() {
        GlStateManager.disableBlend();
    }

    public static void defaultBlendFunc() {
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void enableAlphaTest() {
        GlStateManager.enableAlpha();
    }

    public static void disableAlphaTest() {
        GlStateManager.disableAlpha();
    }

    public static void defaultAlphaFunc() {
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    }

    public static void enableDepthTest() {
        GlStateManager.enableDepth();
    }

    public static void disableDepthTest() {
        GlStateManager.disableDepth();
    }

    public static void depthMask(boolean flag) {
        GlStateManager.depthMask(flag);
    }

    public static void enableTexture() {
        GlStateManager.enableTexture2D();
    }

    public static void disableTexture() {
        GlStateManager.disableTexture2D();
    }

    public static void enableCull() {
        GlStateManager.enableCull();
    }

    public static void disableCull() {
        GlStateManager.disableCull();
    }

    public static void activeTexture(int texture) {
        GlStateManager.setActiveTexture(texture);
    }

    public static void bindTexture(int texture) {
        GlStateManager.bindTexture(texture);
    }

    public static void resetTextureBindings() {
        for (int unit = 0; unit < MAX_TEXTURE_UNITS; unit++) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager.bindTexture(0);
        }

        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
    }

    public static void viewport(int x, int y, int width, int height) {
        GlStateManager.viewport(x, y, width, height);
    }

    public static void clear(int mask, boolean getError) {
        GlStateManager.clear(mask);
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
        GlStateManager.clearColor(red, green, blue, alpha);
    }

    public static void assertThread(BooleanSupplier predicate) {
        predicate.getAsBoolean();
    }

    public static boolean isOnRenderThread() {
        return true;
    }

    public static boolean isOnRenderThreadOrInit() {
        return true;
    }
}
