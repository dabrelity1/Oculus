package net.oculus.gl.shader;

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
