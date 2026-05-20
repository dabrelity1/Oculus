package net.oculus.texture.pbr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;
import net.oculus.gl.state.StateUpdateNotifiers;
import org.junit.After;
import org.junit.Test;

public class PBRTextureManagerTest {
    @After
    public void tearDown() {
        PBRTextureManager.INSTANCE.clear();
        PBRTextureManager.INSTANCE.resetSamplerUsage();
        StateUpdateNotifiers.normalTextureChangeNotifier.setListener(null);
        StateUpdateNotifiers.specularTextureChangeNotifier.setListener(null);
    }

    @Test
    public void singleColorTextureBytesUseIrisRgbaChannelOrder() {
        assertArrayEquals(
            new byte[] {(byte) 0x7F, (byte) 0x7F, (byte) 0xFF, (byte) 0xFF},
            PBRTextureManager.singleColorBytes(PBRType.NORMAL.getDefaultValue()));
        assertArrayEquals(
            new byte[] {0, 0, 0, 0},
            PBRTextureManager.singleColorBytes(PBRType.SPECULAR.getDefaultValue()));
    }

    @Test
    public void pbrSamplerUsageIsResetAndMarkedByPipelineCompilation() {
        PBRTextureManager.INSTANCE.resetSamplerUsage();
        assertFalse(PBRTextureManager.INSTANCE.hasPbrSampler());

        PBRTextureManager.INSTANCE.markPbrSamplerUsed();

        assertTrue(PBRTextureManager.INSTANCE.hasPbrSampler());
    }

    @Test
    public void pbrSamplerNamesMatch1165LevelSamplerBindings() {
        assertTrue(PBRTextureManager.isPbrSamplerName("normals"));
        assertTrue(PBRTextureManager.isPbrSamplerName("specular"));
        assertFalse(PBRTextureManager.isPbrSamplerName("NORMALS"));
        assertFalse(PBRTextureManager.isPbrSamplerName("Specular"));
        assertFalse(PBRTextureManager.isPbrSamplerName("colortex1"));
        assertFalse(PBRTextureManager.isPbrSamplerName("gtexture1"));
    }

    @Test
    public void pbrTextureChangePublishAttemptsSpecularAfterNormalFailure() {
        RuntimeException normalFailure = new RuntimeException("normal");
        AtomicInteger specularCalls = new AtomicInteger();

        StateUpdateNotifiers.normalTextureChangeNotifier.setListener(() -> {
            throw normalFailure;
        });
        StateUpdateNotifiers.specularTextureChangeNotifier.setListener(specularCalls::incrementAndGet);

        try {
            PBRTextureManager.notifyPBRTexturesChanged();
            fail("Expected normal texture listener failure to be rethrown");
        } catch (RuntimeException thrown) {
            assertSame(normalFailure, thrown);
        }

        assertEquals(1, specularCalls.get());
    }

    @Test
    public void pbrTextureChangePublishSuppressesLaterFailure() {
        RuntimeException normalFailure = new RuntimeException("normal");
        RuntimeException specularFailure = new RuntimeException("specular");

        StateUpdateNotifiers.normalTextureChangeNotifier.setListener(() -> {
            throw normalFailure;
        });
        StateUpdateNotifiers.specularTextureChangeNotifier.setListener(() -> {
            throw specularFailure;
        });

        try {
            PBRTextureManager.notifyPBRTexturesChanged();
            fail("Expected first PBR texture listener failure to be rethrown");
        } catch (RuntimeException thrown) {
            assertSame(normalFailure, thrown);
            assertEquals(1, thrown.getSuppressed().length);
            assertSame(specularFailure, thrown.getSuppressed()[0]);
        }
    }

    @Test
    public void pbrTextureChangePublishIgnoresSameThrowableFromBothNotifiers() {
        RuntimeException sharedFailure = new RuntimeException("shared");

        StateUpdateNotifiers.normalTextureChangeNotifier.setListener(() -> {
            throw sharedFailure;
        });
        StateUpdateNotifiers.specularTextureChangeNotifier.setListener(() -> {
            throw sharedFailure;
        });

        try {
            PBRTextureManager.notifyPBRTexturesChanged();
            fail("Expected shared PBR texture listener failure to be rethrown");
        } catch (RuntimeException thrown) {
            assertSame(sharedFailure, thrown);
            assertEquals(0, thrown.getSuppressed().length);
        }
    }

    @Test
    public void cleanupFailureCollectionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("cleanup");

        Throwable collected = collectCleanupFailure(failure, failure);

        assertSame(failure, collected);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void cleanupFailureCollectionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("cleanup");
        RuntimeException laterFailure = new RuntimeException("later");

        Throwable collected = collectCleanupFailure(failure, laterFailure);

