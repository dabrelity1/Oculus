package net.oculus.uniforms;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight frame-update broadcaster used by smoothed uniform suppliers.
 */
public final class FrameUpdateNotifier {
    private final List<Runnable> listeners = new ArrayList<>();

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void onNewFrame() {
        Throwable failure = null;
        for (Runnable listener : new ArrayList<>(listeners)) {
            try {
                listener.run();
            } catch (RuntimeException | Error exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    suppressNotifierFailure(failure, exception);
                }
            }
        }

        rethrowNotifierFailure(failure);
    }

    private static void suppressNotifierFailure(Throwable failure, Throwable exception) {
        if (exception != failure) {
            failure.addSuppressed(exception);
        }
    }

    private static void rethrowNotifierFailure(Throwable failure) {
        if (failure == null) {
            return;
        }

        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected frame update notifier failure", failure);
    }
}
