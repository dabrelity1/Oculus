package net.oculus.colorspace;

/**
 * Applies the Oculus screen-space color conversion pass after the shader-pack
 * final pass. This mirrors the 1.16.5 converter contract with an explicit
 * destroy hook for 1.12.2 framebuffer/texture ownership.
 */
public interface ColorSpaceConverter {
    void rebuildProgram(int width, int height, ColorSpace colorSpace);

    void process(int targetTexture);

    default void destroy() {
    }
}