        assertSame(failure, collected);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(laterFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void nonPositiveTextureIdsUseDefaultHolderWithoutCaching() {
        PBRTextureManager.INSTANCE.getOrLoadHolder(0);
        PBRTextureManager.INSTANCE.getOrLoadHolder(-7);

        assertEquals(0, PBRTextureManager.INSTANCE.getLoadedHolderCount());
    }

    @Test
    public void failedPbrLoadClosesAcceptedTexturesBeforeFallingBack() throws Exception {
        String source = source();
        String loadHolderBody = methodBody(source, "private PBRTextureHolder loadHolder(int id)");
        String cleanupBody = methodBody(source, "private void cleanupAfterLoadFailure(Throwable failure)");
        String restoreBody = methodBody(source,
            "private void restorePreviousTextureBinding(int previousTextureBinding, Throwable loadFailure,");

        int tryBlock = loadHolderBody.indexOf("try {");
        int consumerClear = loadHolderBody.indexOf("consumer.clear();", tryBlock);
        int load = loadHolderBody.indexOf("loader.load(texture, resourceManager, consumer);", consumerClear);
        int holder = loadHolderBody.indexOf("loadedHolder = consumer.toHolder();", load);
        int resolvedLog = loadHolderBody.indexOf("OculusRuntimeValidation.logPBRHolderResolved(", holder);
        int clearReferences = loadHolderBody.indexOf("consumer.clearReferences();", resolvedLog);
        int successReturn = loadHolderBody.indexOf("return loadedHolder;", clearReferences);
        int runtimeCatch = loadHolderBody.indexOf("catch (RuntimeException exception)", successReturn);
        int runtimeFailureCapture = loadHolderBody.indexOf("loadFailure = exception;", runtimeCatch);
        int runtimeRecoverable = loadHolderBody.indexOf("recoverableLoadFailure = true;", runtimeFailureCapture);
        int runtimeCleanup = loadHolderBody.indexOf("cleanupAfterLoadFailure(exception);", runtimeRecoverable);
        int fallback = loadHolderBody.indexOf("return defaultHolder;", runtimeCleanup);
        int zipCatch = loadHolderBody.indexOf("catch (ZipError exception)", fallback);
        int zipFailureCapture = loadHolderBody.indexOf("loadFailure = exception;", zipCatch);
        int zipRecoverable = loadHolderBody.indexOf("recoverableLoadFailure = true;", zipFailureCapture);
        int zipCleanup = loadHolderBody.indexOf("cleanupAfterLoadFailure(exception);", zipRecoverable);
        int zipFallback = loadHolderBody.indexOf("return defaultHolder;", zipCleanup);
        int errorCatch = loadHolderBody.indexOf("catch (Error error)", zipFallback);
        int errorFailureCapture = loadHolderBody.indexOf("loadFailure = error;", errorCatch);
        int errorCleanup = loadHolderBody.indexOf("cleanupAfterLoadFailure(error);", errorFailureCapture);
        int rethrow = loadHolderBody.indexOf("throw error;", errorCleanup);
        int restore = loadHolderBody.indexOf(
            "restorePreviousTextureBinding(previousTextureBinding, loadFailure, recoverableLoadFailure);", rethrow);

        assertTrue(tryBlock >= 0);
        assertTrue(consumerClear > tryBlock);
        assertTrue(load > consumerClear);
        assertTrue(holder > load);
        assertTrue(resolvedLog > holder);
        assertTrue(clearReferences > resolvedLog);
        assertTrue(successReturn > clearReferences);
        assertTrue(runtimeCatch > successReturn);
        assertTrue(runtimeFailureCapture > runtimeCatch);
        assertTrue(runtimeRecoverable > runtimeFailureCapture);
        assertTrue(runtimeCleanup > runtimeRecoverable);
        assertTrue(fallback > runtimeCleanup);
        assertTrue(zipCatch > fallback);
        assertTrue(zipFailureCapture > zipCatch);
        assertTrue(zipRecoverable > zipFailureCapture);
        assertTrue(zipCleanup > zipRecoverable);
        assertTrue(zipFallback > zipCleanup);
        assertTrue(errorCatch > zipFallback);
        assertTrue(errorFailureCapture > errorCatch);
        assertTrue(errorCleanup > errorFailureCapture);
        assertTrue(rethrow > errorCleanup);
        assertTrue(restore > rethrow);
        assertTrue("Failed PBR load cleanup must still close accepted companion textures",
            cleanupBody.contains("consumer.closeLoadedTextures();"));
        assertTrue("Cleanup failures must not replace the original load failure",
            cleanupBody.contains("catch (RuntimeException | Error cleanupFailure)")
                && cleanupBody.contains("collectCleanupFailure(failure, cleanupFailure);"));
        assertTrue("Texture binding restore failures must attach to the original load failure",
            restoreBody.contains("collectCleanupFailure(loadFailure, restoreFailure);"));
        assertTrue("Recoverable load failures must not be replaced by texture binding restore failures",
            restoreBody.contains("if (recoverableLoadFailure)")
                && restoreBody.contains("return;"));
        assertTrue("Texture binding restore failures without a load failure must still fail fast",
            restoreBody.contains("throw restoreFailure;"));
    }

    @Test
    public void successfulPbrLoadClosesUncachedHolderWhenTextureBindingRestoreFails() throws Exception {
        String source = source();
        String loadHolderBody = methodBody(source, "private PBRTextureHolder loadHolder(int id)");
        String restoreFailureCleanupBody = methodBody(source,
            "private void closeLoadedHolderAfterRestoreFailure(PBRTextureHolder loadedHolder, Throwable restoreFailure)");

        int loadedHolderDeclaration = loadHolderBody.indexOf("PBRTextureHolder loadedHolder = null;");
        int load = loadHolderBody.indexOf("loader.load(texture, resourceManager, consumer);",
            loadedHolderDeclaration);
        int holderAssignment = loadHolderBody.indexOf("loadedHolder = consumer.toHolder();", load);
        int resolvedLog = loadHolderBody.indexOf("OculusRuntimeValidation.logPBRHolderResolved(",
            holderAssignment);
        int clearReferences = loadHolderBody.indexOf("consumer.clearReferences();", resolvedLog);
        int successReturn = loadHolderBody.indexOf("return loadedHolder;", clearReferences);
        int finallyBlock = loadHolderBody.indexOf("} finally {", successReturn);
        int restoreTry = loadHolderBody.indexOf("try {", finallyBlock);
        int restoreCall = loadHolderBody.indexOf(
            "restorePreviousTextureBinding(previousTextureBinding, loadFailure, recoverableLoadFailure);",
            restoreTry);
        int restoreCatch = loadHolderBody.indexOf("catch (RuntimeException | Error restoreFailure)", restoreCall);
        int closeHolder = loadHolderBody.indexOf(
            "closeLoadedHolderAfterRestoreFailure(loadedHolder, restoreFailure);", restoreCatch);
        int rethrowRestore = loadHolderBody.indexOf("throw restoreFailure;", closeHolder);

        assertTrue(loadedHolderDeclaration >= 0);
        assertTrue(load > loadedHolderDeclaration);
        assertTrue(holderAssignment > load);
        assertTrue(resolvedLog > holderAssignment);
        assertTrue(clearReferences > resolvedLog);
        assertTrue(successReturn > clearReferences);
        assertTrue(finallyBlock > successReturn);
        assertTrue(restoreTry > finallyBlock);
        assertTrue(restoreCall > restoreTry);
        assertTrue(restoreCatch > restoreCall);
        assertTrue(closeHolder > restoreCatch);
        assertTrue(rethrowRestore > closeHolder);
        assertTrue(restoreFailureCleanupBody.contains("loadedHolder == null || loadedHolder == defaultHolder"));
        assertTrue(restoreFailureCleanupBody.contains("closeHolder(loadedHolder);"));
        assertTrue(restoreFailureCleanupBody.contains("catch (RuntimeException | Error cleanupFailure)"));
        assertTrue(restoreFailureCleanupBody.contains("collectCleanupFailure(restoreFailure, cleanupFailure);"));
    }

    @Test
    public void pbrLookupUsesTrackedAbstractTexturesBeforeTextureManagerScan() throws Exception {
        String source = source();
        String findBody = methodBody(source, "private AbstractTexture findTextureById(int id)");

        int tracked = findBody.indexOf("AbstractTexture trackedTexture = TextureTracker.INSTANCE.getTexture(id);");
        int trackedReturn = findBody.indexOf("return trackedTexture;", tracked);
        int textureManager = findBody.indexOf("TextureManager textureManager = getTextureManager();", trackedReturn);
        int textureMap = findBody.indexOf("getTextureObjects(textureManager);", textureManager);

        assertTrue(tracked >= 0);
        assertTrue(trackedReturn > tracked);
        assertTrue(textureManager > trackedReturn);
        assertTrue(textureMap > textureManager);
    }

    @Test
    public void pbrHolderCleanupSkipsDefaultsAndDuplicateTextureObjects() throws Exception {
        String source = source();
        String clearBody = methodBody(source, "public synchronized void clear()");
        String closeHolderBody = methodBody(source, "private void closeHolder(PBRTextureHolder holder)");
        String closeOwnedBody = methodBody(source, "private void closeOwnedPbrTexture(AbstractTexture texture)");
        String closeDefaultBody = methodBody(source, "private void closeDefaultPbrTexture(AbstractTexture texture, String description)");
        String deleteBody = methodBody(source, "private static void deletePbrTexture(AbstractTexture texture, String description)");
        String closeLoadedBody = methodBody(source, "private void closeLoadedTextures()");
        int captureTextureId = deleteBody.indexOf("int textureId = getExistingTextureId(texture);");
        int deleteTexture = deleteBody.indexOf("texture.deleteGlTexture();", captureTextureId);
        int catchBlock = deleteBody.indexOf("catch (RuntimeException | Error exception)", deleteTexture);
        int finallyBlock = deleteBody.indexOf("} finally {", catchBlock);
        int trackerNotify = deleteBody.indexOf("TextureLifecycleTracker.onDeleteTexture(textureId);", finallyBlock);

        assertTrue(clearBody.contains("try {"));
        assertTrue(clearBody.contains("} finally {"));
        assertTrue(clearBody.contains("holders.clear();"));
        assertTrue(clearBody.contains("atlasHolders.clear();"));
        assertTrue(closeHolderBody.contains("closeOwnedPbrTexture(normalTexture);"));
        assertTrue(closeHolderBody.contains("if (specularTexture != normalTexture)"));
        assertTrue(closeHolderBody.contains("closeOwnedPbrTexture(specularTexture);"));
        assertTrue(closeOwnedBody.contains("texture != defaultNormalTexture && texture != defaultSpecularTexture"));
        assertTrue(closeOwnedBody.contains("deletePbrTexture(texture, \"PBR texture\");"));
        assertTrue(closeDefaultBody.contains("deletePbrTexture(texture, description);"));
        assertTrue(captureTextureId >= 0);
        assertTrue(deleteTexture > captureTextureId);
        assertTrue(catchBlock > deleteTexture);
        assertTrue(finallyBlock > catchBlock);
        assertTrue(trackerNotify > finallyBlock);
        assertTrue(closeLoadedBody.contains("closeOwnedPbrTexture(normalTexture);"));
        assertTrue(closeLoadedBody.contains("if (specularTexture != normalTexture)"));
        assertTrue(closeLoadedBody.contains("clearReferences();"));
    }

    @Test
    public void pbrConsumerClearReleasesTextureReferencesWithoutAllocatingDefaults() throws Exception {
        String source = source();
        String clearBody = methodBody(source, "public synchronized void clear()");
        String clearReferencesBody = methodBody(source, "private void clearReferences()");
        String closeLoadedBody = methodBody(source, "private void closeLoadedTextures()");

        int finallyBlock = clearBody.indexOf("} finally {");
        int clearHolders = clearBody.indexOf("holders.clear();", finallyBlock);
        int clearAtlas = clearBody.indexOf("atlasHolders.clear();", clearHolders);
        int clearReferences = clearBody.indexOf("consumer.clearReferences();", clearAtlas);

        assertTrue(finallyBlock >= 0);
        assertTrue(clearHolders > finallyBlock);
        assertTrue(clearAtlas > clearHolders);
        assertTrue(clearReferences > clearAtlas);
        assertTrue(clearReferencesBody.contains("normalTexture = null;"));
        assertTrue(clearReferencesBody.contains("specularTexture = null;"));
        assertFalse(clearReferencesBody.contains("getDefaultNormalTexture()"));
        assertFalse(clearReferencesBody.contains("getDefaultSpecularTexture()"));
        assertTrue(closeLoadedBody.contains("clearReferences();"));
        assertFalse(closeLoadedBody.contains("clear();"));
    }

    @Test
    public void clearReleasesReusableConsumerTextureReferences() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        TestTexture normalTexture = new TestTexture(false);
        TestTexture specularTexture = new TestTexture(false);
        setConsumerField("normalTexture", normalTexture);
        setConsumerField("specularTexture", specularTexture);
        setConsumerField("loadedNormalTexture", true);
        setConsumerField("loadedSpecularTexture", true);
        setConsumerField("changed", true);

        PBRTextureManager.INSTANCE.clear();

        assertNull(getConsumerField("normalTexture"));
        assertNull(getConsumerField("specularTexture"));
        assertFalse((Boolean) getConsumerField("loadedNormalTexture"));
        assertFalse((Boolean) getConsumerField("loadedSpecularTexture"));
        assertFalse((Boolean) getConsumerField("changed"));
    }

