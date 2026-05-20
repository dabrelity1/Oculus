package net.oculus.gl.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

public class StateUpdateNotifiersTest {
    @After
    public void tearDown() {
        StateUpdateNotifiers.blendFuncNotifier.setListener(null);
        StateUpdateNotifiers.bindTextureNotifier.setListener(null);
        StateUpdateNotifiers.fogToggleNotifier.setListener(null);
        StateUpdateNotifiers.fogModeNotifier.setListener(null);
        StateUpdateNotifiers.fogStartNotifier.setListener(null);
        StateUpdateNotifiers.fogEndNotifier.setListener(null);
        StateUpdateNotifiers.fogDensityNotifier.setListener(null);
        StateUpdateNotifiers.normalTextureChangeNotifier.setListener(null);
        StateUpdateNotifiers.specularTextureChangeNotifier.setListener(null);
        StateUpdateNotifiers.phaseChangeNotifier.setListener(null);
    }

    @Test
    public void blendFuncNotifierPublishesStateChanges() {
        AtomicInteger calls = new AtomicInteger();

        StateUpdateNotifiers.blendFuncNotifier.setListener(calls::incrementAndGet);
        StateUpdateNotifiers.notifyBlendFuncChanged();

        assertEquals(1, calls.get());
    }

    @Test
    public void textureBindingNotifierPublishesStateChanges() {
        AtomicInteger calls = new AtomicInteger();

        StateUpdateNotifiers.bindTextureNotifier.setListener(calls::incrementAndGet);
        StateUpdateNotifiers.notifyTextureBindingChanged();

        assertEquals(1, calls.get());
    }

    @Test
    public void publicNotifierHandlesCannotDriftFromTheirPublisherBackingFields() throws Exception {
        assertFinalNotifierField("blendFuncNotifier");
        assertFinalNotifierField("bindTextureNotifier");
        assertFinalNotifierField("fogToggleNotifier");
        assertFinalNotifierField("fogModeNotifier");
        assertFinalNotifierField("fogStartNotifier");
        assertFinalNotifierField("fogEndNotifier");
        assertFinalNotifierField("fogDensityNotifier");
        assertFinalNotifierField("normalTextureChangeNotifier");
        assertFinalNotifierField("specularTextureChangeNotifier");
        assertFinalNotifierField("phaseChangeNotifier");
    }

    @Test
    public void textureBindingNotifierFansOutToEveryActiveUniformListener() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        StateUpdateNotifiers.bindTextureNotifier.setListener(first::incrementAndGet);
        StateUpdateNotifiers.bindTextureNotifier.setListener(second::incrementAndGet);
        StateUpdateNotifiers.notifyTextureBindingChanged();

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    public void notifierClearRemovesAllActiveListeners() {
        AtomicInteger calls = new AtomicInteger();

        StateUpdateNotifiers.bindTextureNotifier.setListener(calls::incrementAndGet);
        StateUpdateNotifiers.bindTextureNotifier.setListener(calls::incrementAndGet);
        StateUpdateNotifiers.bindTextureNotifier.setListener(null);
        StateUpdateNotifiers.notifyTextureBindingChanged();

        assertEquals(0, calls.get());
    }

    @Test
    public void notifierRemoveOnlyDetachesTheSpecifiedListener() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        Runnable firstListener = first::incrementAndGet;
        Runnable secondListener = second::incrementAndGet;

