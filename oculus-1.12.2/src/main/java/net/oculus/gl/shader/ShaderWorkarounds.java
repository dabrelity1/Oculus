package net.oculus.gl.shader;

/**
 * The 1.16.5 pipeline centralises shader source uploads through this helper so that
 * vendor quirks can be addressed in one place. For now we simply expose the same
 * method signature without any behaviour.
 */
public final class ShaderWorkarounds {
    private ShaderWorkarounds() {
    }

    public static void safeShaderSource(int shaderHandle, String source) {
        // No-op stub. Actual implementations will forward to the GL driver.
    }
}
