package net.oculus.postprocess;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Tracks which render target buffers have been "flipped" between their main
 * and alternate textures during composite passes.
 *
 * <p>Shader packs often use ping-pong buffering where a pass reads from one
 * texture and writes to another for the same buffer, then the next pass
 * reads from what was just written. This class tracks which buffers are
 * currently in the "flipped" state.</p>
 */
public final class BufferFlipper {
    private final Set<Integer> flippedBuffers = new HashSet<>();

    /**
     * Flips the specified buffer. If it was in main state, it goes to alt.
     * If it was in alt state, it goes back to main.
     */
    public void flip(int buffer) {
        if (flippedBuffers.contains(buffer)) {
            flippedBuffers.remove(buffer);
        } else {
            flippedBuffers.add(buffer);
        }
    }

    /**
     * Returns true if the specified buffer is currently in the flipped (alt) state.
     */
    public boolean isFlipped(int buffer) {
        return flippedBuffers.contains(buffer);
    }

    /**
     * Returns an iterator over the currently flipped buffers, matching the
     * Iris 1.16.5 helper used by final-pass swap setup.
     */
    public Iterator<Integer> getFlippedBuffers() {
        return flippedBuffers.iterator();
    }

    /**
     * Returns an immutable snapshot of which buffers are currently flipped.
     */
    public ImmutableSet<Integer> snapshot() {
        return ImmutableSet.copyOf(flippedBuffers);
    }

    /**
     * Resets all buffers to the non-flipped (main) state.
     */
    public void reset() {
        flippedBuffers.clear();
    }
}
