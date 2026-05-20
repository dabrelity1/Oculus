package net.oculus.gl.program;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ProgramUniformsTest {
    @Test
    public void uniformUpdateResetsActiveListenersBeforeReattachingLikeReference() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramUniforms.java")), StandardCharsets.UTF_8);
        String updateBody = methodBody(source, "public void update()");
        String cleanupBody = methodBody(source, "private static Throwable cleanupActiveBeforeUpdate()");

        int cleanup = updateBody.indexOf("Throwable previousCleanupFailure = cleanupActiveBeforeUpdate();");
        int activeSet = updateBody.indexOf("active = this;", cleanup);
        int attach = updateBody.indexOf("attachListeners();", activeSet);
        int rethrowPreviousCleanup = updateBody.indexOf("rethrowCleanupFailure(previousCleanupFailure);", attach);

        assertTrue(cleanup >= 0);
        assertTrue(activeSet > cleanup);
        assertTrue(attach > activeSet);
        assertTrue(rethrowPreviousCleanup > attach);
        assertTrue(cleanupBody.contains("ProgramUniforms current = active;"));
        assertTrue(cleanupBody.contains("return runCleanup(null, current::removeListeners);"));
        assertTrue(source.contains("attachListeners();"));
        assertTrue(source.contains("private final Runnable listener;"));
        assertTrue(source.contains("this.listener = notifier == null ? null : this::upload;"));
        assertTrue(source.contains("notifier.setListener(listener);"));
        assertTrue(source.contains("notifier.removeListener(listener);"));
    }

    @Test
    public void uniformUploadFailuresPropagateLikeReference() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramUniforms.java")), StandardCharsets.UTF_8);
        String upload = methodBody(source, "private void upload()");

        assertTrue(upload.contains("updater.upload(location);"));
        assertTrue("Uniform upload should not catch RuntimeException and continue with stale shader state",
            !upload.contains("catch (RuntimeException"));
        assertTrue("Uniform upload should not demote update failures to log warnings",
            !upload.contains("LOGGER.warn"));
    }

    @Test
    public void uniformCleanupAttemptsEveryListenerBeforeRethrowing() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramUniforms.java")), StandardCharsets.UTF_8);
        String clearBody = methodBody(source, "public static void clearActiveUniforms()");
        String removeBody = methodBody(source, "private void removeListeners()");

        int current = clearBody.indexOf("ProgramUniforms current = active;");
        int runCleanup = clearBody.indexOf("failure = runCleanup(failure, current::removeListeners);", current);
        int finallyBlock = clearBody.indexOf("} finally {", runCleanup);
        int activeNull = clearBody.indexOf("active = null;", finallyBlock);
        int rethrow = clearBody.indexOf("rethrowCleanupFailure(failure);", activeNull);

        int loop = removeBody.indexOf("for (UniformBinding binding : bindings)");
        int aggregate = removeBody.indexOf("failure = runCleanup(failure, binding::detachListener);", loop);
        int removeRethrow = removeBody.indexOf("rethrowCleanupFailure(failure);", aggregate);

        assertTrue(current >= 0);
        assertTrue(runCleanup > current);
        assertTrue(finallyBlock > runCleanup);
        assertTrue(activeNull > finallyBlock);
        assertTrue(rethrow > activeNull);
        assertTrue(loop >= 0);
        assertTrue(aggregate > loop);
        assertTrue(removeRethrow > aggregate);
    }

    @Test
    public void failedUniformUpdateClearsPublishedActiveBindingSetBeforeRethrowing() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ProgramUniforms.java")), StandardCharsets.UTF_8);
        String updateBody = methodBody(source, "public void update()");
        String cleanupBody = methodBody(source, "private static void cleanupAfterFailedUpdate(Throwable failure)");

        int activeSet = updateBody.indexOf("active = this;");
        int tryBlock = updateBody.indexOf("try {", activeSet);
        int attach = updateBody.indexOf("attachListeners();", tryBlock);
        int uploadLoop = updateBody.indexOf("for (UniformBinding binding : bindings)", attach);
        int runtimeCatch = updateBody.indexOf("catch (RuntimeException exception)", uploadLoop);
        int runtimeSuppress = updateBody.indexOf("suppressCleanupFailure(exception, previousCleanupFailure);", runtimeCatch);
        int runtimeCleanup = updateBody.indexOf("cleanupAfterFailedUpdate(exception);", runtimeSuppress);
        int errorCatch = updateBody.indexOf("catch (Error error)", runtimeCleanup);
        int errorSuppress = updateBody.indexOf("suppressCleanupFailure(error, previousCleanupFailure);", errorCatch);
        int errorCleanup = updateBody.indexOf("cleanupAfterFailedUpdate(error);", errorSuppress);

        assertTrue(activeSet >= 0);
        assertTrue(tryBlock > activeSet);
        assertTrue(attach > tryBlock);
        assertTrue(uploadLoop > attach);
        assertTrue(runtimeCatch > uploadLoop);
        assertTrue(runtimeSuppress > runtimeCatch);
        assertTrue(runtimeCleanup > runtimeSuppress);
        assertTrue(errorCatch > runtimeCleanup);
        assertTrue(errorSuppress > errorCatch);
        assertTrue(errorCleanup > errorSuppress);
        assertTrue(cleanupBody.contains("clearActiveUniforms();"));
        assertTrue(cleanupBody.contains("suppressCleanupFailure(failure, cleanupFailure);"));
        assertTrue(source.contains("private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure)"));
    }

    @Test
    public void uniformCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same uniform cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void uniformCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("uniform failure");
        RuntimeException cleanupFailure = new RuntimeException("uniform cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = ProgramUniforms.class.getDeclaredMethod(
            "suppressCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }

        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Unable to read method body for " + signature);
    }
}
