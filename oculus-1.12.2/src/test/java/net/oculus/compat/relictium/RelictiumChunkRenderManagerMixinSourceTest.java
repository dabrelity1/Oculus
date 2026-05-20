package net.oculus.compat.relictium;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RelictiumChunkRenderManagerMixinSourceTest {
    @Test
    public void renderFailureEndsBackendAndScopeWithoutReplacingOriginalFailure() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/RelictiumChunkRenderManagerMixin.java");
        String render = methodBody(source,
            "private void oculus$renderBackendWithScopeCleanup(ChunkRenderBackend<?> backend,");
        String cleanup = methodBody(source,
            "private static void oculus$endBackendAfterRenderFailure(ChunkRenderBackend<?> backend, Throwable failure)");

        int renderCall = render.indexOf("((ChunkRenderBackend) backend).render");
        int runtimeCatch = render.indexOf("catch (RuntimeException exception)", renderCall);
        int runtimeCleanup = render.indexOf("oculus$endBackendAfterRenderFailure(backend, exception);", runtimeCatch);
        int runtimeRethrow = render.indexOf("throw exception;", runtimeCleanup);
        int errorCatch = render.indexOf("catch (Error error)", runtimeRethrow);
        int errorCleanup = render.indexOf("oculus$endBackendAfterRenderFailure(backend, error);", errorCatch);
        int errorRethrow = render.indexOf("throw error;", errorCleanup);

        assertTrue(renderCall >= 0);
        assertTrue("Runtime render failures must end the backend and terrain scope before rethrow",
            runtimeCatch > renderCall && runtimeCleanup > runtimeCatch && runtimeRethrow > runtimeCleanup);
        assertTrue("Hard render failures must end the backend and terrain scope before rethrow",
            errorCatch > runtimeRethrow && errorCleanup > errorCatch && errorRethrow > errorCleanup);
        assertTrue("Render-failure cleanup must not replace the original render failure",
            cleanup.contains("oculus$endBackendWithCollectedScopeCleanup(backend);")
                && cleanup.contains("catch (RuntimeException | Error cleanupFailure)")
                && cleanup.contains("oculus$suppressCleanupFailure(failure, cleanupFailure);"));
    }

    @Test
    public void normalEndCollectsBackendAndTerrainScopeCleanupFailures() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/RelictiumChunkRenderManagerMixin.java");
        String redirect = methodBody(source,
            "private void oculus$endBackendWithScopeCleanup(ChunkRenderBackend<?> backend,");
        String cleanup = methodBody(source,
            "private static void oculus$endBackendWithCollectedScopeCleanup(ChunkRenderBackend<?> backend)");

        assertTrue("The end redirect must delegate to collected cleanup handling",
            redirect.contains("oculus$endBackendWithCollectedScopeCleanup(backend);"));

        int failure = cleanup.indexOf("Throwable failure = null;");
        int backendEnd = cleanup.indexOf("backend.end();", failure);
        int collectBackend = cleanup.indexOf("failure = oculus$collectCleanupFailure(failure, exception);",
            backendEnd);
        int scopeCleanup = cleanup.indexOf("oculus$cleanupTerrainScope(backend);", collectBackend);
        int collectScope = cleanup.indexOf("failure = oculus$collectCleanupFailure(failure, exception);",
            scopeCleanup);
        int rethrow = cleanup.indexOf("oculus$rethrowCleanupFailure(failure);", collectScope);

        assertTrue("Backend end failures must be collected instead of skipping terrain scope cleanup",
            backendEnd > failure && collectBackend > backendEnd);
        assertTrue("Oculus terrain scope cleanup must run after backend.end() even when backend.end() failed",
            scopeCleanup > collectBackend && collectScope > scopeCleanup);
        assertTrue("Collected cleanup failures must be rethrown only after both cleanup paths are attempted",
            rethrow > collectScope);
    }

    @Test
    public void disablesRelictiumFogOcclusionWhileShaderPipelineOwnsFog() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/RelictiumChunkRenderManagerMixin.java");
        String redirect = methodBody(source,
            "private boolean oculus$disableFogOcclusionWhenShaderPackIsActive(SodiumGameOptions.AdvancedSettings settings)");

        assertTrue("Relictium setup must redirect the fog occlusion option",
            source.contains("method = \"setup(F)V\"")
                && source.contains("SodiumGameOptions$AdvancedSettings;useFogOcclusion:Z"));
        assertTrue("Fog occlusion must be disabled while a shader pack owns world fog",
            redirect.contains("PipelineManager.INSTANCE.getPipelineNullable() instanceof ShaderWorldRenderingPipeline")
                && redirect.contains("return false;"));
        assertTrue("Relictium fog occlusion must still respect the user option without a shader pipeline",
            redirect.contains("return settings.useFogOcclusion;"));
    }

    @Test
    public void terrainScopeCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same render-manager cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void terrainScopeCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("render failure");
        RuntimeException cleanupFailure = new RuntimeException("backend end failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = Class.forName("net.oculus.mixin.pipeline.RelictiumChunkRenderManagerMixin")
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
