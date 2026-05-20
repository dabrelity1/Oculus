package net.oculus.texture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class TextureInfoCacheTest {
    @After
    public void clearCache() {
        TextureInfoCache.INSTANCE.clear();
    }

    @Test
    public void levelZeroUploadStoresTextureDimensionsAndInternalFormat() {
        TextureInfoCache.INSTANCE.onTexImage2D(42, GL11.GL_RGBA8, 16, 32);

        TextureInfoCache.TextureInfo info = TextureInfoCache.INSTANCE.getInfo(42);
        assertEquals(42, info.getId());
        assertEquals(GL11.GL_RGBA8, info.getInternalFormat());
        assertEquals(16, info.getWidth());
        assertEquals(32, info.getHeight());
    }

    @Test
    public void directGl11UploadBridgeStoresTextureDimensionsAndInternalFormat() {
        TextureLifecycleTracker.onTexImage2D(42, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 32);

        TextureInfoCache.TextureInfo info = TextureInfoCache.INSTANCE.getInfo(42);
        assertEquals(42, info.getId());
        assertEquals(GL11.GL_RGBA8, info.getInternalFormat());
        assertEquals(16, info.getWidth());
        assertEquals(32, info.getHeight());
    }

    @Test
    public void direct3DUploadBridgeStoresTextureDimensionsInternalFormatAndDepth() {
        TextureLifecycleTracker.onTexImage3D(42, GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, 16, 32, 8);

        TextureInfoCache.TextureInfo info = TextureInfoCache.INSTANCE.getInfo(42);
        assertEquals(42, info.getId());
        assertEquals(GL11.GL_RGBA8, info.getInternalFormat());
        assertEquals(16, info.getWidth());
        assertEquals(32, info.getHeight());
        assertEquals(8, info.getDepth());
    }

    @Test
    public void nonBaseMip2DUploadDoesNotOverwriteTextureSizeMetadata() {
        TextureLifecycleTracker.onTexImage2D(42, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 32);
        TextureLifecycleTracker.onTexImage2D(42, GL11.GL_TEXTURE_2D, 1, GL11.GL_RGBA8, 8, 16);

        TextureInfoCache.TextureInfo info = TextureInfoCache.INSTANCE.getInfo(42);
        assertEquals(GL11.GL_RGBA8, info.getInternalFormat());
        assertEquals(16, info.getWidth());
        assertEquals(32, info.getHeight());
    }

    @Test
    public void nonBaseMip3DUploadDoesNotOverwriteTextureSizeMetadata() {
        TextureLifecycleTracker.onTexImage3D(42, GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, 16, 32, 8);
        TextureLifecycleTracker.onTexImage3D(42, GL12.GL_TEXTURE_3D, 1, GL11.GL_RGBA8, 8, 16, 4);

        TextureInfoCache.TextureInfo info = TextureInfoCache.INSTANCE.getInfo(42);
        assertEquals(GL11.GL_RGBA8, info.getInternalFormat());
        assertEquals(16, info.getWidth());
        assertEquals(32, info.getHeight());
        assertEquals(8, info.getDepth());
    }

    @Test
    public void glStateManagerUploadBridgeIgnoresNon2DTargetsWithoutQueryingGlState() {
        TextureLifecycleTracker.onTexImage2D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, 16, 32, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);

        assertFalse(TextureInfoCache.INSTANCE.hasCachedInfo(0));
    }

    @Test
    public void textureDeletionClearsCachedInfo() {
        TextureInfoCache.INSTANCE.onTexImage2D(42, GL11.GL_RGBA8, 16, 32);
        assertTrue(TextureInfoCache.INSTANCE.hasCachedInfo(42));

        TextureLifecycleTracker.onDeleteTexture(42);

        assertFalse(TextureInfoCache.INSTANCE.hasCachedInfo(42));
    }

    @Test
    public void liveLevelQueryRestoreFailureKeepsOriginalQueryFailure() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/TextureInfoCache.java")), StandardCharsets.UTF_8);
        String fetchBody = methodBody(source, "private int fetchLevelParameter(int parameterName)");
        String restoreBody = methodBody(source,
            "private static void restorePreviousTextureBinding(int bindingTarget, int previousTextureBinding,");

        int queryFailureLocal = fetchBody.indexOf("Throwable queryFailure = null;");
        int bindTexture = fetchBody.indexOf("bindTexture(bindingTarget, id);", queryFailureLocal);
        int levelQuery = fetchBody.indexOf("GL11.glGetTexLevelParameteri", bindTexture);
        int catchBlock = fetchBody.indexOf("catch (RuntimeException | Error exception)", levelQuery);
        int captureFailure = fetchBody.indexOf("queryFailure = exception;", catchBlock);
        int rethrow = fetchBody.indexOf("throw exception;", captureFailure);
        int restore = fetchBody.indexOf("restorePreviousTextureBinding(bindingTarget, previousTextureBinding, queryFailure);", rethrow);

        int restoreBind = restoreBody.indexOf("bindTexture(bindingTarget, previousTextureBinding);");
        int restoreCatch = restoreBody.indexOf("catch (RuntimeException | Error restoreFailure)", restoreBind);
        int failureGuard = restoreBody.indexOf("if (queryFailure != null)", restoreCatch);
        int suppress = restoreBody.indexOf("suppressRestoreFailure(queryFailure, restoreFailure);", failureGuard);
        int keepOriginal = restoreBody.indexOf("return;", suppress);
        int failFast = restoreBody.indexOf("throw restoreFailure;", keepOriginal);

        assertTrue(queryFailureLocal >= 0);
        assertTrue(bindTexture > queryFailureLocal);
        assertTrue(levelQuery > bindTexture);
        assertTrue(catchBlock > levelQuery);
        assertTrue(captureFailure > catchBlock);
        assertTrue(rethrow > captureFailure);
        assertTrue(restore > rethrow);

        assertTrue(restoreBind >= 0);
        assertTrue(restoreCatch > restoreBind);
        assertTrue(failureGuard > restoreCatch);
        assertTrue(suppress > failureGuard);
        assertTrue(keepOriginal > suppress);
        assertTrue(failFast > keepOriginal);
    }

    @Test
    public void liveLevelQuerySuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("query");

        suppressRestoreFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void liveLevelQuerySuppressionKeepsDistinctRestoreFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("query");
        RuntimeException restoreFailure = new RuntimeException("restore");

        suppressRestoreFailure(failure, restoreFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(restoreFailure, failure.getSuppressed()[0]);
    }

    private static void suppressRestoreFailure(Throwable failure, Throwable restoreFailure) throws Exception {
        Method method = TextureInfoCache.TextureInfo.class.getDeclaredMethod(
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

        throw new AssertionError("Could not parse method body for " + signature);
    }
}
