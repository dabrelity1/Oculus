package net.oculus.gl.image;

import java.util.Objects;
import java.util.function.IntSupplier;

import org.lwjgl.opengl.GL15;

import net.oculus.gl.OculusRenderSystem;

/**
 * Represents a single image binding between a texture ID and an image unit.
 */
public final class ImageBinding {
    private final int imageUnit;
    private final int internalFormat;
    private final IntSupplier textureId;

    public ImageBinding(int imageUnit, int internalFormat, IntSupplier textureId) {
        this.imageUnit = imageUnit;
        this.internalFormat = internalFormat;
        this.textureId = Objects.requireNonNull(textureId, "textureId");
    }

    public void update() {
        OculusRenderSystem.bindImageTexture(imageUnit, textureId.getAsInt(), 0, true, 0, GL15.GL_READ_WRITE, internalFormat);
    }
}
