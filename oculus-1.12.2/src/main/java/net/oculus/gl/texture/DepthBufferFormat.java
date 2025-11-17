package net.oculus.gl.texture;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Simple mirror of the Iris depth buffer format enumeration. The concrete OpenGL wiring is
 * intentionally lightweight for now so that framebuffer scaffolding can compile on 1.12.2.
 */
public enum DepthBufferFormat {
    DEPTH(false),
    DEPTH16(false),
    DEPTH24(false),
    DEPTH32(false),
    DEPTH32F(false),
    DEPTH_STENCIL(true),
    DEPTH24_STENCIL8(true),
    DEPTH32F_STENCIL8(true);

    private final boolean combinedStencil;

    DepthBufferFormat(boolean combinedStencil) {
        this.combinedStencil = combinedStencil;
    }

    public static DepthBufferFormat fromGlEnumOrDefault(int glenum) {
        DepthBufferFormat format = fromGlEnum(glenum);
        return format == null ? DEPTH : format;
    }

    public static DepthBufferFormat fromGlEnum(int glenum) {
        switch (glenum) {
            case GL11.GL_DEPTH_COMPONENT:
                return DEPTH;
            case GL14.GL_DEPTH_COMPONENT16:
                return DEPTH16;
            case GL14.GL_DEPTH_COMPONENT24:
                return DEPTH24;
            case GL14.GL_DEPTH_COMPONENT32:
                return DEPTH32;
            case GL30.GL_DEPTH_COMPONENT32F:
                return DEPTH32F;
            case GL30.GL_DEPTH_STENCIL:
                return DEPTH_STENCIL;
            case GL30.GL_DEPTH24_STENCIL8:
                return DEPTH24_STENCIL8;
            case GL30.GL_DEPTH32F_STENCIL8:
                return DEPTH32F_STENCIL8;
            default:
                return null;
        }
    }

    public int getGlInternalFormat() {
        switch (this) {
            case DEPTH:
                return GL11.GL_DEPTH_COMPONENT;
            case DEPTH16:
                return GL14.GL_DEPTH_COMPONENT16;
            case DEPTH24:
                return GL14.GL_DEPTH_COMPONENT24;
            case DEPTH32:
                return GL14.GL_DEPTH_COMPONENT32;
            case DEPTH32F:
                return GL30.GL_DEPTH_COMPONENT32F;
            case DEPTH_STENCIL:
                return GL30.GL_DEPTH_STENCIL;
            case DEPTH24_STENCIL8:
                return GL30.GL_DEPTH24_STENCIL8;
            case DEPTH32F_STENCIL8:
                return GL30.GL_DEPTH32F_STENCIL8;
            default:
                throw new IllegalStateException("Unknown depth buffer format " + this);
        }
    }

    public int getGlFormat() {
        switch (this) {
            case DEPTH:
            case DEPTH16:
                return GL11.GL_UNSIGNED_SHORT;
            case DEPTH24:
            case DEPTH32:
                return GL11.GL_UNSIGNED_INT;
            case DEPTH32F:
                return GL11.GL_FLOAT;
            case DEPTH_STENCIL:
            case DEPTH24_STENCIL8:
                return GL30.GL_UNSIGNED_INT_24_8;
            case DEPTH32F_STENCIL8:
                return GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
            default:
                throw new IllegalStateException("Unknown depth buffer format " + this);
        }
    }

    public int getGlType() {
    return combinedStencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT;
    }

    public boolean isCombinedStencil() {
        return combinedStencil;
    }
}
