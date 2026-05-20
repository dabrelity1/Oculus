package net.oculus.compat.relictium;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class OculusRelictiumChunkProgramOverridesSourceTest {
    @Test
    public void shadowOverrideFailsFastWhenShadowSourceIsMissing() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverrides.java");
        String method = methodBody(source,
            "public ChunkProgram getProgramOverride(RenderDevice device, BlockRenderPass pass)");

        int shadowBranch = method.indexOf("worldPipeline != null && worldPipeline.isRenderingShadowPass()");
        int missingShadowCheck = method.indexOf("if (!sodiumTerrainPipeline.hasShadowPass())", shadowBranch);
        int exception = method.indexOf(
            "throw new IllegalStateException(\"Shadow program requested, but the pack does not have a shadow pass?\");",
            missingShadowCheck);
        int terrainPass = method.indexOf("terrainPass = OculusTerrainPass.SHADOW;", exception);
        int programLookup = method.indexOf("ChunkProgram program = programs.get(terrainPass);", terrainPass);
        int missingOverrideGuard = method.indexOf(
            "if (terrainPass == OculusTerrainPass.SHADOW && program == null)", programLookup);
        int missingOverrideException = method.indexOf(
            "throw new IllegalStateException(\"Shadow program requested, but the Relictium shadow override program \"",
            missingOverrideGuard);
        int lookupLog = method.indexOf("OculusRuntimeValidation.logRelictiumTerrainOverrideLookup(", missingOverrideException);

        assertTrue("Shadow render branch must be source-visible", shadowBranch >= 0);
        assertTrue("Missing shadow pass must be checked inside the shadow branch", missingShadowCheck > shadowBranch);
        assertTrue("Missing shadow pass should fail fast like the 1.16.5 Iris override path",
            exception > missingShadowCheck);
        assertTrue("Shadow override selection should only continue after the fail-fast guard",
            terrainPass > exception);
        assertTrue("Shadow override lookup must read the cached override after selecting the shadow pass",
            programLookup > terrainPass);
        assertTrue("A failed Relictium shadow override build must fail clearly instead of falling back to vanilla terrain",
            missingOverrideGuard > programLookup && missingOverrideException > missingOverrideGuard);
        assertTrue("Shadow override lookup logging must only run after the missing-shadow-override guard",
            lookupLog > missingOverrideException);
        assertFalse("Shadow override requests must not silently fall back to Relictium default terrain programs",
            method.contains("\"shadow source unavailable\""));
        assertFalse("Shadow override requests must not return null when the active pack lacks a shadow source",
            method.contains("if (!sodiumTerrainPipeline.hasShadowPass()) {\n"
                + "                return null;"));
    }

    @Test
    public void deleteShadersClearsReloadCacheAndContinuesAfterHardDeleteFailures() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverrides.java");
        String method = methodBody(source, "public void deleteShaders()");

        int tryBlock = method.indexOf("try {");
        int deleteCall = method.indexOf("program.delete();", tryBlock);
        int catchBlock = method.indexOf("catch (RuntimeException | Error exception)", deleteCall);
        int finallyBlock = method.indexOf("} finally {", catchBlock);
        int clearPrograms = method.indexOf("programs.clear();", finallyBlock);
        int resetVersion = method.indexOf("versionCounterForSodiumShaderReload = -1;", clearPrograms);
        int resetPipeline = method.indexOf("currentPipeline = null;", resetVersion);
        int resetDevice = method.indexOf("currentDevice = null;", resetPipeline);

        assertTrue("deleteShaders must catch hard delete failures per program", catchBlock > deleteCall);
        assertTrue("deleteShaders must clear override programs even after delete failures", clearPrograms > finallyBlock);
        assertTrue("deleteShaders must force the next lookup to rebuild the cache", resetVersion > clearPrograms);
        assertTrue("deleteShaders must drop the cached terrain pipeline key", resetPipeline > resetVersion);
        assertTrue("deleteShaders must drop the cached render device key", resetDevice > resetPipeline);
    }

    @Test
    public void createShaderDeletesLinkedProgramOnRuntimeAndHardFailures() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverrides.java");
        String createShader = methodBody(source,
            "private ChunkProgram createShader(RenderDevice device, OculusTerrainPass pass, SodiumTerrainPipeline pipeline)");
        String failedShaderCleanup = methodBody(source,
            "private static void cleanupFailedShader(ChunkProgram program,");
        String bindingCleanup = methodBody(source,
            "private static void destroyFailedBindings(Program bindings, OculusTerrainPass pass, Throwable failure)");
        String cleanup = methodBody(source,
            "private static void deleteFailedProgram(int handle, OculusTerrainPass pass, Throwable failure)");

        int handleDeclaration = createShader.indexOf("int handle = 0;");
        int bindingsDeclaration = createShader.indexOf("Program bindings = null;", handleDeclaration);
        int programDeclaration = createShader.indexOf("ChunkProgram program = null;", bindingsDeclaration);
        int link = createShader.indexOf("handle = OculusRelictiumProgramLinker.link", programDeclaration);
        int bindings = createShader.indexOf("bindings = pipeline.buildProgramBindings", link);
        int constructProgram = createShader.indexOf("program = new OculusRelictiumChunkProgram", bindings);
        int runtimeCatch = createShader.indexOf("catch (RuntimeException exception)", constructProgram);
        int runtimeCleanup = createShader.indexOf(
            "cleanupFailedShader(program, bindings, handle, pass, exception);", runtimeCatch);
        int runtimeFallback = createShader.indexOf("return null;", runtimeCleanup);
        int errorCatch = createShader.indexOf("catch (Error error)", runtimeFallback);
        int errorCleanup = createShader.indexOf(
            "cleanupFailedShader(program, bindings, handle, pass, error);", errorCatch);
        int rethrow = createShader.indexOf("throw error;", errorCleanup);

        assertTrue("Relictium program handle must be tracked before linking", handleDeclaration >= 0);
        assertTrue("Oculus binding wrapper must be tracked before it can be stranded by constructor failure",
            bindingsDeclaration > handleDeclaration);
        assertTrue("Constructed Relictium programs must be tracked for failure cleanup",
            programDeclaration > bindingsDeclaration);
        assertTrue("Relictium linker must store the linked handle before bindings are built", link > handleDeclaration);
        assertTrue("Oculus binding metadata must be built after the linked handle exists", bindings > link);
        assertTrue("Relictium program ownership must transfer only after bindings are available",
            constructProgram > bindings);
        assertTrue("Runtime creation failures must clean up the linked program and binding wrapper",
            runtimeCleanup > runtimeCatch);
        assertTrue("Runtime creation failures should keep the existing fallback path", runtimeFallback > runtimeCleanup);
        assertTrue("Hard creation failures must be handled after runtime failures", errorCatch > runtimeFallback);
        assertTrue("Hard creation failures must clean up the linked program and binding wrapper",
            errorCleanup > errorCatch);
        assertTrue("Hard creation failures must not be hidden as a missing override", rethrow > errorCleanup);
        assertTrue("If a ChunkProgram was constructed, its delete path owns wrapper and handle cleanup",
            failedShaderCleanup.contains("if (program != null)")
                && failedShaderCleanup.contains("program.delete();")
                && failedShaderCleanup.contains("return;"));
        int destroyBindings = failedShaderCleanup.indexOf("destroyFailedBindings(bindings, pass, failure);");
        int deleteHandle = failedShaderCleanup.indexOf("deleteFailedProgram(handle, pass, failure);", destroyBindings);
        assertTrue("A built non-owning Program wrapper must be destroyed if ChunkProgram construction fails",
            destroyBindings >= 0 && deleteHandle > destroyBindings);
        assertTrue("Binding wrapper cleanup must ignore the no-wrapper path",
            bindingCleanup.contains("if (bindings == null)"));
        assertTrue("Binding wrapper cleanup must destroy the Program wrapper without owning the GL handle",
            bindingCleanup.contains("bindings.destroy();"));
        assertTrue("Binding wrapper cleanup failures should remain attached to the original creation failure",
            bindingCleanup.contains("suppressCleanupFailure(failure, cleanupFailure);"));
        assertTrue("Cleanup should ignore the no-handle path", cleanup.contains("if (handle == 0)"));
        assertTrue("Cleanup should delete linked GL programs", cleanup.contains("OculusRenderSystem.glDeleteProgram(handle);"));
        assertTrue("Cleanup failures should be isolated from the original creation failure",
            cleanup.contains("catch (RuntimeException | Error cleanupFailure)"));
        assertTrue("Cleanup failures should remain attached to the original failure",
            cleanup.contains("suppressCleanupFailure(failure, cleanupFailure);"));
    }

    @Test
    public void rebuildShadersCleansPartialOverridesWhenRebuildAborts() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgramOverrides.java");
        String rebuild = methodBody(source,
            "private void rebuildShaders(SodiumTerrainPipeline sodiumTerrainPipeline, RenderDevice device, int version)");
        String cleanup = methodBody(source, "private void cleanupAfterRebuildFailure(Throwable failure)");

        int initialDelete = rebuild.indexOf("deleteShaders();");
        int cacheVersion = rebuild.indexOf("versionCounterForSodiumShaderReload = version;", initialDelete);
        int tryBlock = rebuild.indexOf("try {", cacheVersion);
        int create = rebuild.indexOf("programs.put(pass, createShader(device, pass, sodiumTerrainPipeline));", tryBlock);
        int runtimeCatch = rebuild.indexOf("catch (RuntimeException exception)", create);
        int runtimeCleanup = rebuild.indexOf("cleanupAfterRebuildFailure(exception);", runtimeCatch);
        int runtimeRethrow = rebuild.indexOf("throw exception;", runtimeCleanup);
        int errorCatch = rebuild.indexOf("catch (Error error)", runtimeRethrow);
        int errorCleanup = rebuild.indexOf("cleanupAfterRebuildFailure(error);", errorCatch);
        int errorRethrow = rebuild.indexOf("throw error;", errorCleanup);

        assertTrue("Rebuild must clear old overrides before storing the new cache key", cacheVersion > initialDelete);
        assertTrue("Override creation loop must run under a partial-build cleanup guard", create > tryBlock);
        assertTrue("Runtime rebuild failures must tear down any partial override cache",
            runtimeCleanup > runtimeCatch);
        assertTrue("Runtime rebuild failures must still propagate to callers",
            runtimeRethrow > runtimeCleanup);
        assertTrue("Hard rebuild failures must tear down any partial override cache",
            errorCleanup > errorCatch);
        assertTrue("Hard rebuild failures must still propagate to callers",
            errorRethrow > errorCleanup);
        assertTrue("Partial rebuild cleanup should reuse the public shader-delete path",
            cleanup.contains("deleteShaders();"));
        assertTrue("Partial rebuild cleanup failures must remain attached to the primary rebuild failure",
            cleanup.contains("suppressCleanupFailure(failure, cleanupFailure);"));
    }

    @Test
    public void cleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same override cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void cleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("override failure");
        RuntimeException cleanupFailure = new RuntimeException("override cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = OculusRelictiumChunkProgramOverrides.class.getDeclaredMethod(
            "suppressCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method signature " + signature, signatureIndex >= 0);
        int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue("Missing method body for " + signature, bodyStart >= 0);

        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, index);
                }
            }
        }

        throw new AssertionError("Unterminated method body for " + signature);
    }
}
