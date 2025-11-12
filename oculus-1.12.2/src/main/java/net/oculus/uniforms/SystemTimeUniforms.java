package net.oculus.uniforms;

/**
 * Barebones clone of the Iris system time uniform helpers. The counters do nothing yet but
 * allow the shader management code to compile while the full timing integration is ported
 * over.
 */
public final class SystemTimeUniforms {
    public static final Counter COUNTER = new Counter();
    public static final Timer TIMER = new Timer();

    private SystemTimeUniforms() {
    }

    public static final class Counter {
        public void reset() {
        }
    }

    public static final class Timer {
        public void reset() {
        }
    }
}
