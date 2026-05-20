package net.oculus.texture;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class FallbackTexturesTest {
    @Test
    public void whiteFallbackTextureUsesOpaqueRgbaBytes() {
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
            FallbackTextures.rgbaBytes(0xFFFFFFFF));
    }

    @Test
    public void fallbackTextureOwnerCleansStateAndRollbackThroughSafeDelete() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/FallbackTextures.java")), StandardCharsets.UTF_8);
        String destroyBody = methodBody(source, "public static void destroy()");
        String createBody = methodBody(source, "private static int createSingleColorTexture(int rgba)");
        String restoreBody = methodBody(source,
            "private static void restorePreviousTextureBinding(int previousTexture, Throwable setupFailure)");
        String deleteBody = methodBody(source, "private static void deleteTexture(int textureId, String description)");

        int captureDestroyTexture = destroyBody.indexOf("int texture = whiteTexture;");
        int clearWhiteTexture = destroyBody.indexOf("whiteTexture = 0;", captureDestroyTexture);
        int destroyDelete = destroyBody.indexOf("deleteTexture(texture, \"white fallback texture\");", clearWhiteTexture);

        int genTexture = createBody.indexOf("int texture = GL11.glGenTextures();");
        int failFast = createBody.indexOf("throw new IllegalStateException(\"Failed to allocate white fallback texture\");",
            genTexture);
        int successFlag = createBody.indexOf("boolean success = false;", failFast);
        int setupFailureLocal = createBody.indexOf("Throwable setupFailure = null;", successFlag);
        int restoreFailureLocal = createBody.indexOf("Throwable restoreFailure = null;", setupFailureLocal);
        int upload = createBody.indexOf("TextureLifecycleTracker.onTexImage2D(", restoreFailureLocal);
        int successSet = createBody.indexOf("success = true;", upload);
        int setupCatchBlock = createBody.indexOf("catch (RuntimeException | Error exception)", successSet);
        int setupFailureCapture = createBody.indexOf("setupFailure = exception;", setupCatchBlock);
        int rethrow = createBody.indexOf("throw exception;", setupFailureCapture);
        int restoreBinding = createBody.indexOf("restorePreviousTextureBinding(previousTexture, setupFailure);", rethrow);
        int restoreCatchBlock = createBody.indexOf("catch (RuntimeException | Error exception)", restoreBinding);
        int restoreFailureCapture = createBody.indexOf("restoreFailure = exception;", restoreCatchBlock);
        int rethrowRestore = createBody.indexOf("throw exception;", restoreFailureCapture);
        int cleanupGuard = createBody.indexOf("if (!success || restoreFailure != null)", rethrowRestore);
        int rollbackDelete = createBody.indexOf("deleteTexture(texture, \"white fallback texture\");", cleanupGuard);
        int restoreRawBind = restoreBody.indexOf("GlStateManager.bindTexture(previousTexture);");
        int restoreCatch = restoreBody.indexOf("catch (RuntimeException | Error restoreFailure)", restoreRawBind);
        int restoreGuard = restoreBody.indexOf("if (setupFailure != null)", restoreCatch);
        int suppress = restoreBody.indexOf("suppressRestoreFailure(setupFailure, restoreFailure);", restoreGuard);
        int keepOriginal = restoreBody.indexOf("return;", suppress);
        int failFastRestore = restoreBody.indexOf("throw restoreFailure;", keepOriginal);
        int glDelete = deleteBody.indexOf("GL11.glDeleteTextures(textureId);");
        int deleteCatchBlock = deleteBody.indexOf("catch (RuntimeException | Error exception)", glDelete);
        int finallyBlock = deleteBody.indexOf("} finally {", deleteCatchBlock);
        int trackerNotify = deleteBody.indexOf("TextureLifecycleTracker.onDeleteTexture(textureId);", finallyBlock);

        assertTrue(captureDestroyTexture >= 0);
        assertTrue(clearWhiteTexture > captureDestroyTexture);
        assertTrue(destroyDelete > clearWhiteTexture);
        assertTrue(genTexture >= 0);
        assertTrue(failFast > genTexture);
        assertTrue(successFlag > failFast);
        assertTrue(setupFailureLocal > successFlag);
        assertTrue(restoreFailureLocal > setupFailureLocal);
        assertTrue(upload > restoreFailureLocal);
        assertTrue(successSet > upload);
        assertTrue(setupCatchBlock > successSet);
        assertTrue(setupFailureCapture > setupCatchBlock);
        assertTrue(rethrow > setupFailureCapture);
        assertTrue(restoreBinding > rethrow);
        assertTrue(restoreCatchBlock > restoreBinding);
        assertTrue(restoreFailureCapture > restoreCatchBlock);
        assertTrue(rethrowRestore > restoreFailureCapture);
        assertTrue(cleanupGuard > rethrowRestore);
        assertTrue(rollbackDelete > cleanupGuard);
        assertTrue(restoreRawBind >= 0);
        assertTrue(restoreCatch > restoreRawBind);
        assertTrue(restoreGuard > restoreCatch);
        assertTrue(suppress > restoreGuard);
        assertTrue(keepOriginal > suppress);
        assertTrue(failFastRestore > keepOriginal);
        assertTrue(deleteBody.contains("if (textureId <= 0)"));
        assertTrue(glDelete >= 0);
        assertTrue(deleteCatchBlock > glDelete);
        assertTrue(finallyBlock > deleteCatchBlock);
        assertTrue(trackerNotify > finallyBlock);
    }

    @Test
    public void fallbackRestoreSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("setup");

        suppressRestoreFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void fallbackRestoreSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("setup");
        RuntimeException restoreFailure = new RuntimeException("restore");

        suppressRestoreFailure(failure, restoreFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(restoreFailure, failure.getSuppressed()[0]);
    }

    private static void suppressRestoreFailure(Throwable failure, Throwable restoreFailure) throws Exception {
        Method method = FallbackTextures.class.getDeclaredMethod(
            "suppressRestoreFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, restoreFailure);
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
