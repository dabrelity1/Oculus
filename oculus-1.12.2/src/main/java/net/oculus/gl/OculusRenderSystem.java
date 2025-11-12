package net.oculus.gl;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight shim that emulates the 1.16.5 {@code IrisRenderSystem} entry points. All
 * methods are currently inert; they merely hand out synthetic identifiers so the shader
 * management code can be structured without invoking OpenGL on 1.12.2 yet.
 */
public final class OculusRenderSystem {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private OculusRenderSystem() {
    }

    public static int createShader() {
        return NEXT_ID.getAndIncrement();
    }

    public static void deleteShader(int id) {
        // No-op until the real GL backend is available.
    }

    public static int createProgram() {
        return NEXT_ID.getAndIncrement();
    }

    public static void deleteProgram(int program) {
        // No-op until the real GL backend is available.
    }

    public static void attachShader(int program, int shader) {
        // No-op stub.
    }

    public static void detachShader(int program, int shader) {
        // No-op stub.
    }

    public static void linkProgram(int program) {
        // No-op stub.
    }

    public static void bindAttributeLocation(int program, int index, String name) {
        // No-op stub.
    }

    public static String getProgramInfoLog(int program) {
        return "";
    }

    public static String getShaderInfoLog(int shader) {
        return "";
    }

    public static void useProgram(int program) {
        // No-op stub.
    }

    public static int getUniformLocation(int program, String name) {
        return -1;
    }

    public static String getActiveUniform(int program, int index) {
        return "";
    }
}
