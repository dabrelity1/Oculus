package net.coderbot.iris.gl;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

/**
 * Minimal GL version helper that works with the LWJGL 2 {@link ContextCapabilities} API.
 */
public enum GlVersion {
    GL11,
    GL12,
    GL13,
    GL20,
    GL21,
    GL30;

    public static GlVersion detect() {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps == null) {
            return GL11;
        }

        return fromCapabilities(caps);
    }

    public static GlVersion fromCapabilities(ContextCapabilities caps) {
        if (caps.OpenGL30) {
            return GL30;
        }
        if (caps.OpenGL21) {
            return GL21;
        }
        if (caps.OpenGL20) {
            return GL20;
        }
        if (caps.OpenGL13) {
            return GL13;
        }
        if (caps.OpenGL12) {
            return GL12;
        }

        return GL11;
    }
}
