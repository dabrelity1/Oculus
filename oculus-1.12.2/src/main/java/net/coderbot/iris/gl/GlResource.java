package net.coderbot.iris.gl;

/**
 * Base class for OpenGL-backed objects which expose a numeric handle.
 * The lifecycle helpers make it harder to leak or reuse destroyed resources
 * while still keeping things lightweight for LWJGL 2.
 */
public abstract class GlResource {
    private final int id;
    private boolean valid;

    protected GlResource(int id) {
        this.id = id;
        this.valid = true;
    }

    protected final int getGlId() {
        assertValid();
        return id;
    }

    public final void destroy() {
        if (!valid) {
            return;
        }

        destroyInternal();
        valid = false;
    }

    protected abstract void destroyInternal();

    protected final void assertValid() {
        if (!valid) {
            throw new IllegalStateException("Tried to use a destroyed OpenGL resource");
        }
    }
}
