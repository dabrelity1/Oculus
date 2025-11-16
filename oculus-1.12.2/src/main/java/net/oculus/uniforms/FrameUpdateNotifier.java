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
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
