package net.oculus.gl.image;

import java.util.function.IntSupplier;

import net.oculus.gl.texture.InternalTextureFormat;

/**
 * Mirrors the Iris image holder interface so render targets can expose texture-backed
 * image units when shader packs opt into compute or image load/store features.
 */
public interface ImageHolder {
    boolean hasImage(String name);

    void addTextureImage(IntSupplier textureId, InternalTextureFormat internalFormat, String name);
}
