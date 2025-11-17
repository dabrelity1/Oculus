package com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer;

import java.nio.ByteBuffer;

import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.gl.texture.PixelFormat;
import net.oculus.gl.texture.PixelType;
import net.oculus.vendored.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/**
 * Port of the Iris {@code RenderTarget} helper. Each target owns a pair of textures so shader packs
 * can request flipped buffers during composite passes. The heavy lifting (copy strategies, mipmap
 * generation, etc.) will be filled in later.
 */
public class RenderTarget {
    private static final ByteBuffer NULL_BUFFER = null;

    private final InternalTextureFormat internalFormat;
    private final PixelFormat format;
    private final PixelType type;
    private int width;
    private int height;

    private boolean valid;
    private final int mainTexture;
    private final int altTexture;

    private RenderTarget(Builder builder) {
        this.internalFormat = builder.internalFormat;
        this.format = builder.pixelFormat;
        this.type = builder.pixelType;
        this.width = builder.width;
        this.height = builder.height;
        this.valid = true;

        this.mainTexture = GL11.glGenTextures();
        this.altTexture = GL11.glGenTextures();

        boolean allowsLinear = !builder.internalFormat.getPixelFormat().isInteger();
        setupTexture(mainTexture, builder.width, builder.height, allowsLinear);
        setupTexture(altTexture, builder.width, builder.height, allowsLinear);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void setupTexture(int texture, int width, int height, boolean allowsLinear) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, allowsLinear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, allowsLinear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        resizeTexture(texture, width, height);
    }

    private void resizeTexture(int texture, int width, int height) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat.getGlFormat(), width, height, 0, format.getGlFormat(), type.getGlFormat(), NULL_BUFFER);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public void resize(Vector2i override) {
        resize(override.x(), override.y());
    }

    public void resize(int width, int height) {
        requireValid();
        this.width = width;
        this.height = height;
        resizeTexture(mainTexture, width, height);
        resizeTexture(altTexture, width, height);
    }

    public int getMainTexture() {
        requireValid();
        return mainTexture;
    }

    public int getAltTexture() {
        requireValid();
        return altTexture;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public InternalTextureFormat getInternalFormat() {
        return internalFormat;
    }

    public void destroy() {
        if (!valid) {
            return;
        }
        valid = false;
        GL11.glDeleteTextures(mainTexture);
        GL11.glDeleteTextures(altTexture);
    }

    private void requireValid() {
        if (!valid) {
            throw new IllegalStateException("Attempted to use a destroyed render target");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private InternalTextureFormat internalFormat = InternalTextureFormat.RGBA8;
        private PixelFormat pixelFormat = PixelFormat.RGBA;
        private PixelType pixelType = PixelType.UNSIGNED_BYTE;
        private int width = 1;
        private int height = 1;

        private Builder() {
        }

        public Builder setInternalFormat(InternalTextureFormat format) {
            this.internalFormat = format;
            return this;
        }

        public Builder setPixelFormat(PixelFormat pixelFormat) {
            this.pixelFormat = pixelFormat;
            return this;
        }

        public Builder setPixelType(PixelType pixelType) {
            this.pixelType = pixelType;
            return this;
        }

        public Builder setDimensions(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Render target dimensions must be positive");
            }
            this.width = width;
            this.height = height;
            return this;
        }

        public RenderTarget build() {
            return new RenderTarget(this);
        }
    }
}
