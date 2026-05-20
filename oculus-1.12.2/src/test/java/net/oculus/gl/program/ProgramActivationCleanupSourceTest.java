package net.oculus.gl.program;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ProgramActivationCleanupSourceTest {
    @Test
    public void programUseCleansAllActiveRuntimeBindingsWhenActivationFails() throws Exception {
        String source = read("src/main/java/net/oculus/gl/program/Program.java");
        String useBody = methodBody(source, "public void use()");
        String activateBody = methodBody(source, "protected final void activate()");
        String cleanupBody = methodBody(source, "static void cleanupAfterActivationFailure(Throwable failure)");

        int tryBlock = useBody.indexOf("try {");
        int activate = useBody.indexOf("activate();", tryBlock);
        int runtimeCatch = useBody.indexOf("catch (RuntimeException exception)", activate);
        int runtimeCleanup = useBody.indexOf("cleanupAfterActivationFailure(exception);", runtimeCatch);
        int runtimeRethrow = useBody.indexOf("throw exception;", runtimeCleanup);
        int errorCatch = useBody.indexOf("catch (Error error)", runtimeRethrow);
        int errorCleanup = useBody.indexOf("cleanupAfterActivationFailure(error);", errorCatch);
        int errorRethrow = useBody.indexOf("throw error;", errorCleanup);

        assertTrue(tryBlock >= 0);
        assertTrue(activate > tryBlock);
        assertTrue(runtimeCatch > activate);
        assertTrue(runtimeCleanup > runtimeCatch);
        assertTrue(runtimeRethrow > runtimeCleanup);
        assertTrue(errorCatch > runtimeRethrow);
        assertTrue(errorCleanup > errorCatch);
        assertTrue(errorRethrow > errorCleanup);
        assertRuntimeActivationSequence(activateBody);
        assertCleanupHelperSuppressesFailures(cleanupBody);
    }

    @Test
    public void computeDispatchCleansRuntimeBindingsWhenActivationOrDispatchFails() throws Exception {
        String source = read("src/main/java/net/oculus/gl/program/ComputeProgram.java");
        String dispatchBody = methodBody(source, "public void dispatch(float width, float height)");

        int supportGuard = dispatchBody.indexOf("if (!OculusRenderSystem.supportsCompute())");
        int tryBlock = dispatchBody.indexOf("try {", supportGuard);
        int activate = dispatchBody.indexOf("activate();", tryBlock);
        int barrierGuard = dispatchBody.indexOf("if (!isConcurrentComputeAllowed())", activate);
        int barrier = dispatchBody.indexOf("OculusRenderSystem.memoryBarrier(PRE_DISPATCH_BARRIER);", barrierGuard);
        int workGroups = dispatchBody.indexOf("Vector3i workGroups = getWorkGroups(width, height);", barrier);
        int dispatch = dispatchBody.indexOf("OculusRenderSystem.dispatchCompute(workGroups);", workGroups);
        int runtimeCatch = dispatchBody.indexOf("catch (RuntimeException exception)", dispatch);
        int runtimeCleanup = dispatchBody.indexOf("Program.cleanupAfterActivationFailure(exception);", runtimeCatch);
        int runtimeRethrow = dispatchBody.indexOf("throw exception;", runtimeCleanup);
        int errorCatch = dispatchBody.indexOf("catch (Error error)", runtimeRethrow);
        int errorCleanup = dispatchBody.indexOf("Program.cleanupAfterActivationFailure(error);", errorCatch);
        int errorRethrow = dispatchBody.indexOf("throw error;", errorCleanup);

        assertTrue(supportGuard >= 0);
        assertTrue(tryBlock > supportGuard);
        assertTrue(activate > tryBlock);
        assertTrue(barrierGuard > activate);
        assertTrue(barrier > barrierGuard);
        assertTrue(workGroups > barrier);
        assertTrue(dispatch > workGroups);
        assertTrue(runtimeCatch > dispatch);
        assertTrue(runtimeCleanup > runtimeCatch);
        assertTrue(runtimeRethrow > runtimeCleanup);
        assertTrue(errorCatch > runtimeRethrow);
        assertTrue(errorCleanup > errorCatch);
        assertTrue(errorRethrow > errorCleanup);
    }

    @Test
    public void programDestroyAttemptsAllActiveBindingCleanupAndOwnedHandleDelete() throws Exception {
        String source = read("src/main/java/net/oculus/gl/program/Program.java");
        String destroyBody = methodBody(source, "protected void destroyInternal()");

        int failure = destroyBody.indexOf("Throwable failure = null;");
        int uniforms = destroyBody.indexOf(
            "failure = runCleanup(failure, () -> ProgramUniforms.clearActiveUniforms(uniforms));",
            failure);
        int samplers = destroyBody.indexOf(
            "failure = runCleanup(failure, () -> ProgramSamplers.clearActiveSamplers(samplers));",
            uniforms);
        int images = destroyBody.indexOf(
            "failure = runCleanup(failure, () -> ProgramImages.clearActiveImages(images));",
            samplers);
        int ownedGuard = destroyBody.indexOf("if (ownsProgramHandle)", images);
        int delete = destroyBody.indexOf(
            "failure = runCleanup(failure, () -> OculusRenderSystem.glDeleteProgram(getGlId()));",
            ownedGuard);
        int rethrow = destroyBody.indexOf("rethrowCleanupFailure(failure);", delete);

        assertTrue(failure >= 0);
        assertTrue(uniforms > failure);
        assertTrue(samplers > uniforms);
        assertTrue(images > samplers);
        assertTrue(ownedGuard > images);
        assertTrue(delete > ownedGuard);
        assertTrue(rethrow > delete);
    }

    private static void assertRuntimeActivationSequence(String activateBody) {
        int useProgram = activateBody.indexOf("OculusRenderSystem.glUseProgram(getGlId());");
        int uniforms = activateBody.indexOf("uniforms.update();", useProgram);
        int samplers = activateBody.indexOf("samplers.update();", uniforms);
        int images = activateBody.indexOf("images.update();", samplers);

        assertTrue(useProgram >= 0);
        assertTrue(uniforms > useProgram);
        assertTrue(samplers > uniforms);
        assertTrue(images > samplers);
    }

    private static void assertCleanupHelperSuppressesFailures(String cleanupBody) {
        int tryBlock = cleanupBody.indexOf("try {");
        int cleanup = cleanupBody.indexOf("clearActiveBindingsAndProgram();", tryBlock);
        int catchBlock = cleanupBody.indexOf("catch (RuntimeException | Error cleanupFailure)", cleanup);
        int suppressed = cleanupBody.indexOf("suppressCleanupFailure(failure, cleanupFailure);", catchBlock);

        assertTrue(tryBlock >= 0);
        assertTrue(cleanup > tryBlock);
        assertTrue(catchBlock > cleanup);
        assertTrue(suppressed > catchBlock);
    }

    @Test
    public void programCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same program cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void programCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("program failure");
        RuntimeException cleanupFailure = new RuntimeException("program cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = Program.class.getDeclaredMethod(
            "suppressCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
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
