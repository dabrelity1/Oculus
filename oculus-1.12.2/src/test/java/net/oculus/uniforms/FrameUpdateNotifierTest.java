package net.oculus.uniforms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public class FrameUpdateNotifierTest {
    @Test
    public void onNewFrameAttemptsEveryListenerBeforeRethrowingFirstFailure() {
        FrameUpdateNotifier notifier = new FrameUpdateNotifier();
        int[] calls = new int[3];
        RuntimeException first = new RuntimeException("first");
        Error second = new AssertionError("second");

        notifier.addListener(() -> {
            calls[0]++;
            throw first;
        });
        notifier.addListener(() -> {
            calls[1]++;
            throw second;
        });
        notifier.addListener(() -> calls[2]++);

        try {
            notifier.onNewFrame();
            fail("Expected the first listener failure to be rethrown");
        } catch (RuntimeException exception) {
            assertSame(first, exception);
            assertArrayEquals(new int[] {1, 1, 1}, calls);
            assertArrayEquals(new Throwable[] {second}, exception.getSuppressed());
        }
    }

    @Test
    public void onNewFrameKeepsOriginalFailureWhenListenersShareThrowableInstance() {
        FrameUpdateNotifier notifier = new FrameUpdateNotifier();
        int[] calls = new int[2];
        RuntimeException shared = new RuntimeException("shared");

        notifier.addListener(() -> {
            calls[0]++;
            throw shared;
        });
        notifier.addListener(() -> {
            calls[1]++;
            throw shared;
        });

        try {
            notifier.onNewFrame();
            fail("Expected the shared listener failure to be rethrown");
        } catch (RuntimeException exception) {
            assertSame(shared, exception);
            assertArrayEquals(new int[] {1, 1}, calls);
            assertArrayEquals(new Throwable[0], exception.getSuppressed());
        }
    }

    @Test
    public void onNewFrameUsesSnapshotWhenListenersAddListeners() {
        FrameUpdateNotifier notifier = new FrameUpdateNotifier();
        int[] calls = new int[2];

        notifier.addListener(() -> {
            calls[0]++;
            notifier.addListener(() -> calls[1]++);
        });

        notifier.onNewFrame();
        assertArrayEquals(new int[] {1, 0}, calls);

        notifier.onNewFrame();
        assertArrayEquals(new int[] {2, 1}, calls);
    }
}