        StateUpdateNotifiers.bindTextureNotifier.setListener(firstListener);
        StateUpdateNotifiers.bindTextureNotifier.setListener(secondListener);
        StateUpdateNotifiers.bindTextureNotifier.removeListener(firstListener);
        StateUpdateNotifiers.notifyTextureBindingChanged();

        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }

    @Test
    public void notifierDeduplicatesTheSameListenerInstance() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;

        StateUpdateNotifiers.bindTextureNotifier.setListener(listener);
        StateUpdateNotifiers.bindTextureNotifier.setListener(listener);
        StateUpdateNotifiers.notifyTextureBindingChanged();

        assertEquals(1, calls.get());
    }

    @Test
    public void fanOutNotifierAttemptsEveryListenerBeforeRethrowingFirstFailure() {
        FanOutValueUpdateNotifier notifier = new FanOutValueUpdateNotifier();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        AtomicInteger third = new AtomicInteger();
        RuntimeException firstFailure = new RuntimeException("first");
        RuntimeException secondFailure = new RuntimeException("second");

        notifier.setListener(() -> {
            first.incrementAndGet();
            throw firstFailure;
        });
        notifier.setListener(() -> {
            second.incrementAndGet();
            throw secondFailure;
        });
        notifier.setListener(third::incrementAndGet);

        try {
            notifier.notifyListeners();
            fail("Expected fan-out notifier to rethrow the first listener failure");
        } catch (RuntimeException thrown) {
            assertSame(firstFailure, thrown);
            assertEquals(1, thrown.getSuppressed().length);
            assertSame(secondFailure, thrown.getSuppressed()[0]);
        }

        assertEquals(1, first.get());
        assertEquals(1, second.get());
        assertEquals(1, third.get());
    }

    @Test
    public void fanOutNotifierKeepsOriginalFailureWhenListenersShareThrowableInstance() {
        FanOutValueUpdateNotifier notifier = new FanOutValueUpdateNotifier();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        RuntimeException sharedFailure = new RuntimeException("shared");

        notifier.setListener(() -> {
            first.incrementAndGet();
            throw sharedFailure;
        });
        notifier.setListener(() -> {
            second.incrementAndGet();
            throw sharedFailure;
        });

        try {
            notifier.notifyListeners();
            fail("Expected fan-out notifier to rethrow the shared listener failure");
        } catch (RuntimeException thrown) {
            assertSame(sharedFailure, thrown);
            assertEquals(0, thrown.getSuppressed().length);
        }

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    public void phaseNotifierFansOutForRenderStageDependentUniforms() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        StateUpdateNotifiers.phaseChangeNotifier.setListener(first::incrementAndGet);
        StateUpdateNotifiers.phaseChangeNotifier.setListener(second::incrementAndGet);
        StateUpdateNotifiers.notifyPhaseChanged();

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    public void pbrTextureNotifiersPublishStateChanges() {
        AtomicInteger calls = new AtomicInteger();

        StateUpdateNotifiers.normalTextureChangeNotifier.setListener(calls::incrementAndGet);
        StateUpdateNotifiers.specularTextureChangeNotifier.setListener(calls::incrementAndGet);
        StateUpdateNotifiers.notifyNormalTextureChanged();
        StateUpdateNotifiers.notifySpecularTextureChanged();

        assertEquals(2, calls.get());
    }

    @Test
    public void fogCombinedNotifierRemoveOnlyDetachesTheSpecifiedListener() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        Runnable firstListener = first::incrementAndGet;
        Runnable secondListener = second::incrementAndGet;
        ValueUpdateNotifier notifier = StateUpdateNotifiers.fogModeNotifierWithToggle();

        notifier.setListener(firstListener);
        notifier.setListener(secondListener);
        notifier.removeListener(firstListener);
        StateUpdateNotifiers.notifyFogModeChanged();
        StateUpdateNotifiers.notifyFogToggled();

        assertEquals(0, first.get());
        assertEquals(2, second.get());
    }

    @Test
    public void fogCombinedNotifierSourceAggregatesAttachAndDetachFailures() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/state/StateUpdateNotifiers.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains(
            "failure = runNotifierOperation(failure, () -> fogToggleNotifier.setListener(listener));"));
        assertTrue(source.contains(
            "failure = runNotifierOperation(failure, () -> notifier.setListener(listener));"));
        assertTrue(source.contains(
            "failure = runNotifierOperation(failure, () -> fogToggleNotifier.removeListener(listener));"));
        assertTrue(source.contains(
            "failure = runNotifierOperation(failure, () -> notifier.removeListener(listener));"));
        assertTrue(source.contains("if (exception != failure) {\n"
            + "            failure.addSuppressed(exception);\n"
            + "        }"));
    }

    @Test
    public void fogValueNotifiersPublishSpecificAndToggleChanges() {
        assertFogNotifierPublishesSpecificAndToggle(StateUpdateNotifiers.fogModeNotifierWithToggle(),
            StateUpdateNotifiers::notifyFogModeChanged);
        assertFogNotifierPublishesSpecificAndToggle(StateUpdateNotifiers.fogStartNotifierWithToggle(),
            StateUpdateNotifiers::notifyFogStartChanged);
        assertFogNotifierPublishesSpecificAndToggle(StateUpdateNotifiers.fogEndNotifierWithToggle(),
            StateUpdateNotifiers::notifyFogEndChanged);
        assertFogNotifierPublishesSpecificAndToggle(StateUpdateNotifiers.fogDensityNotifierWithToggle(),
            StateUpdateNotifiers::notifyFogDensityChanged);
    }

    private static void assertFogNotifierPublishesSpecificAndToggle(ValueUpdateNotifier notifier,
                                                                    Runnable notifySpecific) {
        StateUpdateNotifiers.fogToggleNotifier.setListener(null);
        StateUpdateNotifiers.fogModeNotifier.setListener(null);
        StateUpdateNotifiers.fogStartNotifier.setListener(null);
        StateUpdateNotifiers.fogEndNotifier.setListener(null);
        StateUpdateNotifiers.fogDensityNotifier.setListener(null);

        AtomicInteger calls = new AtomicInteger();
        notifier.setListener(calls::incrementAndGet);

        notifySpecific.run();
        StateUpdateNotifiers.notifyFogToggled();

        assertEquals(2, calls.get());
    }

    private static void assertFinalNotifierField(String fieldName) throws Exception {
        Field field = StateUpdateNotifiers.class.getDeclaredField(fieldName);
        assertTrue("Notifier field " + fieldName + " must stay final",
            Modifier.isFinal(field.getModifiers()));
    }
}