    @Test
    public void defaultSingleColorTextureRollsBackGeneratedIdOnConstructorUploadFailure() throws Exception {
        String source = source();
        String constructorBody = methodBody(source, "private SingleColorTexture(int rgba)");
        String uploadBody = methodBody(source, "private void upload()");
        String restoreBody = methodBody(source,
            "private static void restorePreviousTextureBinding(int previousTextureBinding, Throwable setupFailure)");

        int successFlag = constructorBody.indexOf("boolean success = false;");
        int upload = constructorBody.indexOf("upload();", successFlag);
        int successSet = constructorBody.indexOf("success = true;", upload);
        int cleanupGuard = constructorBody.indexOf("if (!success)", successSet);
        int cleanup = constructorBody.indexOf("deletePbrTexture(this, \"default PBR texture\");", cleanupGuard);

        int textureId = uploadBody.indexOf("int textureId = getGlTextureId();");
        int failFast = uploadBody.indexOf(
            "throw new IllegalStateException(\"Failed to allocate default PBR texture\");", textureId);
        int setupFailureLocal = uploadBody.indexOf("Throwable setupFailure = null;", failFast);
        int bind = uploadBody.indexOf("GlStateManager.bindTexture(textureId);", setupFailureLocal);
        int track = uploadBody.indexOf("TextureLifecycleTracker.onTexImage2D(", bind);
        int setupCatch = uploadBody.indexOf("catch (RuntimeException | Error exception)", track);
        int setupFailureCapture = uploadBody.indexOf("setupFailure = exception;", setupCatch);
        int rethrow = uploadBody.indexOf("throw exception;", setupFailureCapture);
        int restore = uploadBody.indexOf("restorePreviousTextureBinding(previousTextureBinding, setupFailure);", rethrow);
        int restoreBind = restoreBody.indexOf("GlStateManager.bindTexture(previousTextureBinding);");
        int restoreCatch = restoreBody.indexOf("catch (RuntimeException | Error restoreFailure)", restoreBind);
        int restoreGuard = restoreBody.indexOf("if (setupFailure != null)", restoreCatch);
        int suppress = restoreBody.indexOf("collectCleanupFailure(setupFailure, restoreFailure);", restoreGuard);
        int keepOriginal = restoreBody.indexOf("return;", suppress);
        int failFastRestore = restoreBody.indexOf("throw restoreFailure;", keepOriginal);

        assertTrue(successFlag >= 0);
        assertTrue(upload > successFlag);
        assertTrue(successSet > upload);
        assertTrue(cleanupGuard > successSet);
        assertTrue(cleanup > cleanupGuard);
        assertTrue(textureId >= 0);
        assertTrue(failFast > textureId);
        assertTrue(setupFailureLocal > failFast);
        assertTrue(bind > setupFailureLocal);
        assertTrue(track > bind);
        assertTrue(setupCatch > track);
        assertTrue(setupFailureCapture > setupCatch);
        assertTrue(rethrow > setupFailureCapture);
        assertTrue(restore > rethrow);
        assertTrue(restoreBind >= 0);
        assertTrue(restoreCatch > restoreBind);
        assertTrue(restoreGuard > restoreCatch);
        assertTrue(suppress > restoreGuard);
        assertTrue(keepOriginal > suppress);
        assertTrue(failFastRestore > keepOriginal);
    }

