package net.oculus.texture.mipmap;

/**
 * Generates mipmap levels for texture data in Minecraft 1.12's ARGB int format.
 */
public interface CustomMipmapGenerator {
    int[][] generateMipLevels(int[] image, int width, int height, int mipLevel);
}
