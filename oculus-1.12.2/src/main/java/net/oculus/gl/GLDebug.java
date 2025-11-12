package net.oculus.gl;

/**
 * Placeholder for the LWJGL debug helpers used by the 1.16.5 pipeline. The real
 * implementation wires into KHR_debug/ARB_debug_output; for 1.12.2 we only need the
 * API surface so the shader management skeleton compiles.
 */
public final class GLDebug {
    private GLDebug() {
    }

    public static void nameObject(int type, int id, String name) {
        // No-op stub. The 1.12.2 render backend will replace this once real GL bindings exist.
    }
}
