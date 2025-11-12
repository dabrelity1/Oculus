package org.lwjgl.system;

/**
 * Minimal stub for LWJGL's MemoryUtil APIs. The 1.12.2 runtime ships with LWJGL2,
 * so the actual implementations are unavailable. These methods throw by default
 * and act as placeholders until the native path is implemented.
 */
public final class MemoryUtil {
    private MemoryUtil() {
    }

    public static short memGetShort(long address) {
        throw unsupported();
    }

    public static void memPutShort(long address, short value) {
        throw unsupported();
    }

    public static void memPutInt(long address, int value) {
        throw unsupported();
    }

    public static void memPutFloat(long address, float value) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Direct memory access is not available in the current stub");
    }
}
