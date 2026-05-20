package net.oculus.pipeline;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ClearPassSourceTest {
    @Test
    public void clearPassForcesAndRestoresMutatedGlState() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ClearPass.java");
        String execute = source.substring(
            source.indexOf("public void execute(Vector4f defaultClearColor)"),
            source.indexOf("public GlFramebuffer getFramebuffer()"));
        String restoreColorMask = source.substring(
            source.indexOf("private static void restoreColorMask"),
            source.lastIndexOf("\n}"));

        int saveColorMaskBuffer = execute.indexOf("ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);");
        int saveClearColorBuffer = execute.indexOf("FloatBuffer previousClearColor = BufferUtils.createFloatBuffer(16);",
            saveColorMaskBuffer);
        int saveViewportBuffer = execute.indexOf("IntBuffer previousViewport = BufferUtils.createIntBuffer(16);",
            saveClearColorBuffer);
        int readColorMask = execute.indexOf("GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);",
            saveViewportBuffer);
        int readClearColor = execute.indexOf("GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);",
            readColorMask);
        int readViewport = execute.indexOf("GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);",
            readClearColor);
        int saveFramebuffer = execute.indexOf("int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();",
            readViewport);
        int saveReadFramebuffer = execute.indexOf(
            "int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();", saveFramebuffer);
        int saveDrawFramebuffer = execute.indexOf(
            "int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();", saveReadFramebuffer);
        int tryBlock = execute.indexOf("try {", saveDrawFramebuffer);
        int bindFramebuffer = execute.indexOf("framebuffer.bind();", tryBlock);
        int forceColorMask = execute.indexOf("GlStateManager.colorMask(true, true, true, true);",
            bindFramebuffer);
        int clearColor = execute.indexOf(
            "GL11.glClearColor(clearColor.x(), clearColor.y(), clearColor.z(), clearColor.w());",
            forceColorMask);
        int clear = execute.indexOf("GL11.glClear(clearFlags);", clearColor);
        int catchBlock = execute.indexOf("catch (RuntimeException | Error exception)", clear);
        int recordFailure = execute.indexOf("failure = exception;", catchBlock);
        int finallyBlock = execute.indexOf("finally {", recordFailure);
        int cleanupLocal = execute.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int restoreMask = execute.indexOf(
            "cleanupFailure = runCleanup(cleanupFailure, () -> restoreColorMask(previousColorMask));",
            cleanupLocal);
        int restoreClearColor = execute.indexOf("cleanupFailure = runCleanup(cleanupFailure, () -> GL11.glClearColor(",
            restoreMask);
        int restoreFramebuffer = execute.indexOf(
            "cleanupFailure = runCleanup(cleanupFailure, () -> OculusRenderSystem.restoreFramebufferBindings(",
            restoreClearColor);
        int restoreViewport = execute.indexOf("cleanupFailure = runCleanup(cleanupFailure, () -> GL11.glViewport(",
            restoreFramebuffer);
        int suppressCleanup = execute.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);",
            restoreViewport);
        int rethrowCleanup = execute.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressCleanup);

        assertTrue("ClearPass must allocate storage for the incoming color mask", saveColorMaskBuffer >= 0);
        assertTrue("ClearPass must allocate storage for the incoming clear color",
            saveClearColorBuffer > saveColorMaskBuffer);
        assertTrue("ClearPass must allocate storage for the incoming viewport",
            saveViewportBuffer > saveClearColorBuffer);
        assertTrue("ClearPass must read the caller color mask before mutating clear state",
            readColorMask > saveViewportBuffer);
        assertTrue("ClearPass must read the caller clear color before mutating clear state",
            readClearColor > readColorMask);
        assertTrue("ClearPass must read the caller viewport before mutating clear state",
            readViewport > readClearColor);
        assertTrue("ClearPass must snapshot caller framebuffer bindings before binding its clear framebuffer",
            saveFramebuffer > readViewport && saveReadFramebuffer > saveFramebuffer
                && saveDrawFramebuffer > saveReadFramebuffer);
        assertTrue("ClearPass must bind its framebuffer before forcing clear writes",
            bindFramebuffer > tryBlock);
        assertTrue("ClearPass must force full color writes before clearing render targets",
            forceColorMask > bindFramebuffer);
        assertTrue("ClearPass must set the intended clear color after forcing color writes",
            clearColor > forceColorMask);
        assertTrue("ClearPass must perform the clear after the guarded state changes",
            clear > clearColor);
        assertTrue("ClearPass must preserve a primary clear failure before running cleanup",
            catchBlock > clear && recordFailure > catchBlock);
        assertTrue("ClearPass must restore color-mask state from the cleanup path",
            cleanupLocal > finallyBlock && restoreMask > cleanupLocal);
        assertTrue("ClearPass must restore the previous clear color after restoring the mask",
            restoreClearColor > restoreMask);
        assertTrue("ClearPass must restore framebuffer bindings after clear state restoration",
            restoreFramebuffer > restoreClearColor);
        assertTrue("ClearPass must restore the previous viewport after framebuffer bindings",
            restoreViewport > restoreFramebuffer);
        assertTrue("ClearPass cleanup failures must suppress onto primary failures or rethrow alone",
            suppressCleanup > restoreViewport && rethrowCleanup > suppressCleanup);

        assertTrue(restoreColorMask.contains("previousColorMask.get(0) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(1) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(2) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(3) != 0"));
        assertTrue(source.contains("private static Throwable runCleanup(Throwable failure, Runnable cleanup)"));
        assertTrue(source.contains("failure.addSuppressed(exception);"));
        assertTrue(source.contains("throw (RuntimeException) failure;"));
        assertTrue(source.contains("throw (Error) failure;"));
        assertTrue(source.contains("import net.oculus.gl.OculusRenderSystem;"));
    }

    @Test
    public void shadowClearPassCreatorMatchesReferenceGroupingAndFramebufferOwnership() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ClearPassCreator.java");
        String renderSystem = read("src/main/java/net/oculus/gl/OculusRenderSystem.java");
        String createShadowClearPasses = source.substring(
            source.indexOf("public static List<ClearPass> createShadowClearPasses"),
            source.indexOf("private static Throwable destroyClearPassFramebuffers"));
        String addShadowClearPass = source.substring(
            source.indexOf("private static void addShadowClearPass"),
            source.indexOf("private static Throwable destroyClearPassFramebuffers"));
        String destroyShadowClearPassFramebuffers = source.substring(
            source.indexOf("private static Throwable destroyShadowClearPassFramebuffers"),
            source.indexOf("private static Throwable runCleanup"));

        int maxDrawBuffers = createShadowClearPasses.indexOf(
            "int maxDrawBuffers = Math.max(1, OculusRenderSystem.getMaxDrawBuffers());");
        int groupByColor = createShadowClearPasses.indexOf("Map<Vector4f, List<Integer>> buffersByClearPass",
            maxDrawBuffers);
        int colorSettingsLoop = createShadowClearPasses.indexOf(
            "for (int i = 0; i < shadowDirectives.getColorSamplingSettings().size(); i++)", groupByColor);
        int clearDecision = createShadowClearPasses.indexOf("if (!fullClear && !settings.shouldClear())",
            colorSettingsLoop);
        int addBuffer = createShadowClearPasses.indexOf(
            "buffersByClearPass.computeIfAbsent(settings.getClearColor(), ignored -> new ArrayList<>()).add(i);",
            clearDecision);
        int chunkCount = createShadowClearPasses.indexOf(
            "int count = Math.min(buffers.size() - startIndex, maxDrawBuffers);", addBuffer);
        int createAlt = createShadowClearPasses.indexOf(
            "addShadowClearPass(clearPasses, shadowMap, clearColor, true, clearBuffers);",
            chunkCount);
        int createMain = createShadowClearPasses.indexOf(
            "addShadowClearPass(clearPasses, shadowMap, clearColor, false, clearBuffers);",
            createAlt);
        int catchBlock = createShadowClearPasses.indexOf("catch (RuntimeException | Error exception)", createMain);
        int cleanup = createShadowClearPasses.indexOf(
            "destroyShadowClearPassFramebuffers(null, shadowMap, clearPasses)", catchBlock);
        int pendingFramebuffer = addShadowClearPass.indexOf("GlFramebuffer pendingFramebuffer = alt");
        int helperCreateAlt = addShadowClearPass.indexOf("shadowMap.createFramebufferWritingToAlt(clearBuffers)",
            pendingFramebuffer);
        int helperCreateMain = addShadowClearPass.indexOf("shadowMap.createFramebufferWritingToMain(clearBuffers)",
            helperCreateAlt);
        int addPass = addShadowClearPass.indexOf("clearPasses.add(new ClearPass(", helperCreateMain);
        int publish = addShadowClearPass.indexOf("pendingFramebuffer = null;", addPass);
        int helperCatch = addShadowClearPass.indexOf("catch (RuntimeException | Error exception)", publish);
        int destroyPending = addShadowClearPass.indexOf("shadowMap.destroyFramebuffer(framebuffer)", helperCatch);

        assertTrue(source.contains("import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;"));
        assertTrue(source.contains("import net.oculus.gl.OculusRenderSystem;"));
        assertTrue(source.contains("import net.oculus.pipeline.shadow.ShadowMap;"));
        assertTrue(renderSystem.contains("public static int getMaxDrawBuffers()"));
        assertTrue(renderSystem.contains("capabilities.GL_ARB_draw_buffers"));
        assertTrue("Shadow clear passes must chunk by the backend draw-buffer limit", maxDrawBuffers >= 0);
        assertTrue("Shadow clear passes must group by clear color before creating framebuffers",
            groupByColor > maxDrawBuffers && colorSettingsLoop > groupByColor);
        assertTrue("Shadow clear passes must honor fullClear and per-buffer clear directives",
            clearDecision > colorSettingsLoop && addBuffer > clearDecision);
        assertTrue("Shadow clear passes must chunk same-color buffers by GL_MAX_DRAW_BUFFERS",
            chunkCount > addBuffer);
        assertTrue("Shadow clear passes must create alternate-target clears before main-target clears like 1.16.5",
            createAlt > chunkCount && createMain > createAlt);
        assertTrue("Shadow clear-pass creation must destroy partial persistent framebuffers on setup failure",
            catchBlock > createMain && cleanup > catchBlock);
        assertTrue("Shadow clear-pass publication must destroy an unpublished pending framebuffer on failure",
            pendingFramebuffer >= 0 && helperCreateAlt > pendingFramebuffer && helperCreateMain > helperCreateAlt
                && addPass > helperCreateMain && publish > addPass && helperCatch > publish
                && destroyPending > helperCatch);
        assertTrue(destroyShadowClearPassFramebuffers.contains("shadowMap.destroyFramebuffer(clearPass.getFramebuffer())"));
        assertTrue("Regular clear passes must use the same backend-aware draw-buffer limit",
            source.contains("public static List<ClearPass> createClearPasses")
                && source.indexOf("int maxDrawBuffers = Math.max(1, OculusRenderSystem.getMaxDrawBuffers());")
                < source.indexOf("public static List<ClearPass> createShadowClearPasses"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
