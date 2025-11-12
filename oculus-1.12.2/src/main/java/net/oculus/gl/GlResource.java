package net.oculus.gl;

/**
 * Minimal stand-in for the Iris GL resource wrapper. Responsible for tracking the
 * lifecycle of OpenGL objects created by the Oculus shader pipeline. The implementation
 * matches the 1.16.5 layout closely enough for downstream ports while remaining inert
 * on 1.12.2 until the backing GL calls are wired up.
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

        destroyInternal();
        valid = false;
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
