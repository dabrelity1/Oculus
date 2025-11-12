package net.oculus.shaderpack;

/**
 * Lightweight placeholder for shader pack metadata. The 1.12.2 port only
 * tracks a human-readable name, deferring option parsing to later steps.
 */
public final class ShaderPack {
    private final String name;

    private ShaderPack(String name) {
        this.name = name;
    }

    public static ShaderPack placeholder() {
        return new ShaderPack("placeholder");
    }

    public String getName() {
        return name;
    }
}
