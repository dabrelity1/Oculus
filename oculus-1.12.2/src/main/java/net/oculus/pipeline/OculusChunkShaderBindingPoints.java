package net.oculus.pipeline;

import me.jellysquid.mods.sodium.client.gl.shader.ShaderBindingPoint;

/**
 * Defines the GLSL attribute binding locations consumed by the Oculus terrain shaders.
 * These indices extend Relictium's {@code ChunkShaderBindingPoints}, which currently reserve
 * slots 0-4 (position, color, tex coord, light coord, model offset).
 */
public final class OculusChunkShaderBindingPoints {
    public static final ShaderBindingPoint NORMAL = new ShaderBindingPoint(5);
    public static final ShaderBindingPoint ENTITY = new ShaderBindingPoint(11);
    public static final ShaderBindingPoint MID_UV = new ShaderBindingPoint(12);
    public static final ShaderBindingPoint TANGENT = new ShaderBindingPoint(13);
    public static final ShaderBindingPoint MID_BLOCK = new ShaderBindingPoint(14);
    public static final ShaderBindingPoint MATERIAL = new ShaderBindingPoint(7);

    private OculusChunkShaderBindingPoints() {
    }
}
