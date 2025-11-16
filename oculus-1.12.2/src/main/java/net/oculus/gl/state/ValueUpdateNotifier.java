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
}
