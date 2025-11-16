package net.oculus.gl.program;

import java.util.Objects;
import java.util.function.IntSupplier;

import org.lwjgl.opengl.GL11;

/**
 * Small value object wrapping the logic required to bind a texture before a
 * sampler uniform is updated. Shader packs reference a large catalog of
 * symbolic names (colortex0, gdepthtex, etc.); the binding acts as the bridge
 * between those names and the actual OpenGL texture id that should be sampled.
 */
public final class TextureBinding {
    private static final IntSupplier ZERO = () -> 0;
    private static final TextureBinding UNBOUND = new TextureBinding(GL11.GL_TEXTURE_2D, ZERO);

    private final int target;
    private final IntSupplier textureSupplier;

    private TextureBinding(int target, IntSupplier textureSupplier) {
        this.target = target;
        this.textureSupplier = textureSupplier;
    }

    public static TextureBinding unbound() {
        return UNBOUND;
    }

    public static TextureBinding texture2D(IntSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new TextureBinding(GL11.GL_TEXTURE_2D, supplier);
    }

    public int getTarget() {
        return target;
    }

    public int bind() {
        int texture = textureSupplier != null ? textureSupplier.getAsInt() : 0;
        GL11.glBindTexture(target, texture);
        return texture;
    }

    public void unbind() {
        GL11.glBindTexture(target, 0);
    }
}
