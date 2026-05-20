package net.oculus.colorspace;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ColorSpaceShaderSourceTest {
    @Test
    public void fragmentSourceIsPatchedForLegacyFixedFunctionPipeline() {
        String source = ColorSpaceShaderSource.createFragmentSource(ColorSpace.DISPLAY_P3);

        assertTrue(source.contains("#version 120"));
        assertTrue(source.contains("varying vec2 uv;"));
        assertTrue(source.contains("texture2D(readImage, uv)"));
        assertTrue(source.contains("gl_FragColor = vec4(TargetColor, 1.0);"));
        assertFalse(source.contains("out vec3 outColor"));
        assertFalse(source.contains("#version 330 core"));
    }

    @Test
    public void fragmentVertexSourceUsesLegacyFullscreenQuadInputs() {
        String source = ColorSpaceShaderSource.createFragmentVertexSource();

        assertTrue(source.contains("#version 120"));
        assertTrue(source.contains("gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);"));
        assertTrue(source.contains("uv = gl_MultiTexCoord0.xy;"));
        assertFalse(source.contains("iris_Position"));
        assertFalse(source.contains("iris_UV0"));
        assertFalse(source.contains("uniform mat4 projection"));
    }

    @Test
    public void fragmentConverterKeepsLegacySwapCopyPath() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/colorspace/ColorSpaceFragmentConverter.java")), StandardCharsets.UTF_8);

        int overrideSampler = source.indexOf("builder.overrideSamplerBinding(\"readImage\", TextureBinding.texture2D(() -> targetTexture));");
        int setupTry = source.indexOf("try {", overrideSampler);
        int buildProgram = source.indexOf("this.program = builder.build();", setupTry);
        int createSwap = source.indexOf("this.swapTexture = createSwapTexture(this.width, this.height);",
            buildProgram);
        int createFramebuffer = source.indexOf("this.framebuffer = new GlFramebuffer();", createSwap);
        int addColorAttachment = source.indexOf("this.framebuffer.addColorAttachment(0, this.swapTexture);",
            createFramebuffer);
        int drawBuffers = source.indexOf("this.framebuffer.drawBuffers(new int[] {0});", addColorAttachment);
        int readBuffer = source.indexOf("this.framebuffer.readBuffer(0);", drawBuffers);
        int incompleteThrow = source.indexOf(
            "throw new IllegalStateException(\"Color-space converter framebuffer is incomplete\");",
            readBuffer);
        int setupCatch = source.indexOf("catch (RuntimeException | Error exception)", incompleteThrow);
        int destroyPartial = source.indexOf("destroyResources();", setupCatch);
        int cleanupCatch = source.indexOf("catch (RuntimeException | Error cleanupException)", destroyPartial);
        int suppressSetupCleanup = source.indexOf("addSuppressedCleanupFailure(exception, cleanupException);",
            cleanupCatch);

        int use = source.indexOf("this.program.use();");
        int begin = source.indexOf("FullScreenQuadRenderer.INSTANCE.begin();", use);
        int render = source.indexOf("FullScreenQuadRenderer.INSTANCE.renderQuad();", begin);
        int end = source.indexOf("FullScreenQuadRenderer.end();", render);
        int unbind = source.indexOf("Program.unbind();", end);
        int read = source.indexOf("this.framebuffer.bindAsReadBuffer();", unbind);
        int copyMethod = source.indexOf("copyBackToTargetTexture(targetTexture, this.width, this.height);", read);
        int recordFailure = source.indexOf("failure = exception;", copyMethod);
        int resetTargetTexture = source.indexOf("this.targetTexture = 0;", recordFailure);
        int cleanupProgram = source.indexOf("cleanupFailure = runCleanup(cleanupFailure, Program::unbind);",
            resetTargetTexture);
        int cleanupState = source.indexOf("cleanupFailure = runCleanup(cleanupFailure, state::restore);",
            cleanupProgram);
        int suppressCleanup = source.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);",
            cleanupState);
        int copy = source.indexOf("OculusRenderSystem.copyTexSubImage2D(", source.indexOf("private static void copyBackToTargetTexture"));
        int createSwapTexture = source.indexOf("private static int createSwapTexture");
        int failFast = source.indexOf("throw new IllegalStateException(\"Failed to allocate color-space swap texture\");",
            createSwapTexture);
        int textureFinalLocal = source.indexOf("final int textureId = texture;", createSwapTexture);
        int textureSetupWrapper = source.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {",
            textureFinalLocal);
        int texImage = source.indexOf("OculusRenderSystem.texImage2D(textureId, GL11.GL_TEXTURE_2D", textureFinalLocal);
        int rollbackDelete = source.indexOf("deleteTexture(texture);", texImage);
        int saveFramebuffer = source.indexOf("OculusRenderSystem.getFramebufferBinding()",
            source.indexOf("private static SavedState capture()"));
        int saveReadFramebuffer = source.indexOf("OculusRenderSystem.getReadFramebufferBinding()", saveFramebuffer);
        int saveDrawFramebuffer = source.indexOf("OculusRenderSystem.getDrawFramebufferBinding()", saveReadFramebuffer);
        int restoreFramebuffer = source.indexOf(
            "OculusRenderSystem.restoreFramebufferBindings(framebuffer, readFramebuffer, drawFramebuffer)",
            source.indexOf("private void restore()"));
        int restoreProgram = source.indexOf("failure = runCleanup(failure, () -> GL20.glUseProgram(0));",
            restoreFramebuffer);
        int restoreSavedActive = source.indexOf("OculusRenderSystem.setActiveTextureUnit(activeTexture)",
            restoreProgram);
        int rethrowCleanup = source.indexOf("rethrowCleanupFailure(failure);", restoreSavedActive);

        assertTrue(overrideSampler >= 0);
        assertTrue(setupTry > overrideSampler);
        assertTrue(buildProgram > setupTry);
        assertTrue(createSwap > buildProgram);
        assertTrue(createFramebuffer > createSwap);
        assertTrue(addColorAttachment > createFramebuffer);
        assertTrue(drawBuffers > addColorAttachment);
        assertTrue(readBuffer > drawBuffers);
        assertTrue(incompleteThrow > readBuffer);
        assertTrue(setupCatch > incompleteThrow);
        assertTrue(destroyPartial > setupCatch);
        assertTrue(cleanupCatch > destroyPartial);
        assertTrue(suppressSetupCleanup > cleanupCatch);
        assertTrue(use >= 0);
        assertTrue(begin > use);
        assertTrue(render > begin);
        assertTrue(end > render);
        assertTrue(unbind > end);
        assertTrue(read > unbind);
        assertTrue(copyMethod > read);
        assertTrue(recordFailure > copyMethod);
        assertTrue(resetTargetTexture > recordFailure);
        assertTrue(cleanupProgram > resetTargetTexture);
        assertTrue(cleanupState > cleanupProgram);
        assertTrue(suppressCleanup > cleanupState);
        assertTrue(copy >= 0);
        assertTrue(failFast > createSwapTexture);
        assertTrue(textureFinalLocal >= 0);
        assertTrue(textureSetupWrapper > textureFinalLocal);
        assertTrue(texImage > textureSetupWrapper);
        assertTrue(rollbackDelete > texImage);
        assertTrue(saveFramebuffer >= 0);
        assertTrue(saveReadFramebuffer > saveFramebuffer);
        assertTrue(saveDrawFramebuffer > saveReadFramebuffer);
        assertTrue(restoreFramebuffer >= 0);
        assertTrue(restoreProgram > restoreFramebuffer);
        assertTrue(restoreSavedActive > restoreProgram);
        assertTrue(rethrowCleanup > restoreSavedActive);
        assertTrue(source.contains("private static Throwable runCleanup(Throwable failure, Runnable cleanup)"));
        assertTrue(source.contains("if (exception != failure)"));
        assertTrue(source.contains("failure.addSuppressed(exception);"));
        assertTrue(source.contains("private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure)"));
        assertTrue(source.contains("cleanupFailure != primary"));
        assertFalse(source.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);"));
        assertFalse(source.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D, targetTexture);"));
        assertFalse(source.contains("GL11.glCopyTexSubImage2D"));
        assertFalse(source.contains("GL13.glActiveTexture("));
        assertFalse(source.contains("uniformJomlMatrix"));
        assertFalse(source.contains("builder.addDynamicSampler(() -> targetTexture, \"readImage\")"));
    }

    @Test
    public void fragmentConverterDestroyAggregatesAndClearsOwnedResources() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/colorspace/ColorSpaceFragmentConverter.java")), StandardCharsets.UTF_8);
        String destroy = section(source, "private void destroyResources()", "private static void copyBackToTargetTexture");

        int oldProgram = destroy.indexOf("Program oldProgram = program;");
        int oldFramebuffer = destroy.indexOf("GlFramebuffer oldFramebuffer = framebuffer;", oldProgram);
        int oldSwap = destroy.indexOf("int oldSwapTexture = swapTexture;", oldFramebuffer);
        int clearProgram = destroy.indexOf("program = null;", oldSwap);
        int clearFramebuffer = destroy.indexOf("framebuffer = null;", clearProgram);
        int clearSwap = destroy.indexOf("swapTexture = 0;", clearFramebuffer);
        int clearTarget = destroy.indexOf("targetTexture = 0;", clearSwap);
        int failure = destroy.indexOf("Throwable failure = null;", clearTarget);
        int destroyProgram = destroy.indexOf("failure = runCleanup(failure, oldProgram::destroy);", failure);
        int destroyFramebuffer = destroy.indexOf("failure = runCleanup(failure, oldFramebuffer::destroy);",
            destroyProgram);
        int deleteSwap = destroy.indexOf("failure = runCleanup(failure, () -> deleteTexture(oldSwapTexture));",
            destroyFramebuffer);
        int rethrow = destroy.indexOf("rethrowCleanupFailure(failure);", deleteSwap);

        assertTrue(oldProgram >= 0);
        assertTrue(oldFramebuffer > oldProgram);
        assertTrue(oldSwap > oldFramebuffer);
        assertTrue("Color-space destroy must clear owned fields before cleanup can throw",
            clearProgram > oldSwap && clearFramebuffer > clearProgram && clearSwap > clearFramebuffer
                && clearTarget > clearSwap);
        assertTrue(failure > clearTarget);
        assertTrue(destroyProgram > failure);
        assertTrue(destroyFramebuffer > destroyProgram);
        assertTrue(deleteSwap > destroyFramebuffer);
        assertTrue(rethrow > deleteSwap);
    }

    @Test
    public void fragmentConverterTextureDeleteNotifiesLifecycleEvenWhenGlDeleteFails() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/colorspace/ColorSpaceFragmentConverter.java")), StandardCharsets.UTF_8);
        String create = section(source, "private static int createSwapTexture", "private static void deleteTexture");
        String delete = section(source, "private static void deleteTexture", "private static final class SavedState");

        int setupFailure = create.indexOf("Throwable setupFailure = null;");
        int setupCatch = create.indexOf("catch (RuntimeException | Error exception)", setupFailure);
        int rememberSetup = create.indexOf("setupFailure = exception;", setupCatch);
        int rollback = create.indexOf("deleteTexture(texture);", rememberSetup);
        int rollbackCatch = create.indexOf("catch (RuntimeException | Error cleanupException)", rollback);
        int suppress = create.indexOf("addSuppressedCleanupFailure(setupFailure, cleanupException);", rollbackCatch);
        int throwCleanup = create.indexOf("throw cleanupException;", suppress);

        assertTrue(setupFailure >= 0);
        assertTrue(setupCatch > setupFailure);
        assertTrue(rememberSetup > setupCatch);
        assertTrue(rollback > rememberSetup);
        assertTrue(rollbackCatch > rollback);
        assertTrue("Color-space swap texture rollback cleanup must be suppressed onto setup failure",
            suppress > rollbackCatch && throwCleanup > suppress);

        int positiveGuard = delete.indexOf("if (texture <= 0)");
        int failure = delete.indexOf("Throwable failure = null;", positiveGuard);
        int glDelete = delete.indexOf("GL11.glDeleteTextures(texture);", failure);
        int catchDelete = delete.indexOf("catch (RuntimeException | Error exception)", glDelete);
        int collectDelete = delete.indexOf("failure = addCleanupFailure(failure, exception);", catchDelete);
        int finallyBlock = delete.indexOf("} finally {", collectDelete);
        int trackerNotify = delete.indexOf("TextureLifecycleTracker.onDeleteTexture(texture);", finallyBlock);
        int catchTracker = delete.indexOf("catch (RuntimeException | Error exception)", trackerNotify);
        int collectTracker = delete.indexOf("failure = addCleanupFailure(failure, exception);", catchTracker);
        int rethrow = delete.indexOf("rethrowCleanupFailure(failure);", collectTracker);

        assertTrue(positiveGuard >= 0);
        assertTrue(failure > positiveGuard);
        assertTrue(glDelete > failure);
        assertTrue(catchDelete > glDelete);
        assertTrue(collectDelete > catchDelete);
        assertTrue("Color-space texture lifecycle cleanup must run after GL delete failure capture",
            finallyBlock > collectDelete && trackerNotify > finallyBlock);
        assertTrue(catchTracker > trackerNotify);
        assertTrue(collectTracker > catchTracker);
        assertTrue(rethrow > collectTracker);
    }

    @Test
    public void fragmentConverterCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same fragment cleanup failure");

        Throwable returned = addFragmentCleanupFailure(failure, failure);
        addFragmentSuppressedCleanupFailure(failure, failure);

        assertSame(failure, returned);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void fragmentConverterCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("fragment failure");
        RuntimeException firstCleanup = new RuntimeException("first fragment cleanup failure");
        RuntimeException secondCleanup = new RuntimeException("second fragment cleanup failure");

        Throwable returned = addFragmentCleanupFailure(failure, firstCleanup);
        addFragmentSuppressedCleanupFailure(failure, secondCleanup);

        assertSame(failure, returned);
        assertEquals(2, failure.getSuppressed().length);
        assertSame(firstCleanup, failure.getSuppressed()[0]);
        assertSame(secondCleanup, failure.getSuppressed()[1]);
    }

    @Test
    public void computeSourceKeepsComputeVersionAndImageBinding() {
        String source = ColorSpaceShaderSource.createComputeSource(ColorSpace.REC2020);

        assertTrue(source.matches("(?s).*#version\\s+430\\s+core.*"));
        assertTrue(source.matches("(?s).*layout\\s*\\(\\s*local_size_x\\s*=\\s*8\\s*,\\s*local_size_y\\s*=\\s*8\\s*\\)\\s*in\\s*;.*"));
        assertTrue(source.matches("(?s).*uniform\\s+image2D\\s+readImage\\s*;.*"));
    }

    @Test
    public void computeConverterUsesReferenceImageBindingCall() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/colorspace/ColorSpaceComputeConverter.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains(
            "builder.addTextureImage(() -> targetTexture, InternalTextureFormat.RGBA8, \"readImage\");"));
        assertFalse(source.contains("\"readImage\", false"));
    }

    @Test
    public void computeConverterUsesReferenceDispatchAndUnbindShape() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/colorspace/ColorSpaceComputeConverter.java")), StandardCharsets.UTF_8);
        String computeProgram = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/program/ComputeProgram.java")), StandardCharsets.UTF_8);

        int target = source.indexOf("this.targetTexture = targetTexture;");
        int failure = source.indexOf("Throwable failure = null;", target);
        int tryBlock = source.indexOf("try {", failure);
        int use = source.indexOf("program.use();", tryBlock);
        int dispatch = source.indexOf("OculusRenderSystem.dispatchCompute(width / 8, height / 8, 1);", use);
        int barrier = source.indexOf("OculusRenderSystem.memoryBarrier", dispatch);
        int catchBlock = source.indexOf("catch (RuntimeException | Error exception)", barrier);
        int rememberFailure = source.indexOf("failure = exception;", catchBlock);
        int rethrowFailure = source.indexOf("throw exception;", rememberFailure);
        int finallyBlock = source.indexOf("finally {", rethrowFailure);
        int resetTarget = source.indexOf("this.targetTexture = 0;", finallyBlock);
        int unbind = source.indexOf("ComputeProgram.unbind();", resetTarget);
        int cleanupCatch = source.indexOf("catch (RuntimeException | Error cleanupException)", unbind);
        int suppress = source.indexOf("suppressCleanupFailure(failure, cleanupException);", cleanupCatch);
        int throwCleanup = source.indexOf("throw cleanupException;", suppress);

        assertTrue(target >= 0);
        assertTrue(failure > target);
        assertTrue(tryBlock > failure);
        assertTrue(use > tryBlock);
        assertTrue(dispatch > use);
        assertTrue(barrier > dispatch);
        assertTrue(catchBlock > barrier);
        assertTrue(rememberFailure > catchBlock);
        assertTrue(rethrowFailure > rememberFailure);
        assertTrue(finallyBlock > rethrowFailure);
        assertTrue(resetTarget > finallyBlock);
        assertTrue(unbind > resetTarget);
        assertTrue(cleanupCatch > unbind);
        assertTrue(suppress > cleanupCatch);
        assertTrue(throwCleanup > suppress);
        assertTrue(source.contains("private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure)"));
        assertTrue(source.contains("cleanupFailure != failure"));
        assertFalse(source.contains("program.dispatch(this.width, this.height);"));
        assertFalse(source.contains("this.program.dispatch(this.width, this.height);"));
        assertTrue(computeProgram.contains("public static void unbind()"));
    }

    @Test
    public void computeConverterCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same compute cleanup failure");

        suppressComputeCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void computeConverterCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("compute failure");
        RuntimeException cleanupFailure = new RuntimeException("compute cleanup failure");

        suppressComputeCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void computeConverterDestroyClearsOwnedProgramBeforeCleanupCanThrow() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/colorspace/ColorSpaceComputeConverter.java")), StandardCharsets.UTF_8);
        String destroy = section(source, "private void destroyResources()", "}");

        int oldProgram = destroy.indexOf("ComputeProgram oldProgram = program;");
        int clearProgram = destroy.indexOf("program = null;", oldProgram);
        int clearTarget = destroy.indexOf("targetTexture = 0;", clearProgram);
        int guard = destroy.indexOf("if (oldProgram != null)", clearTarget);
        int destroyProgram = destroy.indexOf("oldProgram.destroy();", guard);

        assertTrue(oldProgram >= 0);
        assertTrue("Compute color-space destroy must clear the program field before cleanup can throw",
            clearProgram > oldProgram);
        assertTrue("Compute color-space destroy must clear the target image before cleanup can throw",
            clearTarget > clearProgram);
        assertTrue("Compute color-space destroy must run cleanup through the captured program reference",
            guard > clearTarget && destroyProgram > guard);
        assertFalse("A failed destroy must not leave the old Program reference installed",
            destroy.contains("program.destroy();"));
    }

    @Test
    public void configValuesAcceptReferenceNamesAndReadableAliases() {
        assertTrue(ColorSpace.fromConfigValue("DISPLAY_P3") == ColorSpace.DISPLAY_P3);
        assertTrue(ColorSpace.fromConfigValue("display-p3") == ColorSpace.DISPLAY_P3);
        assertTrue(ColorSpace.fromConfigValue("invalid") == ColorSpace.SRGB);
    }

    private static String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0);
        assertTrue(endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }

    private static Throwable addFragmentCleanupFailure(Throwable failure, Throwable exception) throws Exception {
        Method method = ColorSpaceFragmentConverter.class.getDeclaredMethod(
            "addCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(null, failure, exception);
    }

    private static void addFragmentSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure) throws Exception {
        Method method = ColorSpaceFragmentConverter.class.getDeclaredMethod(
            "addSuppressedCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, primary, cleanupFailure);
    }

    private static void suppressComputeCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = ColorSpaceComputeConverter.class.getDeclaredMethod(
            "suppressCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }
}
