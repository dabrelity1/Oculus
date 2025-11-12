package com.mojang.blaze3d.systems;

import java.util.function.BooleanSupplier;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Translation Facade for Mojang's {@code RenderSystem}. Each method mirrors the signature
 * used by the 1.16.5 pipeline but delegates to the appropriate 1.12.2 {@code GlStateManager}
 * or OpenGL call to provide actual functionality.
 */
public final class RenderSystem {
    private RenderSystem() {
    }

    /**
     * No direct equivalent in 1.12.2; used for 1.16.5 shader initialization.
     */
    public static void initializeShaderPipeline() {
        // No 1.12.2 equivalent - modern shader initialization
    }

    /**
     * No direct equivalent in 1.12.2; used for 1.16.5 shader cleanup.
     */
    public static void releaseShaderPipeline() {
        // No 1.12.2 equivalent - modern shader cleanup
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

    public static void color4f(float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a);
    }

    public static void color3f(float r, float g, float b) {
        GlStateManager.color(r, g, b, 1.0F);
    }

    public static void setShaderColor(float red, float green, float blue, float alpha) {
        // In 1.16.5 this sets shader uniform color; in 1.12.2 we use fixed-function color
        GlStateManager.color(red, green, blue, alpha);
    }

    public static void blendColor(float red, float green, float blue, float alpha) {
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.CONSTANT_COLOR.factor,
            GlStateManager.DestFactor.ONE_MINUS_CONSTANT_COLOR.factor,
            GlStateManager.SourceFactor.CONSTANT_ALPHA.factor,
            GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA.factor
        );
        GL11.glBlendColor(red, green, blue, alpha);
    }

    public static void enableBlend() {
        GlStateManager.enableBlend();
    }

    public static void disableBlend() {
        GlStateManager.disableBlend();
    }

    public static void defaultBlendFunc() {
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA.factor,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.factor,
            GlStateManager.SourceFactor.ONE.factor,
            GlStateManager.DestFactor.ZERO.factor
        );
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

    public static void depthMask(boolean mask) {
        GlStateManager.depthMask(mask);
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

    public static void activeTexture(int unit) {
        GlStateManager.setActiveTexture(unit);
    }

    public static void bindTexture(int texture) {
        GlStateManager.bindTexture(texture);
    }

    public static void resetTextureBindings() {
        // Reset all texture units to 0
        for (int i = 0; i < 16; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }
        // Reset back to texture unit 0
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
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

    /**
     * No direct equivalent in 1.12.2; used for thread safety checks in 1.16.5.
     */
    public static void assertThread(BooleanSupplier predicate) {
        // No 1.12.2 equivalent - modern render thread assertion
    }

    public static boolean isOnRenderThread() {
        return true;
    }

    public static boolean isOnRenderThreadOrInit() {
        return true;
    }
}
