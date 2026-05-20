package net.oculus.gl.state;

/**
 * Minimal notification interface used by shader uniforms to know when cached
 * values have changed.
 */
public interface ValueUpdateNotifier {
    /**
     * Registers a listener that will be invoked when the underlying value is
     * updated.
     */
    void setListener(Runnable listener);

    /**
     * Removes a previously registered listener. Implementations that only
     * support one listener may clear their listener state.
     */
    default void removeListener(Runnable listener) {
        setListener(null);
    }
}
