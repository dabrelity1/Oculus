package net.oculus.compat.relictium;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RelictiumChunkRenderShaderBackendMixinSourceTest {
    @Test
    public void beginFailureCleansTerrainScopeWithoutReplacingOriginalFailure() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/RelictiumChunkRenderShaderBackendMixin.java");
        String begin = methodBody(source, "public void oculus$begin(BlockRenderPass pass)");
        String cleanup = methodBody(source,
            "private void oculus$cleanupTerrainScopeAfterBeginFailure(Throwable failure)");

        int tryBlock = begin.indexOf("try {");
        int previousPhase = begin.indexOf("this.oculus$previousPhase = pipeline.getPhase();", tryBlock);
        int markScope = begin.indexOf("this.oculus$sodiumTerrainScope = true;", previousPhase);
        int setPhase = begin.indexOf("pipeline.setPhase(this.oculus$currentPhase);", markScope);
        int beginSodium = begin.indexOf("pipeline.beginSodiumTerrainRendering();", setPhase);
        int beginCall = begin.indexOf("((ChunkRenderShaderBackend<?>) (Object) this).begin();", beginSodium);
        int runtimeCatch = begin.indexOf("catch (RuntimeException exception)", beginCall);
        int runtimeCleanup = begin.indexOf("oculus$cleanupTerrainScopeAfterBeginFailure(exception);", runtimeCatch);
        int runtimeRethrow = begin.indexOf("throw exception;", runtimeCleanup);
        int errorCatch = begin.indexOf("catch (Error error)", runtimeRethrow);
        int errorCleanup = begin.indexOf("oculus$cleanupTerrainScopeAfterBeginFailure(error);", errorCatch);
        int errorRethrow = begin.indexOf("throw error;", errorCleanup);

        assertTrue("Relictium terrain scope setup must be inside the begin cleanup guard", tryBlock >= 0);
        assertTrue("Previous phase capture must happen before terrain scope is marked active",
            previousPhase > tryBlock && markScope > previousPhase);
        assertTrue("Terrain scope must be marked active before phase updates can throw",
            setPhase > markScope);
        assertTrue("Sodium terrain entry must run under the same cleanup guard before backend begin",
            beginSodium > setPhase && beginCall > beginSodium);
        assertTrue("Runtime begin failures must run Relictium terrain cleanup first",
            runtimeCatch > beginCall && runtimeCleanup > runtimeCatch && runtimeRethrow > runtimeCleanup);
        assertTrue("Hard begin failures must also run Relictium terrain cleanup",
            errorCatch > runtimeRethrow && errorCleanup > errorCatch && errorRethrow > errorCleanup);
        assertTrue("Begin cleanup must attach cleanup failures to the original failure",
            cleanup.contains("oculus$endTerrainScope();")
                && cleanup.contains("catch (RuntimeException | Error cleanupFailure)")
                && cleanup.contains("oculus$suppressCleanupFailure(failure, cleanupFailure);"));
    }

    @Test
    public void endTerrainScopeRestoresPipelineStateAfterProgramUnbindFailure() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/RelictiumChunkRenderShaderBackendMixin.java");
        String endScope = methodBody(source, "private void oculus$endTerrainScope()");

        int hadScope = endScope.indexOf("boolean hadScope = this.oculus$sodiumTerrainScope;");
        int clearScope = endScope.indexOf("this.oculus$sodiumTerrainScope = false;", hadScope);
        int noScopeReset = endScope.indexOf("this.oculus$previousPhase = WorldRenderingPhase.NONE;", clearScope);
        int failure = endScope.indexOf("Throwable failure = null;", noScopeReset);
        int unbind = endScope.indexOf("Program.unbind();", failure);
        int collectUnbind = endScope.indexOf("failure = oculus$collectCleanupFailure(failure, exception);", unbind);
        int pipelineLookup = endScope.indexOf("pipeline = PipelineManager.INSTANCE.getPipelineNullable();",
            collectUnbind);
        int collectLookup = endScope.indexOf("failure = oculus$collectCleanupFailure(failure, exception);",
            pipelineLookup);
        int endSodium = endScope.indexOf("pipeline.endSodiumTerrainRendering();", collectLookup);
        int collectEnd = endScope.indexOf("failure = oculus$collectCleanupFailure(failure, exception);", endSodium);
        int restorePhase = endScope.indexOf("pipeline.setPhase(this.oculus$previousPhase);", collectEnd);
        int collectPhase = endScope.indexOf("failure = oculus$collectCleanupFailure(failure, exception);",
            restorePhase);
        int finalReset = endScope.indexOf("this.oculus$previousPhase = WorldRenderingPhase.NONE;", collectPhase);
        int rethrow = endScope.indexOf("oculus$rethrowCleanupFailure(failure);", finalReset);

        assertTrue(hadScope >= 0);
        assertTrue("Terrain scope flag must clear before any cleanup can throw", clearScope > hadScope);
        assertTrue("No-scope exits must still clear stale previous phase state", noScopeReset > clearScope);
        assertTrue("Program unbind failures must be collected instead of short-circuiting terrain cleanup",
            unbind > failure && collectUnbind > unbind);
        assertTrue("Pipeline lookup failure must be isolated from later state reset",
            pipelineLookup > collectUnbind && collectLookup > pipelineLookup);
        assertTrue("Relictium terrain scope must end even if Program.unbind failed",
            endSodium > collectLookup && collectEnd > endSodium);
        assertTrue("The previous render phase must be restored even if ending the terrain scope failed",
            restorePhase > collectEnd && collectPhase > restorePhase);
        assertTrue("Previous phase must reset before collected cleanup failures are rethrown",
            finalReset > collectPhase && rethrow > finalReset);
    }

    @Test
    public void terrainScopeCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same terrain cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void terrainScopeCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("terrain cleanup failure");
        RuntimeException cleanupFailure = new RuntimeException("late cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = Class.forName("net.oculus.mixin.pipeline.RelictiumChunkRenderShaderBackendMixin")
            .getDeclaredMethod("oculus$suppressCleanupFailure", Throwable.class, Throwable.class);
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
