package net.oculus.gl.state;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Value notifier that supports every listener attached by the active program.
 */
public final class FanOutValueUpdateNotifier implements ValueUpdateNotifier {
    private final Set<Runnable> listeners = new LinkedHashSet<>();

    @Override
    public void setListener(Runnable listener) {
        if (listener == null) {
            listeners.clear();
            return;
        }

        listeners.add(listener);
    }

    @Override
    public void removeListener(Runnable listener) {
        if (listener == null) {
            return;
        }

        listeners.remove(listener);
    }

    public void notifyListeners() {
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
        throw new IllegalStateException("Unexpected fan-out notifier failure", failure);
    }
}
