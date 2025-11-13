package net.oculus.gl.shader;

import net.oculus.gl.OculusRenderSystem;

/**
 * Centralises shader source uploads so platform specific workarounds can live in one
 * place. On 1.12.2 we simply forward to {@link org.lwjgl.opengl.GL20#glShaderSource(int, CharSequence)}.
 */
public final class ShaderWorkarounds {
    private ShaderWorkarounds() {
    }

    public static void safeShaderSource(int shaderHandle, String source) {
        if (source == null) {
            throw new IllegalArgumentException("Shader source cannot be null");
        }

        OculusRenderSystem.glShaderSource(shaderHandle, source);
    }
}
