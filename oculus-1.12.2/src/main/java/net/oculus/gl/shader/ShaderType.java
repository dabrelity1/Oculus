package net.oculus.gl.shader;

/**
 * Mirrors the 1.16.5 shader type enumeration so loaders can request vertex, geometry,
 * fragment, or compute stages. The numeric identifiers are placeholders until the GL
 * bindings are finished.
 */
public enum ShaderType {
    VERTEX(0x8B31),
    GEOMETRY(0x8DD9),
    FRAGMENT(0x8B30),
    COMPUTE(0x91B9);

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }
}
