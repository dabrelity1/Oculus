package net.oculus.gl.program;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import net.oculus.gl.texture.InternalTextureFormat;
import org.junit.Test;

public class ProgramImagesTest {
    @Test
    public void missingImageUniformDoesNotRequireImageUnitSupport() {
        ProgramImages.Builder builder = ProgramImages.builder(7, () -> 0, (program, name) -> -1);
        builder.addTextureImage(() -> 11, InternalTextureFormat.RGBA8, "unusedImage");

        assertEquals(0, builder.build().getActiveImages());
    }

    @Test
    public void activeImageUniformFailsFastWhenImageUnitsAreUnsupported() {
        try {
            ProgramImages.builder(7, () -> 0, (program, name) -> 3)
                .addTextureImage(() -> 11, InternalTextureFormat.RGBA8, "activeImage");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("Image units are not supported"));
            assertTrue(ex.getMessage().contains("activeImage"));
            return;
        }

        throw new AssertionError("Expected active image uniform to fail when no image units are available");
    }

    @Test
    public void imageUnitLimitFailsFastWhenExhausted() {
        ProgramImages.Builder builder = ProgramImages.builder(7, () -> 1, (program, name) -> 3);
        builder.addTextureImage(() -> 11, InternalTextureFormat.RGBA8, "firstImage");

        try {
            builder.addTextureImage(() -> 12, InternalTextureFormat.RGBA8, "secondImage");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("No more available texture units"));
            assertTrue(ex.getMessage().contains("Only 1 image units are available."));
            assertTrue(ex.getMessage().contains("secondImage"));
            return;
        }

        throw new AssertionError("Expected second active image uniform to exceed the image unit limit");
    }

    @Test
    public void imageBindingUsesReferenceLayeredBindCall() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/image/ImageBinding.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("bindImageTexture(imageUnit, textureId.getAsInt(), 0, true, 0,"));
        assertFalse(source.contains("boolean layered"));
        assertFalse(source.contains("private final boolean layered"));
    }

    @Test
    public void rgbaImageFormatUsesReferenceRgba8InternalFormat() throws Exception {
        ProgramImages.Builder builder = ProgramImages.builder(7, () -> 1, (program, name) -> 3);
        builder.addTextureImage(() -> 11, InternalTextureFormat.RGBA, "rgbaImage");

        Object binding = firstImageBinding(builder.build());

        assertEquals(InternalTextureFormat.RGBA8.getGlFormat(), intField(binding, "internalFormat"));
    }

    @Test
    public void activeImageBindingsAreClearedOnProgramSwitchAndUnbind() throws Exception {
        String programImages = read("src/main/java/net/oculus/gl/program/ProgramImages.java");
        String programUniforms = read("src/main/java/net/oculus/gl/program/ProgramUniforms.java");
        String programSamplers = read("src/main/java/net/oculus/gl/program/ProgramSamplers.java");
        String imageBinding = read("src/main/java/net/oculus/gl/image/ImageBinding.java");
        String updateBody = methodBody(programImages, "public void update()");
        String previousCleanupBody = methodBody(programImages, "private Throwable cleanupPreviousActiveBeforeUpdate()");
        String clearBody = methodBody(programImages, "public static void clearActiveImages()");
        String clearImagesIfActive = methodBody(programImages,
            "static void clearActiveImages(ProgramImages images)");
        String clearUniformsIfActive = methodBody(programUniforms,
            "static void clearActiveUniforms(ProgramUniforms uniforms)");
        String clearSamplersIfActive = methodBody(programSamplers,
            "static void clearActiveSamplers(ProgramSamplers samplers)");
        String programSource = read("src/main/java/net/oculus/gl/program/Program.java");
        String programUnbind = methodBody(programSource, "public static void unbind()");
        String programCleanup = methodBody(programSource, "static void clearActiveBindingsAndProgram()");
        String programDestroy = methodBody(programSource,
            "protected void destroyInternal()");
        String computeUnbind = methodBody(read("src/main/java/net/oculus/gl/program/ComputeProgram.java"),
            "public static void unbind()");

        int previousCleanup = updateBody.indexOf("Throwable previousCleanupFailure = cleanupPreviousActiveBeforeUpdate();");
        int activeSet = updateBody.indexOf("active = this;", previousCleanup);
        int initializer = updateBody.indexOf("if (initializer != null)", activeSet);

        assertTrue("ProgramImages should track the active image binding set",
            programImages.contains("private static ProgramImages active;"));
        assertTrue("ProgramImages.update should attempt stale image-unit cleanup before publishing the new set",
            previousCleanup >= 0);
        assertTrue("ProgramImages should keep re-updating the same image set from clearing itself",
            previousCleanupBody.contains("current == null || current == this"));
        assertTrue("ProgramImages should aggregate previous image cleanup failures before activation",
            previousCleanupBody.contains("return runCleanup(null, current::unbind);"));
        assertTrue("ProgramImages.update should publish the new active set before binding images",
            activeSet > previousCleanup);
        assertTrue("ProgramImages.update should still initialize image uniforms after active tracking",
            initializer > activeSet);
        assertTrue("ProgramImages.clearActiveImages should unbind the active image set through cleanup aggregation",
            clearBody.contains("failure = runCleanup(failure, current::unbind);"));
        assertTrue("ProgramImages.clearActiveImages should drop the active image set",
            clearBody.contains("active = null;"));
        assertTrue("ImageBinding.unbind should clear the image unit",
            imageBinding.contains("bindImageTexture(imageUnit, 0, 0, true, 0,"));
        assertTrue("Program.unbind should route through the aggregated active binding cleanup helper",
            programUnbind.contains("clearActiveBindingsAndProgram();"));
        assertAggregatedCleanupStep(programCleanup, "ProgramUniforms::clearActiveUniforms",
            "ProgramSamplers::clearActiveSamplers");
        assertAggregatedCleanupStep(programCleanup, "ProgramSamplers::clearActiveSamplers",
            "ProgramImages::clearActiveImages");
        assertAggregatedCleanupStep(programCleanup, "ProgramImages::clearActiveImages",
            "OculusRenderSystem.glUseProgram(0)");
        assertTrue("Program unbind cleanup should rethrow only after all cleanup steps have been attempted",
            programCleanup.contains("rethrowCleanupFailure(failure);"));
        assertAggregatedCleanupStep(programDestroy, "ProgramUniforms.clearActiveUniforms(uniforms)",
            "ProgramSamplers.clearActiveSamplers(samplers)");
        assertAggregatedCleanupStep(programDestroy, "ProgramSamplers.clearActiveSamplers(samplers)",
            "ProgramImages.clearActiveImages(images)");
        assertAggregatedCleanupStep(programDestroy, "ProgramImages.clearActiveImages(images)",
            "OculusRenderSystem.glDeleteProgram(getGlId())");
        assertTrue("Program.destroyInternal should rethrow cleanup failures only after all cleanup steps",
            programDestroy.contains("rethrowCleanupFailure(failure);"));
        assertTrue("ProgramImages should leave unrelated active image sets alone during inactive program destroy",
            clearImagesIfActive.contains("if (active == images)") &&
                clearImagesIfActive.contains("clearActiveImages();"));
        assertTrue("ProgramUniforms should leave unrelated active uniform listeners alone during inactive program destroy",
            clearUniformsIfActive.contains("if (active == uniforms)") &&
                clearUniformsIfActive.contains("clearActiveUniforms();"));
        assertTrue("ProgramSamplers should leave unrelated active sampler listeners/bindings alone during inactive program destroy",
            clearSamplersIfActive.contains("if (active == samplers)") &&
                clearSamplersIfActive.contains("clearActiveSamplers();"));
        assertTrue("Program.destroyInternal should only delete GL handles owned by the Program wrapper",
            programDestroy.indexOf("if (ownsProgramHandle)") < programDestroy.indexOf(
                "OculusRenderSystem.glDeleteProgram(getGlId())"));
        assertTrue("ComputeProgram.unbind should share Program's aggregated active binding cleanup",
            computeUnbind.contains("Program.clearActiveBindingsAndProgram();"));
    }

    @Test
    public void imageCleanupAttemptsEveryBindingBeforeRethrowing() throws Exception {
        String programImages = read("src/main/java/net/oculus/gl/program/ProgramImages.java");
        String clearBody = methodBody(programImages, "public static void clearActiveImages()");
        String unbindBody = methodBody(programImages, "private void unbind()");

        int current = clearBody.indexOf("ProgramImages current = active;");
        int runCleanup = clearBody.indexOf("failure = runCleanup(failure, current::unbind);", current);
        int finallyBlock = clearBody.indexOf("} finally {", runCleanup);
        int activeNull = clearBody.indexOf("active = null;", finallyBlock);
        int rethrow = clearBody.indexOf("rethrowCleanupFailure(failure);", activeNull);

        int loop = unbindBody.indexOf("for (ImageBinding binding : imageBindings)");
        int aggregate = unbindBody.indexOf("failure = runCleanup(failure, binding::unbind);", loop);
        int unbindRethrow = unbindBody.indexOf("rethrowCleanupFailure(failure);", aggregate);

        assertTrue(current >= 0);
        assertTrue(runCleanup > current);
        assertTrue(finallyBlock > runCleanup);
        assertTrue(activeNull > finallyBlock);
        assertTrue(rethrow > activeNull);
        assertTrue(loop >= 0);
        assertTrue(aggregate > loop);
        assertTrue(unbindRethrow > aggregate);
    }

    @Test
    public void failedImageUpdateClearsPublishedActiveBindingSetBeforeRethrowing() throws Exception {
        String programImages = read("src/main/java/net/oculus/gl/program/ProgramImages.java");
        String updateBody = methodBody(programImages, "public void update()");
        String cleanupBody = methodBody(programImages,
            "private static void cleanupAfterFailedUpdate(Throwable failure)");

        int activeSet = updateBody.indexOf("active = this;");
        int tryBlock = updateBody.indexOf("try {", activeSet);
        int initializer = updateBody.indexOf("if (initializer != null)", tryBlock);
        int updateLoop = updateBody.indexOf("for (ImageBinding binding : imageBindings)", initializer);
        int bindingUpdate = updateBody.indexOf("binding.update();", updateLoop);
        int runtimeCatch = updateBody.indexOf("catch (RuntimeException exception)", bindingUpdate);
        int runtimeSuppress = updateBody.indexOf("suppressCleanupFailure(exception, previousCleanupFailure);", runtimeCatch);
        int runtimeCleanup = updateBody.indexOf("cleanupAfterFailedUpdate(exception);", runtimeSuppress);
        int errorCatch = updateBody.indexOf("catch (Error error)", runtimeCleanup);
        int errorSuppress = updateBody.indexOf("suppressCleanupFailure(error, previousCleanupFailure);", errorCatch);
        int errorCleanup = updateBody.indexOf("cleanupAfterFailedUpdate(error);", errorSuppress);

        assertTrue(activeSet >= 0);
        assertTrue(tryBlock > activeSet);
        assertTrue(initializer > tryBlock);
        assertTrue(updateLoop > initializer);
        assertTrue(bindingUpdate > updateLoop);
        assertTrue(runtimeCatch > bindingUpdate);
        assertTrue(runtimeSuppress > runtimeCatch);
        assertTrue(runtimeCleanup > runtimeSuppress);
        assertTrue(errorCatch > runtimeCleanup);
        assertTrue(errorSuppress > errorCatch);
        assertTrue(errorCleanup > errorSuppress);
        assertTrue(cleanupBody.contains("clearActiveImages();"));
        assertTrue(cleanupBody.contains("suppressCleanupFailure(failure, cleanupFailure);"));
        assertTrue(programImages.contains("private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure)"));
    }

    @Test
    public void imageCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same image binding cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void imageCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("image binding failure");
        RuntimeException cleanupFailure = new RuntimeException("image binding cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    private static Object firstImageBinding(ProgramImages images) throws Exception {
        Field field = ProgramImages.class.getDeclaredField("imageBindings");
        field.setAccessible(true);
        List<?> bindings = (List<?>) field.get(images);
        return bindings.get(0);
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = ProgramImages.class.getDeclaredMethod(
            "suppressCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }

    private static void assertAggregatedCleanupStep(String source, String previous, String next) {
        int previousIndex = source.indexOf(previous);
        int nextIndex = source.indexOf(next, previousIndex + previous.length());
        assertTrue("Expected cleanup step " + previous, previousIndex >= 0);
        assertTrue("Expected cleanup step " + next + " after " + previous, nextIndex > previousIndex);
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
