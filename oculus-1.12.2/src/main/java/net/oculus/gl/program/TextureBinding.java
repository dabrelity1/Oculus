package net.oculus.gl.program;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.oculus.gl.OculusRenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

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
    private final IntConsumer textureConfigurator;

    private TextureBinding(int target, IntSupplier textureSupplier) {
        this(target, textureSupplier, null);
    }

    private TextureBinding(int target, IntSupplier textureSupplier, IntConsumer textureConfigurator) {
        this.target = target;
        this.textureSupplier = textureSupplier;
        this.textureConfigurator = textureConfigurator;
    }

    public static TextureBinding unbound() {
        return UNBOUND;
    }

    public static TextureBinding texture2D(IntSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new TextureBinding(GL11.GL_TEXTURE_2D, supplier);
    }

    public static TextureBinding texture2D(IntSupplier supplier, IntConsumer textureConfigurator) {
        Objects.requireNonNull(supplier, "supplier");
        return new TextureBinding(GL11.GL_TEXTURE_2D, supplier, textureConfigurator);
    }

    public static TextureBinding texture3D(IntSupplier supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new TextureBinding(GL12.GL_TEXTURE_3D, supplier);
    }

    public int getTarget() {
        return target;
    }

    public int bind() {
        int texture = getTextureId();
        GL11.glBindTexture(target, texture);
        configureTexture(texture);
        return texture;
    }

    public int bindToUnit(int textureUnit) {
        int texture = getTextureId();
        if (target == GL11.GL_TEXTURE_2D) {
            OculusRenderSystem.bindSamplerTexture2DToUnit(textureUnit, texture);
        } else {
            OculusRenderSystem.setActiveTextureUnit(GL13.GL_TEXTURE0 + textureUnit);
            GL11.glBindTexture(target, texture);
        }
        configureTexture(texture);
        return texture;
    }

    public int getTextureId() {
        int texture = textureSupplier != null ? textureSupplier.getAsInt() : 0;
        return texture;
    }

    public void unbind() {
        GL11.glBindTexture(target, 0);
    }

    public void unbindFromUnit(int textureUnit) {
        if (target == GL11.GL_TEXTURE_2D) {
            OculusRenderSystem.bindSamplerTexture2DToUnit(textureUnit, 0);
        } else {
            OculusRenderSystem.setActiveTextureUnit(GL13.GL_TEXTURE0 + textureUnit);
            GL11.glBindTexture(target, 0);
        }
    }

    private void configureTexture(int texture) {
        if (texture > 0 && textureConfigurator != null) {
            textureConfigurator.accept(texture);
        }
    }
}
