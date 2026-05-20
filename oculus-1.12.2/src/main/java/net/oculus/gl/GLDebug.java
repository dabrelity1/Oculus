package net.oculus.gl;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.KHRDebug;

public final class GLDebug {
    private GLDebug() {
    }

    public static void nameObject(int type, int id, String name) {
        if (id <= 0 || name == null || name.isEmpty()) {
            return;
        }

        try {
            ContextCapabilities capabilities = GLContext.getCapabilities();
            if (capabilities.OpenGL43 || capabilities.GL_KHR_debug) {
                KHRDebug.glObjectLabel(type, id, name);
            }
        } catch (RuntimeException ignored) {
            // No active context or no debug-label support.
        }
    }
}