    @Test
    public void clearContinuesAfterOwnedPbrTextureDeleteFailure() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        TestTexture failingTexture = new TestTexture(true);
        TestTexture laterTexture = new TestTexture(false);
        putHolder(137, holder(failingTexture, laterTexture));

        PBRTextureManager.INSTANCE.clear();

        assertTrue(failingTexture.deleteAttempted);
        assertTrue(laterTexture.deleteAttempted);
        assertEquals(0, PBRTextureManager.INSTANCE.getLoadedHolderCount());
    }

    @Test
    public void closeContinuesAfterDefaultPbrTextureDeleteFailureAndClearsReferences() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        TestTexture failingDefault = new TestTexture(true);
        TestTexture laterDefault = new TestTexture(false);
        setDefaultTexture("defaultNormalTexture", failingDefault);
        setDefaultTexture("defaultSpecularTexture", laterDefault);

        PBRTextureManager.INSTANCE.close();

        assertTrue(failingDefault.deleteAttempted);
        assertTrue(laterDefault.deleteAttempted);
        assertNull(getDefaultTexture("defaultNormalTexture"));
        assertNull(getDefaultTexture("defaultSpecularTexture"));
    }

    @Test
    public void clearContinuesAfterOwnedPbrTextureDeleteError() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        TestTexture failingTexture = new TestTexture(new AssertionError("expected test failure"));
        TestTexture laterTexture = new TestTexture(false);
        putHolder(138, holder(failingTexture, laterTexture));

        PBRTextureManager.INSTANCE.clear();

        assertTrue(failingTexture.deleteAttempted);
        assertTrue(laterTexture.deleteAttempted);
        assertEquals(0, PBRTextureManager.INSTANCE.getLoadedHolderCount());
    }

    @Test
    public void clearRemovesHolderBeforeClosingToTolerateDeleteCallbacks() throws Exception {
        String source = source();
        String clearBody = methodBody(source, "public synchronized void clear()");

        int loop = clearBody.indexOf("while (!holders.isEmpty())");
        int entry = clearBody.indexOf("Map.Entry<Integer, PBRTextureHolder> entry = holders.entrySet().iterator().next();", loop);
        int holder = clearBody.indexOf("PBRTextureHolder holder = entry.getValue();", entry);
        int remove = clearBody.indexOf("holders.remove(entry.getKey());", holder);
        int close = clearBody.indexOf("closeHolder(holder);", remove);
        int finallyBlock = clearBody.indexOf("} finally {", close);

        assertTrue(loop >= 0);
        assertTrue(entry > loop);
        assertTrue(holder > entry);
        assertTrue(remove > holder);
        assertTrue(close > remove);
        assertTrue(finallyBlock > close);
        assertFalse(clearBody.contains("for (PBRTextureHolder holder : holders.values())"));
    }

    @Test
    public void clearContinuesWhenTextureDeleteReentersHolderRemoval() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        TestTexture callbackTexture = new TestTexture(false);
        TestTexture removedByCallback = new TestTexture(false);
        TestTexture laterTexture = new TestTexture(false);
        callbackTexture.onDelete = () -> PBRTextureManager.INSTANCE.onDeleteTexture(2);
        putHolder(1, holder(callbackTexture, callbackTexture));
        putHolder(2, holder(removedByCallback, removedByCallback));
        putHolder(3, holder(laterTexture, laterTexture));

        PBRTextureManager.INSTANCE.clear();

        assertTrue(callbackTexture.deleteAttempted);
        assertTrue(removedByCallback.deleteAttempted);
        assertTrue(laterTexture.deleteAttempted);
        assertEquals(0, PBRTextureManager.INSTANCE.getLoadedHolderCount());
    }

    @Test
    public void clearAttemptsLaterHolderCleanupWhenOneHolderThrows() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        RuntimeException holderFailure = new RuntimeException("expected holder failure");
        TestTexture laterTexture = new TestTexture(false);
        putHolder(1, throwingHolder(holderFailure));
        putHolder(2, holder(laterTexture, laterTexture));

        try {
            PBRTextureManager.INSTANCE.clear();
            fail("Expected holder cleanup failure to be rethrown after cleanup");
        } catch (RuntimeException thrown) {
            assertSame(holderFailure, thrown);
        }

        assertTrue(laterTexture.deleteAttempted);
        assertEquals(0, PBRTextureManager.INSTANCE.getLoadedHolderCount());
    }

    @Test
    public void holderCleanupAttemptsSpecularTextureWhenNormalGetterThrows() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        RuntimeException normalFailure = new RuntimeException("expected normal getter failure");
        TestTexture specularTexture = new TestTexture(false);
        putHolder(1, holderWithThrowingNormal(normalFailure, specularTexture));

        try {
            PBRTextureManager.INSTANCE.clear();
            fail("Expected normal getter failure to be rethrown after specular cleanup");
        } catch (RuntimeException thrown) {
            assertSame(normalFailure, thrown);
        }

        assertTrue(specularTexture.deleteAttempted);
        assertEquals(0, PBRTextureManager.INSTANCE.getLoadedHolderCount());
    }

    @Test
    public void holderCleanupAttemptsNormalTextureWhenSpecularGetterThrows() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        RuntimeException specularFailure = new RuntimeException("expected specular getter failure");
        TestTexture normalTexture = new TestTexture(false);
        putHolder(1, holderWithThrowingSpecular(normalTexture, specularFailure));

        try {
            PBRTextureManager.INSTANCE.clear();
            fail("Expected specular getter failure to be rethrown after normal cleanup");
        } catch (RuntimeException thrown) {
            assertSame(specularFailure, thrown);
        }

        assertTrue(normalTexture.deleteAttempted);
        assertEquals(0, PBRTextureManager.INSTANCE.getLoadedHolderCount());
    }

    @Test
    public void closeAttemptsDefaultTextureCleanupAfterHolderCleanupFailure() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        RuntimeException holderFailure = new RuntimeException("expected holder failure");
        TestTexture defaultNormal = new TestTexture(false);
        TestTexture defaultSpecular = new TestTexture(false);
        putHolder(1, throwingHolder(holderFailure));
        setDefaultTexture("defaultNormalTexture", defaultNormal);
        setDefaultTexture("defaultSpecularTexture", defaultSpecular);

        try {
            PBRTextureManager.INSTANCE.close();
            fail("Expected holder cleanup failure to be rethrown after default cleanup");
        } catch (RuntimeException thrown) {
            assertSame(holderFailure, thrown);
        }

        assertTrue(defaultNormal.deleteAttempted);
        assertTrue(defaultSpecular.deleteAttempted);
        assertNull(getDefaultTexture("defaultNormalTexture"));
        assertNull(getDefaultTexture("defaultSpecularTexture"));
    }

    @Test
    public void clearClosesRegisteredAtlasTexturesWithoutCachedHolder() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        TestAtlasTexture normalAtlas = new TestAtlasTexture(PBRType.NORMAL);
        TestAtlasTexture specularAtlas = new TestAtlasTexture(PBRType.SPECULAR);

        PBRTextureManager.INSTANCE.registerAtlasTexture(null, PBRType.NORMAL, normalAtlas);
        PBRTextureManager.INSTANCE.registerAtlasTexture(null, PBRType.SPECULAR, specularAtlas);

        PBRTextureManager.INSTANCE.clear();

        assertEquals(1, normalAtlas.deleteCalls);
        assertEquals(1, specularAtlas.deleteCalls);
        assertTrue(atlasHolders().isEmpty());
    }

    @Test
    public void clearDoesNotDoubleDeleteSharedRegisteredAtlasTexture() throws Exception {
        PBRTextureManager.INSTANCE.clear();
        TestAtlasTexture sharedAtlas = new TestAtlasTexture(PBRType.NORMAL);

        PBRTextureManager.INSTANCE.registerAtlasTexture(null, PBRType.NORMAL, sharedAtlas);
        PBRTextureManager.INSTANCE.registerAtlasTexture(null, PBRType.SPECULAR, sharedAtlas);

        PBRTextureManager.INSTANCE.clear();

        assertEquals(1, sharedAtlas.deleteCalls);
        assertTrue(atlasHolders().isEmpty());
    }

    @Test
    public void clearClosesRegisteredAtlasTexturesBeforeDroppingAtlasRegistry() throws Exception {
        String source = source();
        String clearBody = methodBody(source, "public synchronized void clear()");
        String closeRegisteredBody = methodBody(source, "private void closeRegisteredAtlasTextures()");

        int finallyBlock = clearBody.indexOf("} finally {");
        int closeRegistered = clearBody.indexOf("closeRegisteredAtlasTextures();", finallyBlock);
        int clearAtlas = clearBody.indexOf("atlasHolders.clear();", closeRegistered);

        assertTrue(finallyBlock >= 0);
        assertTrue(closeRegistered > finallyBlock);
        assertTrue(clearAtlas > closeRegistered);
        assertTrue(closeRegisteredBody.contains("Collections.newSetFromMap(new IdentityHashMap<PBRAtlasTexture, Boolean>())"));
        assertTrue(closeRegisteredBody.contains("atlasHolder.getNormalAtlas()"));
        assertTrue(closeRegisteredBody.contains("atlasHolder.getSpecularAtlas()"));
        assertTrue(closeRegisteredBody.contains("closeOwnedPbrTexture(atlasTexture);"));
    }

    @Test
    public void atlasAnimationUpdateAttemptsSpecularAfterNormalFailure() {
        RuntimeException normalFailure = new RuntimeException("normal atlas");
        TestAtlasTexture normalAtlas = new TestAtlasTexture(PBRType.NORMAL);
        TestAtlasTexture specularAtlas = new TestAtlasTexture(PBRType.SPECULAR);
        normalAtlas.updateFailure = normalFailure;

        PBRAtlasHolder holder = new PBRAtlasHolder();
        holder.setNormalAtlas(normalAtlas);
        holder.setSpecularAtlas(specularAtlas);

        try {
            holder.updateAnimations();
            fail("Expected normal atlas update failure to be rethrown");
        } catch (RuntimeException thrown) {
            assertSame(normalFailure, thrown);
        }

        assertEquals(1, normalAtlas.updateCalls);
        assertEquals(1, specularAtlas.updateCalls);
    }

    @Test
    public void atlasAnimationUpdateSuppressesSpecularFailureAfterNormalFailure() {
        RuntimeException normalFailure = new RuntimeException("normal atlas");
        RuntimeException specularFailure = new RuntimeException("specular atlas");
        TestAtlasTexture normalAtlas = new TestAtlasTexture(PBRType.NORMAL);
        TestAtlasTexture specularAtlas = new TestAtlasTexture(PBRType.SPECULAR);
        normalAtlas.updateFailure = normalFailure;
        specularAtlas.updateFailure = specularFailure;

        PBRAtlasHolder holder = new PBRAtlasHolder();
        holder.setNormalAtlas(normalAtlas);
        holder.setSpecularAtlas(specularAtlas);

        try {
            holder.updateAnimations();
            fail("Expected first atlas update failure to be rethrown");
        } catch (RuntimeException thrown) {
            assertSame(normalFailure, thrown);
            assertEquals(1, thrown.getSuppressed().length);
            assertSame(specularFailure, thrown.getSuppressed()[0]);
        }

        assertEquals(1, normalAtlas.updateCalls);
        assertEquals(1, specularAtlas.updateCalls);
    }

    @Test
    public void atlasAnimationUpdateIgnoresSameThrowableFromBothAtlases() {
        RuntimeException sharedFailure = new RuntimeException("shared atlas");
        TestAtlasTexture normalAtlas = new TestAtlasTexture(PBRType.NORMAL);
        TestAtlasTexture specularAtlas = new TestAtlasTexture(PBRType.SPECULAR);
        normalAtlas.updateFailure = sharedFailure;
        specularAtlas.updateFailure = sharedFailure;

        PBRAtlasHolder holder = new PBRAtlasHolder();
        holder.setNormalAtlas(normalAtlas);
        holder.setSpecularAtlas(specularAtlas);

        try {
            holder.updateAnimations();
            fail("Expected shared atlas update failure to be rethrown");
        } catch (RuntimeException thrown) {
            assertSame(sharedFailure, thrown);
            assertEquals(0, thrown.getSuppressed().length);
        }

        assertEquals(1, normalAtlas.updateCalls);
        assertEquals(1, specularAtlas.updateCalls);
    }

    @Test
    public void atlasAnimationUpdateFanoutIsSourcePinned() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/pbr/PBRAtlasHolder.java")), StandardCharsets.UTF_8);
        String updateBody = methodBody(source, "public void updateAnimations()");
        String updateAtlasBody = methodBody(source,
            "private static Throwable updateAtlasAnimations(Throwable failure, PBRAtlasTexture atlas)");
        String rethrowBody = methodBody(source, "private static void rethrowFailure(Throwable failure)");

        int failureInit = updateBody.indexOf("Throwable failure = null;");
        int normalGuard = updateBody.indexOf("if (normalAtlas != null)", failureInit);
        int normalUpdate = updateBody.indexOf("failure = updateAtlasAnimations(failure, normalAtlas);", normalGuard);
        int specularGuard = updateBody.indexOf("if (specularAtlas != null)", normalUpdate);
        int specularUpdate = updateBody.indexOf("failure = updateAtlasAnimations(failure, specularAtlas);",
            specularGuard);
        int rethrow = updateBody.indexOf("rethrowFailure(failure);", specularUpdate);
        int atlasUpdate = updateAtlasBody.indexOf("atlas.updateAnimations();");
        int catchBlock = updateAtlasBody.indexOf("catch (RuntimeException | Error exception)", atlasUpdate);
        int collect = updateAtlasBody.indexOf("return collectFailure(failure, exception);", catchBlock);
        String collectBody = methodBody(source,
            "private static Throwable collectFailure(Throwable failure, Throwable exception)");

        assertTrue(failureInit >= 0);
        assertTrue(normalGuard > failureInit);
        assertTrue(normalUpdate > normalGuard);
        assertTrue(specularGuard > normalUpdate);
        assertTrue(specularUpdate > specularGuard);
        assertTrue(rethrow > specularUpdate);
        assertTrue(atlasUpdate >= 0);
        assertTrue(catchBlock > atlasUpdate);
        assertTrue(collect > catchBlock);
        assertTrue(collectBody.contains("if (exception != failure)"));
        assertTrue(collectBody.contains("failure.addSuppressed(exception);"));
        assertTrue(rethrowBody.contains("throw (RuntimeException) failure;"));
        assertTrue(rethrowBody.contains("throw (Error) failure;"));
    }

    private static String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/pbr/PBRTextureManager.java")), StandardCharsets.UTF_8);
    }

    private static Throwable collectCleanupFailure(Throwable failure, Throwable exception) throws Exception {
        Method method = PBRTextureManager.class.getDeclaredMethod(
            "collectCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(null, failure, exception);
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

    private static PBRTextureHolder holder(final AbstractTexture normalTexture, final AbstractTexture specularTexture) {
        return new PBRTextureHolder() {
            @Override
            public AbstractTexture getNormalTexture() {
                return normalTexture;
            }

            @Override
            public AbstractTexture getSpecularTexture() {
                return specularTexture;
            }
        };
    }

    private static PBRTextureHolder throwingHolder(final RuntimeException failure) {
        return new PBRTextureHolder() {
            @Override
            public AbstractTexture getNormalTexture() {
                throw failure;
            }

            @Override
            public AbstractTexture getSpecularTexture() {
                throw failure;
            }
        };
    }

    private static PBRTextureHolder holderWithThrowingNormal(final RuntimeException normalFailure,
                                                            final AbstractTexture specularTexture) {
        return new PBRTextureHolder() {
            @Override
            public AbstractTexture getNormalTexture() {
                throw normalFailure;
            }

            @Override
            public AbstractTexture getSpecularTexture() {
                return specularTexture;
            }
        };
    }

    private static PBRTextureHolder holderWithThrowingSpecular(final AbstractTexture normalTexture,
                                                              final RuntimeException specularFailure) {
        return new PBRTextureHolder() {
            @Override
            public AbstractTexture getNormalTexture() {
                return normalTexture;
            }

            @Override
            public AbstractTexture getSpecularTexture() {
                throw specularFailure;
            }
        };
    }

    private static void putHolder(int id, PBRTextureHolder holder) throws Exception {
        holders().put(id, holder);
    }

    private static void setDefaultTexture(String fieldName, AbstractTexture texture) throws Exception {
        Field field = PBRTextureManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(PBRTextureManager.INSTANCE, texture);
    }

    private static AbstractTexture getDefaultTexture(String fieldName) throws Exception {
        Field field = PBRTextureManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AbstractTexture) field.get(PBRTextureManager.INSTANCE);
    }

    private static void setConsumerField(String fieldName, Object value) throws Exception {
        Field field = consumer().getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(consumer(), value);
    }

    private static Object getConsumerField(String fieldName) throws Exception {
        Field field = consumer().getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(consumer());
    }

    private static Object consumer() throws Exception {
        Field field = PBRTextureManager.class.getDeclaredField("consumer");
        field.setAccessible(true);
        return field.get(PBRTextureManager.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, PBRTextureHolder> holders() throws Exception {
        Field field = PBRTextureManager.class.getDeclaredField("holders");
        field.setAccessible(true);
        return (Map<Integer, PBRTextureHolder>) field.get(PBRTextureManager.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, PBRAtlasHolder> atlasHolders() throws Exception {
        Field field = PBRTextureManager.class.getDeclaredField("atlasHolders");
        field.setAccessible(true);
        return (Map<?, PBRAtlasHolder>) field.get(PBRTextureManager.INSTANCE);
    }

    private static final class TestTexture extends AbstractTexture {
        private final Throwable failureOnDelete;
        private Runnable onDelete;
        private boolean deleteAttempted;

        private TestTexture(boolean failOnDelete) {
            this(failOnDelete ? new RuntimeException("expected test failure") : null);
        }

        private TestTexture(Throwable failureOnDelete) {
            this.failureOnDelete = failureOnDelete;
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) {
        }

        @Override
        public void deleteGlTexture() {
            deleteAttempted = true;
            if (onDelete != null) {
                onDelete.run();
            }
            if (failureOnDelete instanceof RuntimeException) {
                throw (RuntimeException) failureOnDelete;
            }
            if (failureOnDelete instanceof Error) {
                throw (Error) failureOnDelete;
            }
        }
    }

    private static final class TestAtlasTexture extends PBRAtlasTexture {
        private int deleteCalls;
        private int updateCalls;
        private RuntimeException updateFailure;

        private TestAtlasTexture(PBRType type) {
            super(null, type);
        }

        @Override
        public void updateAnimations() {
            updateCalls++;
            if (updateFailure != null) {
                throw updateFailure;
            }
        }

        @Override
        public void deleteGlTexture() {
            deleteCalls++;
        }
    }
}
