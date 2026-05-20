package net.oculus.pipeline.texture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.gl.texture.PixelFormat;
import net.oculus.gl.texture.PixelType;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.OptionType;
import net.oculus.shaderpack.option.StringOption;
import net.oculus.shaderpack.option.values.OptionValues;
import net.oculus.shaderpack.texture.CustomImageData;
import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class CustomImageManagerTest {
    @Test
    public void bytesPerPixelUsesComponentCountForScalarTypes() {
        assertEquals(1, CustomImageManager.bytesPerPixel(PixelFormat.RED, PixelType.UNSIGNED_BYTE));
        assertEquals(2, CustomImageManager.bytesPerPixel(PixelFormat.RG, PixelType.UNSIGNED_BYTE));
        assertEquals(12, CustomImageManager.bytesPerPixel(PixelFormat.RGB, PixelType.FLOAT));
        assertEquals(16, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_INT));
    }

    @Test
    public void bytesPerPixelUsesPackedTypeWidthForPackedTypes() {
        assertEquals(1, CustomImageManager.bytesPerPixel(PixelFormat.RGB, PixelType.UNSIGNED_BYTE_3_3_2));
        assertEquals(1, CustomImageManager.bytesPerPixel(PixelFormat.RGB, PixelType.UNSIGNED_BYTE_2_3_3_REV));

        assertEquals(2, CustomImageManager.bytesPerPixel(PixelFormat.RGB, PixelType.UNSIGNED_SHORT_5_6_5));
        assertEquals(2, CustomImageManager.bytesPerPixel(PixelFormat.RGB, PixelType.UNSIGNED_SHORT_5_6_5_REV));
        assertEquals(2, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_SHORT_4_4_4_4));
        assertEquals(2, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_SHORT_4_4_4_4_REV));
        assertEquals(2, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_SHORT_5_5_5_1));
        assertEquals(2, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_SHORT_1_5_5_5_REV));

        assertEquals(4, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_INT_8_8_8_8));
        assertEquals(4, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_INT_8_8_8_8_REV));
        assertEquals(4, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_INT_10_10_10_2));
        assertEquals(4, CustomImageManager.bytesPerPixel(PixelFormat.RGBA, PixelType.UNSIGNED_INT_2_10_10_10_REV));
    }

    @Test
    public void dimensionExpressionsResolveLiteralRelativeAndOptionValues() {
        CustomImageManager manager = new CustomImageManager(null, optionValues(
            "COLORED_LIGHTING", "128", "64 128 256 512", "256"));

        assertEquals(Integer.valueOf(384), manager.resolveDimensionForTesting("384", false, 1920));
        assertEquals(Integer.valueOf(960), manager.resolveDimensionForTesting("0.5", true, 1920));
        assertEquals(Integer.valueOf(256), manager.resolveDimensionForTesting("COLORED_LIGHTING", false, 1920));
        assertEquals(Integer.valueOf(256), manager.resolveAbsoluteDimensionForTesting("COLORED_LIGHTING"));
    }

    @Test
    public void unresolvedDimensionExpressionsFailClearly() {
        CustomImageManager manager = new CustomImageManager(null, null);

        try {
            manager.resolveDimensionForTesting("MISSING_OPTION", false, 1920);
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Unable to resolve custom image dimension"));
            assertTrue(exception.getMessage().contains("MISSING_OPTION"));
            return;
        }

        throw new AssertionError("Expected unresolved custom image dimensions to fail");
    }

    @Test
    public void unresolvedDepthExpressionsFailClearly() {
        CustomImageManager manager = new CustomImageManager(null, null);

        try {
            manager.resolveAbsoluteDimensionForTesting("MISSING_DEPTH_OPTION");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Unable to resolve custom image depth"));
            assertTrue(exception.getMessage().contains("MISSING_DEPTH_OPTION"));
            return;
        }

        throw new AssertionError("Expected unresolved custom image depth to fail");
    }

    @Test
    public void complementaryStyleImageDirectiveKeepsSamplerAndOptionDimensions() {
        ShaderProperties properties = new ShaderProperties(
            "image.wsr_img=wsr_sampler red_integer r16ui unsigned_int true false COLORED_LIGHTING 64 COLORED_LIGHTING\n");

        CustomImageData image = properties.getCustomImages().get("wsr_img");

        assertEquals("wsr_sampler", image.getSamplerName());
        assertEquals(PixelFormat.RED_INTEGER, image.getPixelFormat());
        assertEquals("COLORED_LIGHTING", image.getWidthExpression());
        assertEquals("64", image.getHeightExpression());
        assertEquals("COLORED_LIGHTING", image.getDepthExpression());
    }

    @Test
    public void twoDimensionalImageDirectiveKeepsDepthUnset() {
        ShaderProperties properties = new ShaderProperties(
            "image.puddle_img=puddle_sampler red_integer r8ui unsigned_int true false 128 128\n");

        CustomImageData image = properties.getCustomImages().get("puddle_img");

        assertEquals("puddle_sampler", image.getSamplerName());
        assertFalse(image.isThreeDimensional());
    }

    @Test
    public void customImageAllocationIsNotGatedByImageUnitLimit() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String initializeBody = methodBody(source, "public void initializeOrResize(int width, int height)");

        assertFalse(source.contains("import net.oculus.gl.image.ImageLimits;"));
        assertFalse(source.contains("maxImageUnitsSupplier"));
        assertFalse(initializeBody.contains("getMaxImageUnits()"));
        assertTrue(initializeBody.contains("CustomImageTexture texture = allocateTexture(data, dimensions);"));
    }

    @Test
    public void applyToProgramUsesReferenceLayeredImageBindingPath() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String applyBody = methodBody(source, "public void applyToProgram(ProgramBuilder builder)");

        assertTrue(applyBody.contains(
            "builder.addTextureImage(() -> getTextureId(data.getImageName()), data.getInternalFormat(),"));
        assertTrue(applyBody.contains("data.getImageName());"));
        assertFalse(applyBody.contains("data.isThreeDimensional()"));
    }

    @Test
    public void applyToProgramAlwaysOffersPairedSamplerEvenWhenImageUniformIsInactive() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String applyBody = methodBody(source, "public void applyToProgram(ProgramBuilder builder)");

        int imageGuard = applyBody.indexOf("if (builder.hasImage(data.getImageName()))");
        int addImage = applyBody.indexOf("builder.addTextureImage(() -> getTextureId(data.getImageName()),",
            imageGuard);
        int imageGuardClose = applyBody.indexOf("}", addImage);
        int samplerOverride = applyBody.indexOf("builder.overrideSamplerBinding(data.getSamplerName(), texture.binding);",
            imageGuardClose);

        assertTrue("Custom image image-uniform binding must stay guarded by active image discovery",
            imageGuard >= 0 && addImage > imageGuard);
        assertTrue("Custom image paired sampler binding must be outside the image-uniform guard",
            samplerOverride > imageGuardClose);
    }

    @Test
    public void threeDimensionalImageAllocationPublishesTextureMetadata() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String allocateBody = methodBody(source,
            "private CustomImageTexture allocateTexture(CustomImageData data, Dimensions dimensions)");

        int texImage3D = allocateBody.indexOf("GL12.glTexImage3D(");
        int trackerNotify = allocateBody.indexOf("TextureLifecycleTracker.onTexImage3D(textureId, target, 0,",
            texImage3D);
        int twoDimensionalBranch = allocateBody.indexOf("} else {", trackerNotify);
        int texImage2D = allocateBody.indexOf("GL11.glTexImage2D(", twoDimensionalBranch);
        int trackerNotify2D = allocateBody.indexOf("TextureLifecycleTracker.onTexImage2D(textureId, target, 0,",
            texImage2D);

        assertTrue("3D custom image allocations must publish texture metadata for reload/texture-size state",
            trackerNotify > texImage3D);
        assertTrue("2D custom image metadata publication must remain in the 2D allocation branch",
            trackerNotify2D > texImage2D);
    }

    @Test
    public void destroyTexturesUnregistersOnlyOwnedCustomImageSamplerBindings() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String destroyBody = methodBody(source, "private void destroyTextures()");
        String destroyMapBody = methodBody(source,
            "private void destroyTextureMap(Map<String, CustomImageTexture> ownedTextures, boolean unregisterSamplers)");
        String deleteBody = methodBody(source, "private static void deleteTexture(int textureId, String imageName)");

        int tryBlock = destroyBody.indexOf("try {");
        int destroyMap = destroyBody.indexOf("destroyTextureMap(textures, true);", tryBlock);
        int finallyBlock = destroyBody.indexOf("} finally {", destroyMap);
        int clear = destroyBody.indexOf("textures.clear();", finallyBlock);
        int failureInit = destroyMapBody.indexOf("Throwable failure = null;");
        int mapTryBlock = destroyMapBody.indexOf("try {", failureInit);
        int loop = destroyMapBody.indexOf("for (CustomImageTexture texture : ownedTextures.values())", mapTryBlock);
        int unregisterGuard = destroyMapBody.indexOf("if (unregisterSamplers)");
        int unregisterTry = destroyMapBody.indexOf("try {", unregisterGuard);
        int unregister = destroyMapBody.indexOf(
            "TextureBindingRegistry.unregister(texture.data.getSamplerName(), texture.binding);", unregisterTry);
        int unregisterCatch = destroyMapBody.indexOf("catch (RuntimeException | Error exception)", unregister);
        int collectFailure = destroyMapBody.indexOf("failure = collectFailure(failure, exception);", unregisterCatch);
        int deleteTexture = destroyMapBody.indexOf("deleteTexture(texture.textureId, texture.data.getImageName());",
            collectFailure);
        int mapFinally = destroyMapBody.indexOf("} finally {", deleteTexture);
        int ownedClear = destroyMapBody.indexOf("ownedTextures.clear();", mapFinally);
        int rethrowFailure = destroyMapBody.indexOf("rethrowFailure(failure);", ownedClear);
        int glDelete = deleteBody.indexOf("GL11.glDeleteTextures(textureId);");
        int catchBlock = deleteBody.indexOf("catch (RuntimeException | Error exception)", glDelete);
        int deleteFinally = deleteBody.indexOf("} finally {", catchBlock);
        int trackerNotify = deleteBody.indexOf("TextureLifecycleTracker.onDeleteTexture(textureId);", deleteFinally);

        assertTrue(tryBlock >= 0);
        assertTrue(destroyMap > tryBlock);
        assertTrue(finallyBlock > destroyMap);
        assertTrue(clear > finallyBlock);
        assertTrue(failureInit >= 0);
        assertTrue(mapTryBlock > failureInit);
        assertTrue(loop > mapTryBlock);
        assertTrue(unregisterGuard > loop);
        assertTrue(unregisterTry > unregisterGuard);
        assertTrue(unregister > unregisterTry);
        assertTrue(unregisterCatch > unregister);
        assertTrue(collectFailure > unregisterCatch);
        assertTrue(deleteTexture > collectFailure);
        assertTrue(mapFinally > deleteTexture);
        assertTrue(ownedClear > mapFinally);
        assertTrue(rethrowFailure > ownedClear);
        assertTrue(deleteBody.contains("if (textureId <= 0)"));
        assertTrue(glDelete >= 0);
        assertTrue(catchBlock > glDelete);
        assertTrue(deleteFinally > catchBlock);
        assertTrue(trackerNotify > deleteFinally);
    }

    @Test
    public void destroyResetsCachedDimensionsEvenWhenTextureCleanupThrows() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String destroyBody = methodBody(source, "public void destroy()");

        int tryBlock = destroyBody.indexOf("try {");
        int destroyTextures = destroyBody.indexOf("destroyTextures();", tryBlock);
        int finallyBlock = destroyBody.indexOf("} finally {", destroyTextures);
        int resetWidth = destroyBody.indexOf("framebufferWidth = -1;", finallyBlock);
        int resetHeight = destroyBody.indexOf("framebufferHeight = -1;", resetWidth);

        assertTrue(tryBlock >= 0);
        assertTrue(destroyTextures > tryBlock);
        assertTrue(finallyBlock > destroyTextures);
        assertTrue("Cached custom-image framebuffer width must reset even after cleanup failures",
            resetWidth > finallyBlock);
        assertTrue("Cached custom-image framebuffer height must reset even after cleanup failures",
            resetHeight > resetWidth);
        assertFalse(destroyBody.substring(0, finallyBlock).contains("framebufferWidth = -1;"));
        assertFalse(destroyBody.substring(0, finallyBlock).contains("framebufferHeight = -1;"));
    }

    @Test
    public void initializeCleansOnlyReplacementImageStateWhenAllocationFails() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String initializeBody = methodBody(source, "public void initializeOrResize(int width, int height)");

        int newTextures = initializeBody.indexOf("Map<String, CustomImageTexture> newTextures = new LinkedHashMap<>();");
        int loopGuard = initializeBody.indexOf("try {\n"
            + "            for (CustomImageData data : imageData.values()) {");
        int newTexturePut = initializeBody.indexOf("newTextures.put(data.getImageName(), texture);", loopGuard);
        int catchBlock = initializeBody.indexOf("catch (RuntimeException | Error exception)", newTexturePut);
        int cleanup = initializeBody.indexOf("destroyTextureMap(newTextures, false);", catchBlock);
        int rethrow = initializeBody.indexOf("throw exception;", cleanup);
        int replaceTextures = initializeBody.indexOf("replaceTextures(newTextures, resolvedWidth, resolvedHeight);",
            rethrow);

        assertTrue(newTextures >= 0);
        assertTrue(loopGuard > newTextures);
        assertTrue(newTexturePut > loopGuard);
        assertTrue(catchBlock > newTexturePut);
        assertTrue(cleanup > catchBlock);
        assertTrue(rethrow > cleanup);
        assertTrue(replaceTextures > rethrow);
        assertFalse(initializeBody.contains("destroyTextures();"));
        assertFalse(initializeBody.contains("TextureBindingRegistry.register(data.getSamplerName(), texture.binding);"));
        assertFalse(initializeBody.contains("framebufferWidth = resolvedWidth;"));
        assertFalse(initializeBody.contains("framebufferHeight = resolvedHeight;"));
    }

    @Test
    public void replaceTexturesRollsBackRegistrationFailureBeforePublishingReplacementState() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String replaceBody = methodBody(source,
            "private void replaceTextures(Map<String, CustomImageTexture> newTextures, int resolvedWidth, int resolvedHeight)");
        String rollbackBody = methodBody(source, "private void rollbackNewTextureRegistration");

        int oldTextures = replaceBody.indexOf("Map<String, CustomImageTexture> oldTextures = new LinkedHashMap<>(textures);");
        int registeredTextures = replaceBody.indexOf("List<CustomImageTexture> registeredTextures = new ArrayList<>();",
            oldTextures);
        int registerLoop = replaceBody.indexOf("for (CustomImageTexture texture : newTextures.values())",
            registeredTextures);
        int register = replaceBody.indexOf("TextureBindingRegistry.register(texture.data.getSamplerName(), texture.binding);",
            registerLoop);
        int trackRegistered = replaceBody.indexOf("registeredTextures.add(texture);", register);
        int catchBlock = replaceBody.indexOf("catch (RuntimeException | Error exception)", trackRegistered);
        int rollback = replaceBody.indexOf(
            "rollbackNewTextureRegistration(registeredTextures, oldTextures, newTextures, exception);", catchBlock);
        int rethrow = replaceBody.indexOf("throw exception;", rollback);
        int publishClear = replaceBody.indexOf("textures.clear();", rethrow);
        int publishNew = replaceBody.indexOf("textures.putAll(newTextures);", publishClear);
        int publishWidth = replaceBody.indexOf("framebufferWidth = resolvedWidth;", publishNew);
        int publishHeight = replaceBody.indexOf("framebufferHeight = resolvedHeight;", publishWidth);
        int destroyOld = replaceBody.indexOf("destroyTextureMap(oldTextures, true);", publishHeight);

        int unregisterLoop = rollbackBody.indexOf("for (CustomImageTexture texture : registeredTextures)");
        int unregister = rollbackBody.indexOf(
            "TextureBindingRegistry.unregister(texture.data.getSamplerName(), texture.binding);", unregisterLoop);
        int unregisterSuppress = rollbackBody.indexOf("suppressFailure(failure, exception);", unregister);
        int oldRegisterLoop = rollbackBody.indexOf("for (CustomImageTexture texture : oldTextures.values())",
            unregisterSuppress);
        int oldRegister = rollbackBody.indexOf(
            "TextureBindingRegistry.register(texture.data.getSamplerName(), texture.binding);", oldRegisterLoop);
        int oldRegisterSuppress = rollbackBody.indexOf("suppressFailure(failure, exception);", oldRegister);
        int destroyNew = rollbackBody.indexOf("destroyTextureMap(newTextures, false);", oldRegisterSuppress);
        int destroySuppress = rollbackBody.indexOf("suppressFailure(failure, exception);", destroyNew);

        assertTrue(oldTextures >= 0);
        assertTrue(registeredTextures > oldTextures);
        assertTrue(registerLoop > registeredTextures);
        assertTrue(register > registerLoop);
        assertTrue(trackRegistered > register);
        assertTrue(catchBlock > trackRegistered);
        assertTrue(rollback > catchBlock);
        assertTrue(rethrow > rollback);
        assertTrue("Replacement images must not publish until all sampler aliases register",
            publishClear > rethrow);
        assertTrue(publishNew > publishClear);
        assertTrue("Replacement resize dimensions must publish with the new texture map",
            publishWidth > publishNew);
        assertTrue(publishHeight > publishWidth);
        assertTrue("Replacement dimensions must publish before old texture cleanup can throw",
            destroyOld > publishHeight);
        assertFalse("Old image state must remain authoritative inside the registration try block",
            replaceBody.substring(0, catchBlock).contains("textures.clear();"));
        assertFalse("Failed replacement registration must not publish new framebuffer dimensions",
            replaceBody.substring(0, catchBlock).contains("framebufferWidth = resolvedWidth;"));

        assertTrue(unregisterLoop >= 0);
        assertTrue(unregister > unregisterLoop);
        assertTrue(unregisterSuppress > unregister);
        assertTrue(oldRegisterLoop > unregisterSuppress);
        assertTrue(oldRegister > oldRegisterLoop);
        assertTrue(oldRegisterSuppress > oldRegister);
        assertTrue(destroyNew > oldRegisterSuppress);
        assertTrue(destroySuppress > destroyNew);
        assertFalse(rollbackBody.contains("destroyTextureMap(oldTextures"));
    }

    @Test
    public void allocateTextureDeletesGeneratedTextureWhenSetupFails() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String allocateBody = methodBody(source, "private CustomImageTexture allocateTexture(CustomImageData data, Dimensions dimensions)");
        String restoreTextureBody = methodBody(source, "private static Throwable restoreTextureBinding");
        String restoreActiveBody = methodBody(source, "private static Throwable restoreActiveTexture");
        String collectRestoreBody = methodBody(source, "private static Throwable collectRestoreFailure");

        int textureId = allocateBody.indexOf("int textureId = GL11.glGenTextures();");
        int previousActiveTexture = allocateBody.indexOf("int previousActiveTexture = GL13.GL_TEXTURE0;", textureId);
        int previousActiveTextureCaptured = allocateBody.indexOf("boolean previousActiveTextureCaptured = false;",
            previousActiveTexture);
        int previousTexture = allocateBody.indexOf("int previousTexture = 0;", previousActiveTextureCaptured);
        int previousTextureCaptured = allocateBody.indexOf("boolean previousTextureCaptured = false;", previousTexture);
        int successFlag = allocateBody.indexOf("boolean success = false;", previousTextureCaptured);
        int setupFailure = allocateBody.indexOf("Throwable setupFailure = null;", successFlag);
        int tryBlock = allocateBody.indexOf("try {", setupFailure);
        int capturePreviousActiveTexture = allocateBody.indexOf(
            "previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);",
            tryBlock);
        int markPreviousActiveTextureCaptured = allocateBody.indexOf("previousActiveTextureCaptured = true;",
            capturePreviousActiveTexture);
        int restoreDefaultActiveTexture = allocateBody.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();",
            markPreviousActiveTextureCaptured);
        int capturePreviousTexture = allocateBody.indexOf("previousTexture = getBoundTexture(target);",
            restoreDefaultActiveTexture);
        int markPreviousTextureCaptured = allocateBody.indexOf("previousTextureCaptured = true;", capturePreviousTexture);
        int bindTexture = allocateBody.indexOf("bindTexture(target, textureId);", markPreviousTextureCaptured);
        int successSet = allocateBody.indexOf("success = true;", bindTexture);
        int catchBlock = allocateBody.indexOf("catch (RuntimeException | Error exception)", successSet);
        int setupFailureCapture = allocateBody.indexOf("setupFailure = exception;", catchBlock);
        int rethrow = allocateBody.indexOf("throw exception;", setupFailureCapture);
        int restoreFailure = allocateBody.indexOf("Throwable restoreFailure = null;", rethrow);
        int restoreTexture = allocateBody.indexOf(
            "restoreTextureBinding(target, previousTexture, previousTextureCaptured, setupFailure,", restoreFailure);
        int restoreActiveTexture = allocateBody.indexOf(
            "restoreActiveTexture(previousActiveTexture, previousActiveTextureCaptured, setupFailure,", restoreTexture);
        int cleanupTexture = allocateBody.indexOf("boolean cleanupTexture = !success || restoreFailure != null;",
            restoreActiveTexture);
        int deleteGuard = allocateBody.indexOf("if (cleanupTexture && textureId > 0)", cleanupTexture);
        int deleteTexture = allocateBody.indexOf("deleteTexture(textureId, data.getImageName());", deleteGuard);
        int rethrowRestore = allocateBody.indexOf("rethrowFailure(restoreFailure);", deleteTexture);

        int restoreTextureBind = restoreTextureBody.indexOf("bindTexture(target, previousTexture);");
        int restoreTextureCatch = restoreTextureBody.indexOf("catch (RuntimeException | Error exception)",
            restoreTextureBind);
        int restoreTextureCollect = restoreTextureBody.indexOf(
            "return collectRestoreFailure(primaryFailure, restoreFailure, exception);", restoreTextureCatch);
        int restoreActiveSet = restoreActiveBody.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);");
        int restoreActiveCatch = restoreActiveBody.indexOf("catch (RuntimeException | Error exception)",
            restoreActiveSet);
        int restoreActiveCollect = restoreActiveBody.indexOf(
            "return collectRestoreFailure(primaryFailure, restoreFailure, exception);", restoreActiveCatch);
        int primaryGuard = collectRestoreBody.indexOf("if (primaryFailure != null)");
        int suppress = collectRestoreBody.indexOf("suppressFailure(primaryFailure, exception);", primaryGuard);
        int keepOriginal = collectRestoreBody.indexOf("return restoreFailure;", suppress);
        int collectForRethrow = collectRestoreBody.indexOf("return collectFailure(restoreFailure, exception);",
            keepOriginal);

        assertTrue(textureId >= 0);
        assertTrue(previousActiveTexture > textureId);
        assertTrue(previousActiveTextureCaptured > previousActiveTexture);
        assertTrue(previousTexture > previousActiveTextureCaptured);
        assertTrue(previousTextureCaptured > previousTexture);
        assertTrue(successFlag > previousTextureCaptured);
        assertTrue(setupFailure > successFlag);
        assertTrue(tryBlock > setupFailure);
        assertTrue("Custom image allocation must capture active texture inside the rollback scope",
            capturePreviousActiveTexture > tryBlock);
        assertTrue(markPreviousActiveTextureCaptured > capturePreviousActiveTexture);
        assertTrue("Custom image allocation must operate on the default active texture unit",
            restoreDefaultActiveTexture > markPreviousActiveTextureCaptured);
        assertTrue(capturePreviousTexture > restoreDefaultActiveTexture);
        assertTrue(markPreviousTextureCaptured > capturePreviousTexture);
        assertTrue(bindTexture > markPreviousTextureCaptured);
        assertTrue(successSet > successFlag);
        assertTrue(catchBlock > successSet);
        assertTrue(setupFailureCapture > catchBlock);
        assertTrue(rethrow > setupFailureCapture);
        assertTrue(restoreFailure > rethrow);
        assertTrue(restoreTexture > restoreFailure);
        assertTrue(restoreActiveTexture > restoreTexture);
        assertTrue("A successful custom-image allocation must still be deleted when restore cleanup fails before publication",
            cleanupTexture > restoreActiveTexture);
        assertTrue(deleteGuard > cleanupTexture);
        assertTrue(deleteTexture > deleteGuard);
        assertTrue(rethrowRestore > deleteTexture);
        assertTrue(restoreTextureBind >= 0);
        assertTrue(restoreTextureCatch > restoreTextureBind);
        assertTrue(restoreTextureCollect > restoreTextureCatch);
        assertTrue(restoreActiveSet >= 0);
        assertTrue(restoreActiveCatch > restoreActiveSet);
        assertTrue(restoreActiveCollect > restoreActiveCatch);
        assertTrue(primaryGuard >= 0);
        assertTrue(suppress > primaryGuard);
        assertTrue(keepOriginal > suppress);
        assertTrue(collectForRethrow > keepOriginal);
        assertFalse(allocateBody.contains("int previousActiveTexture = GL11.glGetInteger"));
        assertFalse(allocateBody.contains("GL11.glDeleteTextures(textureId);"));
    }

    @Test
    public void fallbackClearRestoresTextureUnitAndUnpackAlignmentIfTextureBindFails() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String clearBody = methodBody(source, "private void clearTextureFallback(CustomImageTexture texture)");
        String restoreUnpackBody = methodBody(source, "private static Throwable restoreUnpackAlignment");

        int previousActiveTexture = clearBody.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int previousUnpack = clearBody.indexOf("int previousUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);",
            previousActiveTexture);
        int previousTexture = clearBody.indexOf("int previousTexture = 0;", previousUnpack);
        int previousTextureCaptured = clearBody.indexOf("boolean previousTextureCaptured = false;", previousTexture);
        int unpackAlignmentChanged = clearBody.indexOf("boolean unpackAlignmentChanged = false;", previousTextureCaptured);
        int clearFailure = clearBody.indexOf("Throwable clearFailure = null;", unpackAlignmentChanged);
        int restoreDefaultActiveTexture = clearBody.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();",
            clearFailure);
        int capturePreviousTexture = clearBody.indexOf("previousTexture = getBoundTexture(texture.target);",
            restoreDefaultActiveTexture);
        int markPreviousTextureCaptured = clearBody.indexOf("previousTextureCaptured = true;", capturePreviousTexture);
        int setUnpack = clearBody.indexOf("GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);", markPreviousTextureCaptured);
        int markUnpackChanged = clearBody.indexOf("unpackAlignmentChanged = true;", setUnpack);
        int bindTexture = clearBody.indexOf("bindTexture(texture.target, texture.textureId);", markUnpackChanged);
        int catchBlock = clearBody.indexOf("catch (RuntimeException | Error exception)", bindTexture);
        int clearFailureCapture = clearBody.indexOf("clearFailure = exception;", catchBlock);
        int rethrow = clearBody.indexOf("throw exception;", clearFailureCapture);
        int restoreFailure = clearBody.indexOf("Throwable restoreFailure = null;", rethrow);
        int restoreTexture = clearBody.indexOf(
            "restoreTextureBinding(texture.target, previousTexture, previousTextureCaptured, clearFailure,",
            restoreFailure);
        int restoreUnpack = clearBody.indexOf(
            "restoreUnpackAlignment(previousUnpackAlignment, unpackAlignmentChanged, clearFailure,",
            restoreTexture);
        int restoreActiveTexture = clearBody.indexOf(
            "restoreActiveTexture(previousActiveTexture, true, clearFailure, restoreFailure);", restoreUnpack);
        int rethrowRestore = clearBody.indexOf("rethrowFailure(restoreFailure);", restoreActiveTexture);

        int restoreUnpackPixelStore = restoreUnpackBody.indexOf(
            "GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousUnpackAlignment);");
        int restoreUnpackCatch = restoreUnpackBody.indexOf("catch (RuntimeException | Error exception)",
            restoreUnpackPixelStore);
        int restoreUnpackCollect = restoreUnpackBody.indexOf(
            "return collectRestoreFailure(primaryFailure, restoreFailure, exception);", restoreUnpackCatch);

        assertTrue(previousActiveTexture >= 0);
        assertTrue(previousUnpack > previousActiveTexture);
        assertTrue(previousTexture > previousUnpack);
        assertTrue(previousTextureCaptured > previousTexture);
        assertTrue(unpackAlignmentChanged > previousTextureCaptured);
        assertTrue(clearFailure > unpackAlignmentChanged);
        assertTrue("Fallback image clears must operate on the default active texture unit",
            restoreDefaultActiveTexture > clearFailure);
        assertTrue(capturePreviousTexture > restoreDefaultActiveTexture);
        assertTrue(markPreviousTextureCaptured > capturePreviousTexture);
        assertTrue(setUnpack > markPreviousTextureCaptured);
        assertTrue(markUnpackChanged > setUnpack);
        assertTrue(bindTexture > markUnpackChanged);
        assertTrue(catchBlock > bindTexture);
        assertTrue(clearFailureCapture > catchBlock);
        assertTrue(rethrow > clearFailureCapture);
        assertTrue(restoreFailure > rethrow);
        assertTrue(restoreTexture > restoreFailure);
        assertTrue(restoreUnpack > restoreTexture);
        assertTrue("Fallback image clears must restore the caller active texture unit last",
            restoreActiveTexture > restoreUnpack);
        assertTrue(rethrowRestore > restoreActiveTexture);
        assertTrue(restoreUnpackPixelStore >= 0);
        assertTrue(restoreUnpackCatch > restoreUnpackPixelStore);
        assertTrue(restoreUnpackCollect > restoreUnpackCatch);
    }

    @Test
    public void clearNewFrameImagesAttemptsEveryClearableImageBeforeRethrowing() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomImageManager.java")), StandardCharsets.UTF_8);
        String clearBody = methodBody(source, "public void clearNewFrameImages()");
        String collectBody = methodBody(source,
            "private static Throwable collectFailure(Throwable failure, Throwable exception)");
        String suppressBody = methodBody(source,
            "private static void suppressFailure(Throwable failure, Throwable exception)");
        String rethrowBody = methodBody(source, "private static void rethrowFailure(Throwable failure)");

        int failureInit = clearBody.indexOf("Throwable failure = null;");
        int loop = clearBody.indexOf("for (CustomImageTexture texture : textures.values())", failureInit);
        int guard = clearBody.indexOf("if (texture.data.shouldClearOnNewFrame())", loop);
        int tryBlock = clearBody.indexOf("try {", guard);
        int clearTexture = clearBody.indexOf("clearTexture(texture);", tryBlock);
        int catchBlock = clearBody.indexOf("catch (RuntimeException | Error exception)", clearTexture);
        int collect = clearBody.indexOf("failure = collectFailure(failure, exception);", catchBlock);
        int rethrow = clearBody.indexOf("rethrowFailure(failure);", collect);

        assertTrue(failureInit >= 0);
        assertTrue(loop > failureInit);
        assertTrue(guard > loop);
        assertTrue(tryBlock > guard);
        assertTrue(clearTexture > tryBlock);
        assertTrue(catchBlock > clearTexture);
        assertTrue(collect > catchBlock);
        assertTrue(rethrow > collect);
        assertFalse(clearBody.contains("throw exception;"));
        assertTrue(collectBody.contains("suppressFailure(failure, exception);"));
        assertTrue(suppressBody.contains("if (failure != exception)"));
        assertTrue(suppressBody.contains("failure.addSuppressed(exception);"));
        assertTrue(rethrowBody.contains("throw (RuntimeException) failure;"));
        assertTrue(rethrowBody.contains("throw (Error) failure;"));
    }

    @Test
    public void collectFailurePreservesOriginalWhenSameThrowableIsReportedTwice() throws Exception {
        RuntimeException failure = new RuntimeException("same image failure");

        Throwable collected = collectFailure(failure, failure);

        assertSame(failure, collected);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void collectFailureSuppressesDistinctLaterFailure() throws Exception {
        RuntimeException firstFailure = new RuntimeException("first image failure");
        RuntimeException secondFailure = new RuntimeException("second image failure");

        Throwable collected = collectFailure(firstFailure, secondFailure);

        assertSame(firstFailure, collected);
        assertEquals(1, firstFailure.getSuppressed().length);
        assertSame(secondFailure, firstFailure.getSuppressed()[0]);
    }

    @Test
    public void destroyPreservesNewerSamplerBindingWithSameCustomImageSamplerName() throws Exception {
        TextureBindingRegistry.clear();
        CustomImageManager manager = new CustomImageManager(null, null);
        CustomImageData data = new CustomImageData(
            "wsr_img",
            "wsr_sampler",
            PixelFormat.RED_INTEGER,
            InternalTextureFormat.R8UI,
            PixelType.UNSIGNED_INT,
            true,
            false,
            "1",
            "1",
            null);
        TextureBinding ownedBinding = TextureBinding.texture2D(() -> 77);
        TextureBinding newerBinding = TextureBinding.texture2D(() -> 88);

        try {
            putTexture(manager, data, ownedBinding);
            TextureBindingRegistry.register(data.getSamplerName(), ownedBinding);
            TextureBindingRegistry.register(data.getSamplerName(), newerBinding);

            manager.destroy();

            assertEquals(88, TextureBindingRegistry.resolve(data.getSamplerName()).getTextureId());
        } finally {
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void resizeFailureBeforeAllocationPreservesPreviousImageState() throws Exception {
        TextureBindingRegistry.clear();
        ShaderProperties properties = new ShaderProperties(
            "image.wsr_img=wsr_sampler red_integer r16ui unsigned_int true false MISSING_WIDTH 64\n");
        CustomImageManager manager = new CustomImageManager(properties, null);
        CustomImageData data = properties.getCustomImages().get("wsr_img");
        TextureBinding oldBinding = TextureBinding.texture2D(() -> 77);

        try {
            putTexture(manager, data, oldBinding);
            TextureBindingRegistry.register(data.getSamplerName(), oldBinding);

            try {
                manager.initializeOrResize(64, 64);
            } catch (IllegalStateException exception) {
                assertTrue(exception.getMessage().contains("MISSING_WIDTH"));
                assertEquals(1, textureCount(manager));
                assertEquals(77, TextureBindingRegistry.resolve(data.getSamplerName()).getTextureId());
                return;
            }

            throw new AssertionError("Expected unresolved replacement dimensions to fail");
        } finally {
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void customImageSamplerBindingRequeriesCurrentTextureIdAcrossReplacement() throws Exception {
        CustomImageManager manager = new CustomImageManager(null, null);
        CustomImageData data = new CustomImageData(
            "wsr_img",
            "wsr_sampler",
            PixelFormat.RED_INTEGER,
            InternalTextureFormat.R16UI,
            PixelType.UNSIGNED_INT,
            true,
            false,
            "1",
            "1",
            "1");
        TextureBinding binding = createBinding(manager, data);

        putTexture(manager, data, binding, 77, GL12.GL_TEXTURE_3D);
        assertEquals(77, binding.getTextureId());

        putTexture(manager, data, binding, 88, GL12.GL_TEXTURE_3D);
        assertEquals(88, binding.getTextureId());
    }

    @Test
    public void complementaryVolumeCustomImagesUse3DSamplerBinding() throws Exception {
        CustomImageManager manager = new CustomImageManager(null, null);
        CustomImageData volumeImage = new CustomImageData(
            "voxel_img",
            "voxel_sampler",
            PixelFormat.RED_INTEGER,
            InternalTextureFormat.R8UI,
            PixelType.UNSIGNED_INT,
            true,
            false,
            "128",
            "64",
            "128");
        CustomImageData planeImage = new CustomImageData(
            "puddle_img",
            "puddle_sampler",
            PixelFormat.RED_INTEGER,
            InternalTextureFormat.R8UI,
            PixelType.UNSIGNED_INT,
            true,
            false,
            "128",
            "128",
            null);

        assertEquals(GL12.GL_TEXTURE_3D, createBinding(manager, volumeImage).getTarget());
        assertEquals(GL11.GL_TEXTURE_2D, createBinding(manager, planeImage).getTarget());
    }

    private static OptionValues optionValues(String name, String defaultValue, String allowedValues, String selectedValue) {
        StringOption option = StringOption.create(
            OptionType.DEFINE,
            name,
            "Custom image dimension [" + allowedValues + "]",
            defaultValue);
        OptionSet optionSet = OptionSet.builder().addString(option).build();
        Map<String, String> values = new HashMap<>();
        values.put(name, selectedValue);
        return new OptionValues(optionSet, values);
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

    @SuppressWarnings("unchecked")
    private static void putTexture(CustomImageManager manager, CustomImageData data, TextureBinding binding)
        throws Exception {
        putTexture(manager, data, binding, 0, 0);
    }

    private static void putTexture(CustomImageManager manager, CustomImageData data, TextureBinding binding,
                                   int textureId, int target)
        throws Exception {
        Class<?> dimensionsClass = Class.forName("net.oculus.pipeline.texture.CustomImageManager$Dimensions");
        Constructor<?> dimensionsConstructor = dimensionsClass.getDeclaredConstructor(int.class, int.class, int.class);
        dimensionsConstructor.setAccessible(true);
        Object dimensions = dimensionsConstructor.newInstance(1, 1, 1);

        Class<?> textureClass = Class.forName("net.oculus.pipeline.texture.CustomImageManager$CustomImageTexture");
        Constructor<?> textureConstructor = textureClass.getDeclaredConstructor(
            CustomImageData.class,
            dimensionsClass,
            int.class,
            int.class,
            TextureBinding.class);
        textureConstructor.setAccessible(true);
        Object texture = textureConstructor.newInstance(data, dimensions, textureId, target, binding);

        java.lang.reflect.Field texturesField = CustomImageManager.class.getDeclaredField("textures");
        texturesField.setAccessible(true);
        Map<String, Object> textures = (Map<String, Object>) texturesField.get(manager);
        textures.put(data.getImageName(), texture);
    }

    private static TextureBinding createBinding(CustomImageManager manager, CustomImageData data)
        throws Exception {
        java.lang.reflect.Method method = CustomImageManager.class.getDeclaredMethod(
            "createBinding", CustomImageData.class);
        method.setAccessible(true);
        return (TextureBinding) method.invoke(manager, data);
    }

    private static Throwable collectFailure(Throwable failure, Throwable exception) throws ReflectiveOperationException {
        Method method = CustomImageManager.class.getDeclaredMethod("collectFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(null, failure, exception);
    }

    @SuppressWarnings("unchecked")
    private static int textureCount(CustomImageManager manager) throws Exception {
        java.lang.reflect.Field texturesField = CustomImageManager.class.getDeclaredField("textures");
        texturesField.setAccessible(true);
        Map<String, Object> textures = (Map<String, Object>) texturesField.get(manager);
        return textures.size();
    }

}
