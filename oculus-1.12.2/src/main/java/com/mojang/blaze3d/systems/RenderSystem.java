package com.mojang.blaze3d.systems;

import java.util.function.BooleanSupplier;

/**
 * Stub of Mojang's {@code RenderSystem}. Each method mirrors the signature used by the
 * 1.16.5 pipeline but omits any side effects so the 1.12.2 backport can compile before
 * the rendering backend is implemented.
 */
public final class RenderSystem {
    private RenderSystem() {
    }

    public static void initializeShaderPipeline() {
    }

    public static void releaseShaderPipeline() {
    }

    public static void pushMatrix() {
    }

    public static void popMatrix() {
    }

    public static void matrixMode(int mode) {
    }

    public static void loadIdentity() {
    }

    public static void translatef(float x, float y, float z) {
    }

    public static void scalef(float x, float y, float z) {
    }

    public static void color4f(float r, float g, float b, float a) {
    }

    public static void color3f(float r, float g, float b) {
    }

    public static void setShaderColor(float red, float green, float blue, float alpha) {
    }

    public static void blendColor(float red, float green, float blue, float alpha) {
    }

    public static void enableBlend() {
    }

    public static void disableBlend() {
    }

    public static void defaultBlendFunc() {
    }

    public static void enableAlphaTest() {
    }

    public static void disableAlphaTest() {
    }

    public static void defaultAlphaFunc() {
    }

    public static void enableDepthTest() {
    }

    public static void disableDepthTest() {
    }

    public static void depthMask(boolean mask) {
    }

    public static void enableTexture() {
    }

    public static void disableTexture() {
    }

    public static void enableCull() {
    }

    public static void disableCull() {
    }

    public static void activeTexture(int unit) {
    }

    public static void bindTexture(int texture) {
    }

    public static void resetTextureBindings() {
    }

    public static void viewport(int x, int y, int width, int height) {
    }

    public static void clear(int mask, boolean getError) {
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
    }

    public static void assertThread(BooleanSupplier predicate) {
    }

    public static boolean isOnRenderThread() {
        return true;
    }

    public static boolean isOnRenderThreadOrInit() {
        return true;
    }
}
