package net.oculus.texture.pbr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import net.oculus.texture.TextureInfoCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lwjgl.opengl.GL11;

public class PBRAtlasTextureTest {
    @After
    @Before
    public void clearTextureInfoCache() {
        TextureInfoCache.INSTANCE.onDeleteTexture(73);
    }

    @Test
    public void atlasDefaultFillConvertsIrisRgbaToMinecraftArgb() {
        assertEquals(0xFF7F7FFF, PBRAtlasTexture.defaultArgb(PBRType.NORMAL));
        assertEquals(0x00000000, PBRAtlasTexture.defaultArgb(PBRType.SPECULAR));
    }

    @Test
    public void atlasAllocationTracksTextureInfoForSizeUniforms() {
        PBRAtlasTexture.trackAtlasAllocation(73, 128, 64);

        TextureInfoCache.TextureInfo info = TextureInfoCache.INSTANCE.getInfo(73);
        assertEquals(GL11.GL_RGBA, info.getInternalFormat());
        assertEquals(128, info.getWidth());
        assertEquals(64, info.getHeight());
    }

    @Test
    public void failedAtlasUploadReturnsFalseEvenWhenCleanupThrows() {
        FailingAtlasTexture texture = new FailingAtlasTexture();
        texture.deleteFailure = new IllegalStateException("delete failed");

        assertFalse(texture.tryUpload(16, 16, 0));

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void failedAtlasUploadReturnsFalseEvenWhenCleanupThrowsError() {
        FailingAtlasTexture texture = new FailingAtlasTexture();
        texture.deleteFailure = new AssertionError("delete failed");

        assertFalse(texture.tryUpload(16, 16, 0));

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void atlasDeleteClearsLifecycleTrackerEvenWhenSuperDeleteThrows() throws Exception {
        String source = new String(Files.readAllBytes(
            Paths.get("src/main/java/net/oculus/texture/pbr/PBRAtlasTexture.java")), StandardCharsets.UTF_8);
        String deleteBody = methodBody(source, "public void deleteGlTexture()");

        int captureTextureId = deleteBody.indexOf("int textureId = this.glTextureId;");
        int failureLocal = deleteBody.indexOf("Throwable failure = null;", captureTextureId);
        int unregister = deleteBody.indexOf("PBRTextureManager.INSTANCE.unregisterAtlasTexture", failureLocal);
        int unregisterCatch = deleteBody.indexOf("catch (RuntimeException | Error exception)", unregister);
        int unregisterCollect = deleteBody.indexOf("failure = collectFailure(failure, exception);", unregisterCatch);
        int superDelete = deleteBody.indexOf("super.deleteGlTexture();", unregisterCollect);
        int superCatch = deleteBody.indexOf("catch (RuntimeException | Error exception)", superDelete);
        int superCollect = deleteBody.indexOf("failure = collectFailure(failure, exception);", superCatch);
        int finallyBlock = deleteBody.indexOf("finally", superDelete);
        int trackerNotify = deleteBody.indexOf("TextureLifecycleTracker.onDeleteTexture(textureId);", finallyBlock);
        int rethrow = deleteBody.indexOf("rethrowFailure(failure);", trackerNotify);

        assertTrue(captureTextureId >= 0);
        assertTrue(failureLocal > captureTextureId);
        assertTrue(unregister > failureLocal);
        assertTrue(unregisterCatch > unregister);
        assertTrue(unregisterCollect > unregisterCatch);
        assertTrue(superDelete > unregisterCollect);
        assertTrue(superCatch > superDelete);
        assertTrue(superCollect > superCatch);
        assertTrue(finallyBlock > superDelete);
        assertTrue(trackerNotify > finallyBlock);
        assertTrue(rethrow > trackerNotify);
    }

    @Test
    public void atlasUploadAndAnimationRestoreFailuresKeepOriginalFailure() throws Exception {
        String source = new String(Files.readAllBytes(
            Paths.get("src/main/java/net/oculus/texture/pbr/PBRAtlasTexture.java")), StandardCharsets.UTF_8);
        String uploadBody = methodBody(source, "public void upload(int atlasWidth, int atlasHeight, int mipLevel)");
        String updateBody = methodBody(source, "public void updateAnimations()");
        String restoreBody = methodBody(source,
            "private static void restorePreviousTextureBinding(int previousTextureBinding, Throwable primaryFailure)");

        int uploadFailureLocal = uploadBody.indexOf("Throwable uploadFailure = null;");
        int uploadCatch = uploadBody.indexOf("catch (RuntimeException | Error exception)", uploadFailureLocal);
        int uploadFailureCapture = uploadBody.indexOf("uploadFailure = exception;", uploadCatch);
        int uploadRethrow = uploadBody.indexOf("throw exception;", uploadFailureCapture);
        int uploadRestore = uploadBody.indexOf(
            "restorePreviousTextureBinding(previousTextureBinding, uploadFailure);", uploadRethrow);
        int registerAtlas = uploadBody.indexOf("PBRTextureManager.INSTANCE.registerAtlasTexture", uploadRestore);

        int updateFailureLocal = updateBody.indexOf("Throwable updateFailure = null;");
        int spriteUpdate = updateBody.indexOf("updateFailure = updateSpriteAnimation(updateFailure, sprite);",
            updateFailureLocal);
        int updateRethrowHelper = updateBody.indexOf("rethrowFailure(updateFailure);", spriteUpdate);
        int updateCatch = updateBody.indexOf("catch (RuntimeException | Error exception)", updateRethrowHelper);
        int updateFailureCapture = updateBody.indexOf("updateFailure = exception;", updateCatch);
        int updateRethrow = updateBody.indexOf("throw exception;", updateFailureCapture);
        int updateRestore = updateBody.indexOf(
            "restorePreviousTextureBinding(previousTextureBinding, updateFailure);", updateRethrow);

        int bind = restoreBody.indexOf("GlStateManager.bindTexture(previousTextureBinding);");
        int catchBlock = restoreBody.indexOf("catch (RuntimeException | Error restoreFailure)", bind);
        int primaryGuard = restoreBody.indexOf("if (primaryFailure != null)", catchBlock);
        int suppress = restoreBody.indexOf("collectFailure(primaryFailure, restoreFailure);", primaryGuard);
        int keepOriginal = restoreBody.indexOf("return;", suppress);
        int failFast = restoreBody.indexOf("throw restoreFailure;", keepOriginal);

        assertTrue(uploadFailureLocal >= 0);
        assertTrue(uploadCatch > uploadFailureLocal);
        assertTrue(uploadFailureCapture > uploadCatch);
        assertTrue(uploadRethrow > uploadFailureCapture);
        assertTrue(uploadRestore > uploadRethrow);
        assertTrue(registerAtlas > uploadRestore);

        assertTrue(updateFailureLocal >= 0);
        assertTrue(spriteUpdate > updateFailureLocal);
        assertTrue(updateRethrowHelper > spriteUpdate);
        assertTrue(updateCatch > updateRethrowHelper);
        assertTrue(updateFailureCapture > updateCatch);
        assertTrue(updateRethrow > updateFailureCapture);
        assertTrue(updateRestore > updateRethrow);

        assertTrue(bind >= 0);
        assertTrue(catchBlock > bind);
        assertTrue(primaryGuard > catchBlock);
        assertTrue(suppress > primaryGuard);
        assertTrue(keepOriginal > suppress);
        assertTrue(failFast > keepOriginal);
    }

    @Test
    public void atlasFailureCollectionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("atlas");

        Throwable collected = collectFailure(failure, failure);

        assertSame(failure, collected);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void atlasFailureCollectionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("atlas");
        RuntimeException laterFailure = new RuntimeException("later");

        Throwable collected = collectFailure(failure, laterFailure);

        assertSame(failure, collected);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(laterFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void atlasAnimationAttemptsEverySpriteBeforeRethrowing() throws Exception {
        String source = new String(Files.readAllBytes(
            Paths.get("src/main/java/net/oculus/texture/pbr/PBRAtlasTexture.java")), StandardCharsets.UTF_8);
        String updateBody = methodBody(source, "public void updateAnimations()");
        String updateSpriteBody = methodBody(source,
            "private static Throwable updateSpriteAnimation(Throwable failure, PBRAtlasSprite sprite)");
        String collectBody = methodBody(source,
            "private static Throwable collectFailure(Throwable failure, Throwable exception)");
        String rethrowBody = methodBody(source, "private static void rethrowFailure(Throwable failure)");

        int bind = updateBody.indexOf("GlStateManager.bindTexture(getGlTextureId());");
        int loop = updateBody.indexOf("for (PBRAtlasSprite sprite : animatedSprites)", bind);
        int updateSprite = updateBody.indexOf("updateFailure = updateSpriteAnimation(updateFailure, sprite);", loop);
        int rethrow = updateBody.indexOf("rethrowFailure(updateFailure);", updateSprite);
        int catchBlock = updateBody.indexOf("catch (RuntimeException | Error exception)", rethrow);
        int restore = updateBody.indexOf("restorePreviousTextureBinding(previousTextureBinding, updateFailure);",
            catchBlock);
        int spriteCall = updateSpriteBody.indexOf("sprite.updateAnimation();");
        int spriteCatch = updateSpriteBody.indexOf("catch (RuntimeException | Error exception)", spriteCall);
        int collect = updateSpriteBody.indexOf("return collectFailure(failure, exception);", spriteCatch);

        assertTrue(bind >= 0);
        assertTrue(loop > bind);
        assertTrue(updateSprite > loop);
        assertTrue(rethrow > updateSprite);
        assertTrue(catchBlock > rethrow);
        assertTrue(restore > catchBlock);
        assertTrue(spriteCall >= 0);
        assertTrue(spriteCatch > spriteCall);
        assertTrue(collect > spriteCatch);
        assertTrue(collectBody.contains("if (exception != failure)"));
        assertTrue(collectBody.contains("failure.addSuppressed(exception);"));
        assertTrue(rethrowBody.contains("throw (RuntimeException) failure;"));
        assertTrue(rethrowBody.contains("throw (Error) failure;"));
    }

    private static Throwable collectFailure(Throwable failure, Throwable exception) throws Exception {
        Method method = PBRAtlasTexture.class.getDeclaredMethod("collectFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(null, failure, exception);
    }

    private static String methodBody(String source, String methodSignature) {
        int signature = source.indexOf(methodSignature);
        assertTrue("Missing method " + methodSignature, signature >= 0);

        int openingBrace = source.indexOf('{', signature);
        assertTrue("Missing method opening brace for " + methodSignature, openingBrace >= 0);

        int depth = 0;
        for (int i = openingBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openingBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Missing method closing brace for " + methodSignature);
    }

    private static final class FailingAtlasTexture extends PBRAtlasTexture {
        private Throwable deleteFailure;
        private int deleteCalls;

        private FailingAtlasTexture() {
            super(null, PBRType.NORMAL);
        }

        @Override
        public void upload(int atlasWidth, int atlasHeight, int mipLevel) {
            throw new IllegalStateException("upload failed");
        }

        @Override
        public void deleteGlTexture() {
            deleteCalls++;
            if (deleteFailure instanceof RuntimeException) {
                throw (RuntimeException) deleteFailure;
            }
            if (deleteFailure instanceof Error) {
                throw (Error) deleteFailure;
            }
        }
    }
}
