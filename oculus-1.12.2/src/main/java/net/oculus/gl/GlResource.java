package net.oculus.gl;

/**
 * Base wrapper for OpenGL objects owned by the active Oculus shader pipeline.
 * Subclasses perform the real LWJGL/GlStateManager deletion in destroyInternal();
 * this class only centralizes the 1.16.5-style validity guard.
 */
public abstract class GlResource {
    private final int id;
    private boolean valid;

    protected GlResource(int id) {
        this.id = id;
        this.valid = true;
    }

    public final void destroy() {
        if (!valid) {
            return;
        }

        try {
            destroyInternal();
        } finally {
            valid = false;
        }
    }

    protected abstract void destroyInternal();

    protected void assertValid() {
        if (!valid) {
            throw new IllegalStateException("Tried to use a destroyed GlResource");
        }
    }

    protected int getGlId() {
        assertValid();
        return id;
    }
}
