package net.oculus.pipeline.texture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.ToIntBiFunction;

import org.junit.Test;

import net.oculus.gl.program.ProgramSamplers;
import net.oculus.gl.program.SamplerOverrideMap;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.texture.CustomTextureData;
import net.oculus.shaderpack.texture.TextureStage;
import net.oculus.texture.TextureInfoCache;
import net.oculus.texture.pbr.PBRType;
import org.lwjgl.opengl.GL11;

public class CustomTextureManagerTest {
    @Test
    public void renderTargetOverrideIsAppliedBeforeBufferHasBeenFlipped() {
        assertTrue(CustomTextureManager.shouldApplyStageOverride("colortex3", Collections.singleton(2)));
    }

    @Test
    public void colortexOverrideIsDeactivatedAfterBufferHasBeenFlipped() {
        assertFalse(CustomTextureManager.shouldApplyStageOverride("colortex3", Collections.singleton(3)));
    }

    @Test
    public void legacyRenderTargetOverrideIsDeactivatedAfterBufferHasBeenFlipped() {
        assertFalse(CustomTextureManager.shouldApplyStageOverride("gaux2", Collections.singleton(5)));
    }

    @Test
    public void overrideDeactivationPreservesReferenceCaseSensitivity() {
        assertTrue(CustomTextureManager.shouldApplyStageOverride("COLORTEX7", Collections.singleton(7)));
        assertTrue(CustomTextureManager.shouldApplyStageOverride("GAUX2", Collections.singleton(5)));
    }

    @Test
    public void nonRenderTargetOverrideIsNotDeactivated() {
        assertTrue(CustomTextureManager.shouldApplyStageOverride("cloudNoise", Arrays.asList(3, 5, 7)));
    }

    @Test
    public void missingFlipSnapshotKeepsLegacyBehavior() {
        assertTrue(CustomTextureManager.shouldApplyStageOverride("colortex3", null));
    }

    @Test
    public void lowRenderTargetSamplerDetectionPreservesReferenceNamesAndCase() {
        assertTrue(CustomTextureManager.isLowRenderTargetSamplerName("gcolor"));
        assertTrue(CustomTextureManager.isLowRenderTargetSamplerName("gdepth"));
        assertTrue(CustomTextureManager.isLowRenderTargetSamplerName("gnormal"));
        assertTrue(CustomTextureManager.isLowRenderTargetSamplerName("composite"));
        assertTrue(CustomTextureManager.isLowRenderTargetSamplerName("gaux0"));
        assertTrue(CustomTextureManager.isLowRenderTargetSamplerName("colortex0"));
        assertTrue(CustomTextureManager.isLowRenderTargetSamplerName("colortex3"));

        assertFalse(CustomTextureManager.isLowRenderTargetSamplerName("colortex4"));
        assertFalse(CustomTextureManager.isLowRenderTargetSamplerName("gaux1"));
        assertFalse(CustomTextureManager.isLowRenderTargetSamplerName("depthtex0"));
        assertFalse(CustomTextureManager.isLowRenderTargetSamplerName("gdepthtex"));
        assertFalse(CustomTextureManager.isLowRenderTargetSamplerName("noisetex"));
        assertFalse(CustomTextureManager.isLowRenderTargetSamplerName("COLORTEX0"));
    }

    @Test
    public void unregisteredHelperOwnedTargetOverrideIsSkippedLikeReference() {
        assertFalse(CustomTextureManager.shouldApplyUnregisteredStageOverride("gcolor", null));
        assertFalse(CustomTextureManager.shouldApplyUnregisteredStageOverride("colortex0", null));
        assertFalse(CustomTextureManager.shouldApplyUnregisteredStageOverride("colortex3", null));
        assertFalse(CustomTextureManager.shouldApplyUnregisteredStageOverride("depthtex0", null));
        assertFalse(CustomTextureManager.shouldApplyUnregisteredStageOverride("depthtex1", null));
        assertFalse(CustomTextureManager.shouldApplyUnregisteredStageOverride("gdepthtex", null));
        assertFalse(CustomTextureManager.shouldApplyUnregisteredStageOverride("depthtex2", null));

        assertTrue(CustomTextureManager.shouldApplyUnregisteredStageOverride("tex", null));
        assertTrue(CustomTextureManager.shouldApplyUnregisteredStageOverride("colortex4", null));
        assertTrue(CustomTextureManager.shouldApplyUnregisteredStageOverride("noisetex", null));
    }

    @Test
    public void helperOwnedTargetOverrideNamesRequireRegisteredSamplerBinding() {
        assertTrue(CustomTextureManager.requiresRegisteredStageOverride("gcolor"));
        assertTrue(CustomTextureManager.requiresRegisteredStageOverride("colortex0"));
        assertTrue(CustomTextureManager.requiresRegisteredStageOverride("depthtex0"));
        assertTrue(CustomTextureManager.requiresRegisteredStageOverride("depthtex1"));
        assertTrue(CustomTextureManager.requiresRegisteredStageOverride("gdepthtex"));
        assertTrue(CustomTextureManager.requiresRegisteredStageOverride("depthtex2"));

        assertFalse(CustomTextureManager.requiresRegisteredStageOverride("tex"));
        assertFalse(CustomTextureManager.requiresRegisteredStageOverride("colortex4"));
        assertFalse(CustomTextureManager.requiresRegisteredStageOverride("noisetex"));
    }

    @Test
    public void renderTargetStageOverrideNamesIncludeReferenceLegacyColortexPair() {
        assertEquals(Arrays.asList("gaux4", "colortex7"),
            CustomTextureManager.equivalentStageOverrideNames("gaux4"));
        assertEquals(Arrays.asList("colortex7", "gaux4"),
            CustomTextureManager.equivalentStageOverrideNames("colortex7"));
        assertEquals(Arrays.asList("gaux2", "colortex5"),
            CustomTextureManager.equivalentStageOverrideNames("gaux2"));
        assertEquals(Collections.singletonList("cloudNoise"),
            CustomTextureManager.equivalentStageOverrideNames("cloudNoise"));
    }

    @Test
    public void stageOverrideNamesIncludeReferenceLevelAndCompositeDepthAliasGroups() {
        assertEquals(Arrays.asList("gtexture", "tex", "texture", "gcolor", "colortex0"),
            CustomTextureManager.equivalentStageOverrideNames("gtexture"));
        assertEquals(Arrays.asList("tex", "texture", "gtexture", "gcolor", "colortex0"),
            CustomTextureManager.equivalentStageOverrideNames("tex"));
        assertEquals(Arrays.asList("gcolor", "tex", "texture", "gtexture", "colortex0"),
            CustomTextureManager.equivalentStageOverrideNames("gcolor"));
        assertEquals(Arrays.asList("colortex0", "tex", "texture", "gtexture", "gcolor"),
            CustomTextureManager.equivalentStageOverrideNames("colortex0"));
        assertEquals(Arrays.asList("gdepthtex", "depthtex0"),
            CustomTextureManager.equivalentStageOverrideNames("gdepthtex"));
        assertEquals(Arrays.asList("depthtex0", "gdepthtex"),
            CustomTextureManager.equivalentStageOverrideNames("depthtex0"));
    }

    @Test
    public void renderTargetStageOverrideNamesPreserveShaderPackCase() {
        assertEquals(Collections.singletonList("GAUX4"),
            CustomTextureManager.equivalentStageOverrideNames("GAUX4"));
        assertEquals(Collections.singletonList("COLORTEX7"),
            CustomTextureManager.equivalentStageOverrideNames("COLORTEX7"));
        assertEquals(Collections.singletonList("GTEXTURE"),
            CustomTextureManager.equivalentStageOverrideNames("GTEXTURE"));
        assertEquals(Collections.singletonList("GDEPTHTEX"),
            CustomTextureManager.equivalentStageOverrideNames("GDEPTHTEX"));
    }

    @Test
    public void legacyStageOverrideUpdatesEquivalentColortexSamplerLikeReferenceInterceptor() throws Exception {
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(null);
        TextureBinding customBinding = TextureBinding.texture2D(() -> 77);
        Map<String, TextureBinding> gbuffersBindings = new HashMap<>();
        gbuffersBindings.put("gaux4", customBinding);
        stageBindings(manager).put(TextureStage.GBUFFERS_AND_SHADOW, gbuffersBindings);
        markInitialized(manager);

        ProgramSamplers.Builder builder = programSamplersBuilder("gbuffers_terrain", "colortex7");
        manager.applyCustomSamplers("gbuffers_terrain", builder, Collections.emptySet());

        Object samplerBinding = firstSamplerBinding(builder.build());
        assertEquals("colortex7", fieldValue(samplerBinding, "uniformName"));
        assertSame(customBinding, fieldValue(samplerBinding, "binding"));
    }

    @Test
    public void deactivatedLegacyStageOverrideDoesNotUpdateEquivalentColortexSampler() throws Exception {
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(null);
        TextureBinding customBinding = TextureBinding.texture2D(() -> 77);
        Map<String, TextureBinding> gbuffersBindings = new HashMap<>();
        gbuffersBindings.put("gaux4", customBinding);
        stageBindings(manager).put(TextureStage.GBUFFERS_AND_SHADOW, gbuffersBindings);
        markInitialized(manager);

        ProgramSamplers.Builder builder = programSamplersBuilder("gbuffers_terrain", "colortex7");
        manager.applyCustomSamplers("gbuffers_terrain", builder, Collections.singleton(7));

        assertTrue(samplerBindings(builder.build()).isEmpty());
    }

    @Test
    public void levelAlbedoStageOverrideUpdatesEquivalentActiveAliasLikeReferenceInterceptor() throws Exception {
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(null);
        TextureBinding customBinding = TextureBinding.texture2D(() -> 78);
        Map<String, TextureBinding> gbuffersBindings = new HashMap<>();
        gbuffersBindings.put("gtexture", customBinding);
        stageBindings(manager).put(TextureStage.GBUFFERS_AND_SHADOW, gbuffersBindings);
        markInitialized(manager);

        ProgramSamplers.Builder builder = programSamplersBuilder("gbuffers_terrain", "tex");
        manager.applyCustomSamplers("gbuffers_terrain", builder, Collections.emptySet());

        Object samplerBinding = firstSamplerBinding(builder.build());
        assertEquals("tex", fieldValue(samplerBinding, "uniformName"));
        assertSame(customBinding, fieldValue(samplerBinding, "binding"));
    }

    @Test
    public void levelAlbedoStageOverrideReplacesExternalAtlasAliasGroupLikeReferenceInterceptor() throws Exception {
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(null);
        TextureBinding customBinding = TextureBinding.texture2D(() -> 82);
        Map<String, TextureBinding> gbuffersBindings = new HashMap<>();
        gbuffersBindings.put("gtexture", customBinding);
        stageBindings(manager).put(TextureStage.GBUFFERS_AND_SHADOW, gbuffersBindings);
        markInitialized(manager);

        ProgramSamplers.Builder builder = programSamplersBuilderWithReserved(
            "gbuffers_terrain", Collections.singleton(0), "tex", "texture", "gtexture");
        builder.addExternalSampler(0, "tex", "texture", "gtexture");

        manager.applyCustomSamplers("gbuffers_terrain", builder, Collections.emptySet());

        ProgramSamplers samplers = builder.build();
        assertEquals(1, samplers.getActiveSamplers());
        List<Object> bindings = samplerBindings(samplers);
        assertEquals(3, bindings.size());
        assertSharedCustomBinding(bindings, customBinding, "tex", 0);
        assertSharedCustomBinding(bindings, customBinding, "texture", 0);
        assertSharedCustomBinding(bindings, customBinding, "gtexture", 0);
    }

    @Test
    public void nonZeroExternalStageOverrideUsesDynamicUnitLikeReferenceInterceptor() throws Exception {
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(null);
        TextureBinding customBinding = TextureBinding.texture2D(() -> 83);
        Map<String, TextureBinding> gbuffersBindings = new HashMap<>();
        gbuffersBindings.put("lightmap", customBinding);
        stageBindings(manager).put(TextureStage.GBUFFERS_AND_SHADOW, gbuffersBindings);
        markInitialized(manager);

        ProgramSamplers.Builder builder = programSamplersBuilderWithReserved(
            "gbuffers_terrain", new HashSet<>(Arrays.asList(0, 1, 2)), "lightmap");
        builder.addExternalSampler(1, "lightmap");

        manager.applyCustomSamplers("gbuffers_terrain", builder, Collections.emptySet());

        Object samplerBinding = firstSamplerBinding(builder.build());
        assertEquals("lightmap", fieldValue(samplerBinding, "uniformName"));
        assertEquals(16, fieldValue(samplerBinding, "unit"));
        assertSame(customBinding, fieldValue(samplerBinding, "binding"));
    }

    @Test
    public void unavailableAlbedoLowAliasOverrideUpdatesFallbackRegisteredTexLikeReferenceInterceptor() throws Exception {
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(null);
        TextureBinding customBinding = TextureBinding.texture2D(() -> 80);
        Map<String, TextureBinding> gbuffersBindings = new HashMap<>();
        gbuffersBindings.put("gcolor", customBinding);
        stageBindings(manager).put(TextureStage.GBUFFERS_AND_SHADOW, gbuffersBindings);
        markInitialized(manager);

        ProgramSamplers.Builder fallbackBuilder = programSamplersBuilder("gbuffers_terrain", "tex");
        fallbackBuilder.addDynamicSampler(() -> 44, "tex", "texture", "gtexture", "gcolor", "colortex0");
        manager.applyCustomSamplers("gbuffers_terrain", fallbackBuilder, Collections.emptySet());

        Object samplerBinding = firstSamplerBinding(fallbackBuilder.build());
        assertEquals("tex", fieldValue(samplerBinding, "uniformName"));
        assertSame(customBinding, fieldValue(samplerBinding, "binding"));
    }

    @Test
    public void albedoLowAliasOverrideDoesNotReplaceExternalAtlasSamplerWhenFallbackGroupIsAbsent() throws Exception {
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(null);
        TextureBinding customBinding = TextureBinding.texture2D(() -> 81);
        Map<String, TextureBinding> gbuffersBindings = new HashMap<>();
        gbuffersBindings.put("gcolor", customBinding);
        stageBindings(manager).put(TextureStage.GBUFFERS_AND_SHADOW, gbuffersBindings);
        markInitialized(manager);

        ProgramSamplers.Builder externalBuilder = programSamplersBuilderWithReserved(
            "gbuffers_terrain", Collections.singleton(0), "tex");
        externalBuilder.addExternalSampler(0, "tex");
        manager.applyCustomSamplers("gbuffers_terrain", externalBuilder, Collections.emptySet());

        assertTrue(samplerBindings(externalBuilder.build()).isEmpty());
    }

    @Test
    public void compositeDepthStageOverrideUpdatesRegisteredEquivalentAliasLikeReferenceInterceptor() throws Exception {
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(null);
        TextureBinding helperBinding = TextureBinding.texture2D(() -> 44);
        TextureBinding customBinding = TextureBinding.texture2D(() -> 79);
        Map<String, TextureBinding> compositeBindings = new HashMap<>();
        compositeBindings.put("gdepthtex", customBinding);
        stageBindings(manager).put(TextureStage.COMPOSITE_AND_FINAL, compositeBindings);
        markInitialized(manager);

        ProgramSamplers.Builder builder = programSamplersBuilder("composite", "depthtex0");
        builder.addDynamicSampler(() -> helperBinding.getTextureId(), "depthtex0");
        manager.applyCustomSamplers("composite", builder, Collections.emptySet());

        Object samplerBinding = firstSamplerBinding(builder.build());
        assertEquals("depthtex0", fieldValue(samplerBinding, "uniformName"));
        assertSame(customBinding, fieldValue(samplerBinding, "binding"));
    }

    @Test
    public void applyCustomSamplersRequiresRegisteredLowAliasBeforeOverriding() throws Exception {
        String source = source();
        String body = methodBody(source,
            "public void applyCustomSamplers(String programName, ProgramSamplers.Builder builder, Iterable<Integer> flippedAtLeastOnce)");

        int stageOverride = body.indexOf("shouldApplyStageOverride(samplerName, flippedAtLeastOnce)");
        int equivalentOverride = body.indexOf("for (String overrideName : equivalentStageOverrideNames(samplerName))", stageOverride);
        int guardedOverride = body.indexOf("shouldApplyEquivalentStageOverride(samplerName, overrideName, builder)", equivalentOverride);
        int overrideBinding = body.indexOf("builder.overrideBinding(overrideName, binding);", guardedOverride);

        assertTrue(stageOverride >= 0);
        assertTrue(equivalentOverride > stageOverride);
        assertTrue(guardedOverride > equivalentOverride);
        assertTrue(overrideBinding > guardedOverride);
    }

    @Test
    public void defaultNoiseTextureResolutionIsClampedToAtLeastOne() {
        assertEquals(1, CustomTextureManager.sanitizeNoiseTextureResolution(0));
        assertEquals(1, CustomTextureManager.sanitizeNoiseTextureResolution(-16));
        assertEquals(128, CustomTextureManager.sanitizeNoiseTextureResolution(128));
    }

    @Test
    public void defaultNoisePixelBufferSizeUsesWideMathBeforeAllocation() {
        assertEquals(4, CustomTextureManager.defaultNoisePixelBufferSize(1));
        assertEquals(262144, CustomTextureManager.defaultNoisePixelBufferSize(256));
        assertEquals(4, CustomTextureManager.defaultNoisePixelBufferSize(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void defaultNoisePixelBufferSizeRejectsOverflowingPackResolution() {
        CustomTextureManager.defaultNoisePixelBufferSize(32768);
    }

    @Test(expected = IllegalArgumentException.class)
    public void defaultNoisePixelsRejectOverflowingPackResolutionBeforeAllocation() {
        CustomTextureManager.createDefaultNoisePixels(32768);
    }

    @Test
    public void defaultNoisePixelsAreDeterministicAndOpaque() {
        byte[] first = CustomTextureManager.createDefaultNoisePixels(2);
        byte[] second = CustomTextureManager.createDefaultNoisePixels(2);

        assertArrayEquals(first, second);
        assertEquals(16, first.length);
        assertEquals((byte) 0xFF, first[3]);
        assertEquals((byte) 0xFF, first[7]);
        assertEquals((byte) 0xFF, first[11]);
        assertEquals((byte) 0xFF, first[15]);
        assertArrayEquals(createReferenceNoisePixels(2), first);
    }

    @Test
    public void pngCustomTextureAllocationTracksTextureInfoForSizeUniforms() {
        TextureInfoCache.INSTANCE.onDeleteTexture(91);
        try {
            CustomTextureManager.trackPngTextureAllocation(91,
                new BufferedImage(17, 9, BufferedImage.TYPE_INT_ARGB));

            TextureInfoCache.TextureInfo info = TextureInfoCache.INSTANCE.getInfo(91);
            assertEquals(GL11.GL_RGBA, info.getInternalFormat());
            assertEquals(17, info.getWidth());
            assertEquals(9, info.getHeight());
        } finally {
            TextureInfoCache.INSTANCE.onDeleteTexture(91);
        }
    }

    @Test
    public void initializeCleansPartialTextureStateWhenTextureBuildFails() throws Exception {
        String source = source();
        String initializeBody = methodBody(source, "public void initialize()");
        String cleanupBody = methodBody(source, "private void cleanupAfterInitializeFailure(Throwable failure)");

        int tryBlock = initializeBody.indexOf("try {\n"
            + "            Map<CustomTextureData, TextureBinding> bindingCache = new IdentityHashMap<>();");
        int stagePut = initializeBody.indexOf("stageBindings.put(stage, resolved);", tryBlock);
        int rawNoise = initializeBody.indexOf("if (rawNoiseTexture != null)", stagePut);
        int noiseBuild = initializeBody.indexOf("noiseBinding = buildDefaultNoiseBinding(defaultNoiseTextureResolution);", rawNoise);
        int initialized = initializeBody.indexOf("initialized = true;", noiseBuild);
        int catchBlock = initializeBody.indexOf("catch (RuntimeException | Error exception)", initialized);
        int cleanup = initializeBody.indexOf("cleanupAfterInitializeFailure(exception);", catchBlock);
        int rethrow = initializeBody.indexOf("throw exception;", cleanup);

        assertTrue(tryBlock >= 0);
        assertTrue(stagePut > tryBlock);
        assertTrue(rawNoise > stagePut);
        assertTrue(noiseBuild > rawNoise);
        assertTrue(initialized > noiseBuild);
        assertTrue(catchBlock > initialized);
        assertTrue(cleanup > catchBlock);
        assertTrue(rethrow > cleanup);
        assertTrue(cleanupBody.contains("destroy();"));
        assertTrue(cleanupBody.contains("catch (RuntimeException | Error cleanupFailure)"));
        assertTrue(cleanupBody.contains("suppressFailure(failure, cleanupFailure);"));
    }

    @Test
    public void customNoiseFallsBackToGeneratedNoiseWhenDeclaredBindingCannotBeBuilt() throws Exception {
        String source = source();
        String initializeBody = methodBody(source, "public void initialize()");

        int rawNoise = initializeBody.indexOf("if (rawNoiseTexture != null)");
        int rawNoiseBinding = initializeBody.indexOf(
            "noiseBinding = bindingCache.computeIfAbsent(rawNoiseTexture, textureData -> buildBinding(\"noise_texture\", textureData));",
            rawNoise);
        int fallbackGuard = initializeBody.indexOf("if (noiseBinding == null)", rawNoiseBinding);
        int defaultNoise = initializeBody.indexOf(
            "noiseBinding = buildDefaultNoiseBinding(defaultNoiseTextureResolution);",
            fallbackGuard);
        int initialized = initializeBody.indexOf("initialized = true;", defaultNoise);

        assertTrue(rawNoise >= 0);
        assertTrue(rawNoiseBinding > rawNoise);
        assertTrue(fallbackGuard > rawNoiseBinding);
        assertTrue(defaultNoise > fallbackGuard);
        assertTrue(initialized > defaultNoise);
    }

    @Test
    public void pngBindingDeletesGeneratedTextureWhenUploadOrSuccessfulRestoreFails() throws Exception {
        String source = source();
        String body = methodBody(source, "private TextureBinding buildPngBinding(CustomTextureData.PngData pngData, String samplerName)");

        int textureId = body.indexOf("final int textureId = TextureUtil.glGenTextures();");
        int failFast = body.indexOf("throw new IllegalStateException(\"Failed to allocate custom texture \" + samplerName);",
            textureId);
        int successFlag = body.indexOf("boolean success = false;", textureId);
        int setupFailure = body.indexOf("Throwable setupFailure = null;", successFlag);
        int restoreFailure = body.indexOf("Throwable restoreFailure = null;", setupFailure);
        int previousTexture = body.indexOf("int previousTextureBinding = 0;", textureId);
        int previousTextureCaptured = body.indexOf("boolean previousTextureCaptured = false;", previousTexture);
        int capturePreviousTexture = body.indexOf("previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);", previousTextureCaptured);
        int markPreviousTextureCaptured = body.indexOf("previousTextureCaptured = true;", capturePreviousTexture);
        int upload = body.indexOf("TextureUtil.uploadTextureImageAllocate(textureId, image, blur, clamp);", markPreviousTextureCaptured);
        int owned = body.indexOf("ownedTextureIds.add(textureId);", upload);
        int successSet = body.indexOf("success = true;", owned);
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", successSet);
        int captureFailure = body.indexOf("setupFailure = exception;", catchBlock);
        int rethrow = body.indexOf("throw exception;", captureFailure);
        int restoreGuard = body.indexOf("try {", rethrow);
        int restoreCall = body.indexOf("restorePreviousTextureBinding(previousTextureBinding, previousTextureCaptured, setupFailure);",
            restoreGuard);
        int restoreCatch = body.indexOf("catch (RuntimeException | Error exception)", restoreGuard);
        int restoreFailureCapture = body.indexOf("restoreFailure = exception;", restoreCatch);
        int rethrowRestore = body.indexOf("throw exception;", restoreFailureCapture);
        int deleteGuard = body.indexOf("if ((!success || restoreFailure != null) && textureId > 0)", rethrowRestore);
        int removeOwned = body.indexOf("ownedTextureIds.remove(Integer.valueOf(textureId));", deleteGuard);
        int deleteTexture = body.indexOf("deleteOwnedTexture(textureId, \"custom texture \" + samplerName);", removeOwned);

        assertTrue(textureId >= 0);
        assertTrue(failFast > textureId);
        assertTrue(previousTexture > textureId);
        assertTrue(previousTextureCaptured > previousTexture);
        assertTrue(capturePreviousTexture > previousTextureCaptured);
        assertTrue(markPreviousTextureCaptured > capturePreviousTexture);
        assertTrue(successFlag > failFast);
        assertTrue(setupFailure > successFlag);
        assertTrue(restoreFailure > setupFailure);
        assertTrue(upload > restoreFailure);
        assertTrue(owned > upload);
        assertTrue(successSet > owned);
        assertTrue(catchBlock > successSet);
        assertTrue(captureFailure > catchBlock);
        assertTrue(rethrow > captureFailure);
        assertTrue(restoreGuard > rethrow);
        assertTrue(restoreCall > restoreGuard);
        assertTrue(restoreCatch > restoreCall);
        assertTrue(restoreFailureCapture > restoreCatch);
        assertTrue(rethrowRestore > restoreFailureCapture);
        assertTrue(deleteGuard > rethrowRestore);
        assertTrue(removeOwned > deleteGuard);
        assertTrue(deleteTexture > removeOwned);
        assertFalse(body.contains("GL11.glDeleteTextures(textureId);"));
    }

    @Test
    public void defaultNoiseBindingDeletesGeneratedTextureWhenUploadOrSuccessfulRestoreFails() throws Exception {
        String source = source();
        String body = methodBody(source, "private TextureBinding buildDefaultNoiseBinding(int resolution)");

        int textureId = body.indexOf("final int textureId = TextureUtil.glGenTextures();");
        int failFast = body.indexOf("throw new IllegalStateException(\"Failed to allocate generated noise texture\");",
            textureId);
        int successFlag = body.indexOf("boolean success = false;", textureId);
        int setupFailure = body.indexOf("Throwable setupFailure = null;", successFlag);
        int restoreFailure = body.indexOf("Throwable restoreFailure = null;", setupFailure);
        int previousTexture = body.indexOf("int previousTextureBinding = 0;", textureId);
        int previousTextureCaptured = body.indexOf("boolean previousTextureCaptured = false;", previousTexture);
        int capturePreviousTexture = body.indexOf("previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);", previousTextureCaptured);
        int markPreviousTextureCaptured = body.indexOf("previousTextureCaptured = true;", capturePreviousTexture);
        int upload = body.indexOf("GL11.glTexImage2D(", markPreviousTextureCaptured);
        int owned = body.indexOf("ownedTextureIds.add(textureId);", upload);
        int successSet = body.indexOf("success = true;", owned);
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", successSet);
        int captureFailure = body.indexOf("setupFailure = exception;", catchBlock);
        int rethrow = body.indexOf("throw exception;", captureFailure);
        int restoreGuard = body.indexOf("try {", rethrow);
        int restoreCall = body.indexOf("restorePreviousTextureBinding(previousTextureBinding, previousTextureCaptured, setupFailure);",
            restoreGuard);
        int restoreCatch = body.indexOf("catch (RuntimeException | Error exception)", restoreGuard);
        int restoreFailureCapture = body.indexOf("restoreFailure = exception;", restoreCatch);
        int rethrowRestore = body.indexOf("throw exception;", restoreFailureCapture);
        int deleteGuard = body.indexOf("if ((!success || restoreFailure != null) && textureId > 0)", rethrowRestore);
        int removeOwned = body.indexOf("ownedTextureIds.remove(Integer.valueOf(textureId));", deleteGuard);
        int deleteTexture = body.indexOf("deleteOwnedTexture(textureId, \"generated noise texture\");", removeOwned);

        assertTrue(textureId >= 0);
        assertTrue(failFast > textureId);
        assertTrue(previousTexture > textureId);
        assertTrue(previousTextureCaptured > previousTexture);
        assertTrue(capturePreviousTexture > previousTextureCaptured);
        assertTrue(markPreviousTextureCaptured > capturePreviousTexture);
        assertTrue(successFlag > failFast);
        assertTrue(setupFailure > successFlag);
        assertTrue(restoreFailure > setupFailure);
        assertTrue(upload > restoreFailure);
        assertTrue(owned > upload);
        assertTrue(successSet > owned);
        assertTrue(catchBlock > successSet);
        assertTrue(captureFailure > catchBlock);
        assertTrue(rethrow > captureFailure);
        assertTrue(restoreGuard > rethrow);
        assertTrue(restoreCall > restoreGuard);
        assertTrue(restoreCatch > restoreCall);
        assertTrue(restoreFailureCapture > restoreCatch);
        assertTrue(rethrowRestore > restoreFailureCapture);
        assertTrue(deleteGuard > rethrowRestore);
        assertTrue(removeOwned > deleteGuard);
        assertTrue(deleteTexture > removeOwned);
        assertFalse(body.contains("GL11.glDeleteTextures(textureId);"));
    }

    @Test
    public void textureSetupRestoreFailuresAreSuppressedOnOriginalSetupFailure() throws Exception {
        String source = source();
        String restoreBody = methodBody(source,
            "private static void restorePreviousTextureBinding(int previousTextureBinding, boolean previousTextureCaptured,");

        int capturedGuard = restoreBody.indexOf("if (!previousTextureCaptured)");
        int bind = restoreBody.indexOf("GlStateManager.bindTexture(previousTextureBinding);", capturedGuard);
        int catchBlock = restoreBody.indexOf("catch (RuntimeException | Error restoreFailure)", bind);
        int setupFailureGuard = restoreBody.indexOf("if (setupFailure != null)", catchBlock);
        int suppress = restoreBody.indexOf("suppressFailure(setupFailure, restoreFailure);", setupFailureGuard);
        int keepOriginal = restoreBody.indexOf("return;", suppress);
        int failFast = restoreBody.indexOf("throw restoreFailure;", keepOriginal);

        assertTrue(capturedGuard >= 0);
        assertTrue(bind > capturedGuard);
        assertTrue(catchBlock > bind);
        assertTrue(setupFailureGuard > catchBlock);
        assertTrue(suppress > setupFailureGuard);
        assertTrue(keepOriginal > suppress);
        assertTrue(failFast > keepOriginal);
    }

    @Test
    public void collectFailurePreservesOriginalWhenSameThrowableIsReportedTwice() throws Exception {
        RuntimeException failure = new RuntimeException("same failure");

        Throwable collected = collectFailure(failure, failure);

        assertSame(failure, collected);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void collectFailureSuppressesDistinctLaterFailure() throws Exception {
        RuntimeException firstFailure = new RuntimeException("first failure");
        RuntimeException secondFailure = new RuntimeException("second failure");

        Throwable collected = collectFailure(firstFailure, secondFailure);

        assertSame(firstFailure, collected);
        assertEquals(1, firstFailure.getSuppressed().length);
        assertSame(secondFailure, firstFailure.getSuppressed()[0]);
    }

    @Test
    public void resourceCustomTexturesDetectPbrSuffixBeforeExtensionLikeReference() {
        assertEquals(PBRType.NORMAL, CustomTextureManager.detectPbrType("textures/block/stone_n.png"));
        assertEquals(PBRType.SPECULAR, CustomTextureManager.detectPbrType("textures/block/stone_s.png"));
        assertEquals(PBRType.NORMAL, CustomTextureManager.detectPbrType("textures/block/stone.v2_n.png"));
        assertEquals(PBRType.SPECULAR, CustomTextureManager.detectPbrType("textures/block/stone.v2_s.png"));
        assertEquals(PBRType.NORMAL, CustomTextureManager.detectPbrType("textures/block/stone_n"));
        assertEquals(PBRType.SPECULAR, CustomTextureManager.detectPbrType("textures/block/stone_s"));
    }

    @Test
    public void resourceCustomTexturesStripPbrSuffixToBaseResource() {
        assertEquals("textures/block/stone.png",
            CustomTextureManager.stripPbrSuffix("textures/block/stone_n.png", PBRType.NORMAL));
        assertEquals("textures/block/stone.png",
            CustomTextureManager.stripPbrSuffix("textures/block/stone_s.png", PBRType.SPECULAR));
        assertEquals("textures/block/stone.v2.png",
            CustomTextureManager.stripPbrSuffix("textures/block/stone.v2_n.png", PBRType.NORMAL));
        assertEquals("textures/block/stone.v2.png",
            CustomTextureManager.stripPbrSuffix("textures/block/stone.v2_s.png", PBRType.SPECULAR));
        assertEquals("textures/block/stone",
            CustomTextureManager.stripPbrSuffix("textures/block/stone_n", PBRType.NORMAL));
    }

    @Test
    public void pbrResourceReferenceResolvesBaseLocationAndLayerType() {
        CustomTextureManager.ResourceTextureReference reference =
            CustomTextureManager.resolveResourceTextureReference(
                new CustomTextureData.ResourceData("minecraft", "textures/block/stone_s.png"));

        assertEquals("minecraft:textures/block/stone.png", reference.location.toString());
        assertEquals(PBRType.SPECULAR, reference.pbrType);
    }

    @Test
    public void pbrResourceReferencePreservesMultiDotBaseResourceName() {
        CustomTextureManager.ResourceTextureReference reference =
            CustomTextureManager.resolveResourceTextureReference(
                new CustomTextureData.ResourceData("minecraft", "textures/block/stone.v2_n.png"));

        assertEquals("minecraft:textures/block/stone.v2.png", reference.location.toString());
        assertEquals(PBRType.NORMAL, reference.pbrType);
    }

    @Test
    public void regularResourceReferenceKeepsOriginalLocation() {
        CustomTextureManager.ResourceTextureReference reference =
            CustomTextureManager.resolveResourceTextureReference(
                new CustomTextureData.ResourceData("minecraft", "textures/atlas/blocks.png"));

        assertEquals("minecraft:textures/atlas/blocks.png", reference.location.toString());
        assertEquals(null, reference.pbrType);
    }

    @Test
    public void resourceTextureBindingRequeriesTextureManagerInsideSupplierForReloadSafety() throws Exception {
        String source = source();
        String body = methodBody(source, "private TextureBinding buildResourceBinding(CustomTextureData.ResourceData resourceData)");

        int initialManager = body.indexOf("TextureManager textureManager = getTextureManager();");
        int resolveTry = body.indexOf("try {", initialManager);
        int resolve = body.indexOf("reference = resolveResourceTextureReference(resourceData);", resolveTry);
        int resolveCatch = body.indexOf("catch (RuntimeException exception)", resolve);
        int skipInvalid = body.indexOf("return null;", resolveCatch);
        int ensureLoaded = body.indexOf("ensureTextureLoaded(textureManager, reference.location);", skipInvalid);
        int lambda = body.indexOf("return TextureBinding.texture2D(() -> {", ensureLoaded);
        int reloadComment = body.indexOf("Resource reloads can replace the texture object", lambda);
        int liveManager = body.indexOf("TextureManager manager = getTextureManager();", lambda);
        int liveTexture = body.indexOf("ITextureObject texture = manager.getTexture(reference.location);", liveManager);
        int missingTexture = body.indexOf("return TextureUtil.MISSING_TEXTURE.getGlTextureId();", liveTexture);

        assertTrue(initialManager >= 0);
        assertTrue(resolveTry > initialManager);
        assertTrue(resolve > resolveTry);
        assertTrue(resolveCatch > resolve);
        assertTrue(skipInvalid > resolveCatch);
        assertTrue(ensureLoaded > initialManager);
        assertTrue(lambda > ensureLoaded);
        assertTrue(reloadComment > lambda);
        assertTrue(liveManager > reloadComment);
        assertTrue(liveTexture > liveManager);
        assertTrue(missingTexture > liveTexture);
    }

    @Test
    public void malformedCustomResourceTextureLocationsAreSkippedBeforeBinding() throws Exception {
        String source = source();
        String body = methodBody(source, "private TextureBinding buildResourceBinding(CustomTextureData.ResourceData resourceData)");

        int resolveTry = body.indexOf("try {");
        int resolve = body.indexOf("reference = resolveResourceTextureReference(resourceData);", resolveTry);
        int resolveCatch = body.indexOf("catch (RuntimeException exception)", resolve);
        int warn = body.indexOf("Failed to resolve custom resource texture", resolveCatch);
        int skip = body.indexOf("return null;", warn);
        int ensureLoaded = body.indexOf("ensureTextureLoaded(textureManager, reference.location);", skip);

        assertTrue(resolveTry >= 0);
        assertTrue(resolve > resolveTry);
        assertTrue(resolveCatch > resolve);
        assertTrue(warn > resolveCatch);
        assertTrue(skip > warn);
        assertTrue(ensureLoaded > skip);
    }

    @Test
    public void eagerResourceTextureLoadFailuresDoNotAbortBindingCreation() throws Exception {
        String source = source();
        String body = methodBody(source, "private void ensureTextureLoaded(TextureManager textureManager, ResourceLocation location)");

        int existingCheck = body.indexOf("if (textureManager.getTexture(location) != null)");
        int tryBlock = body.indexOf("try {", existingCheck);
        int load = body.indexOf("textureManager.loadTexture(location, new SimpleTexture(location));", tryBlock);
        int catchBlock = body.indexOf("catch (RuntimeException | ZipError exception)", load);
        int warn = body.indexOf("Failed to eagerly load custom resource texture", catchBlock);

        assertTrue(existingCheck >= 0);
        assertTrue(tryBlock > existingCheck);
        assertTrue(load > tryBlock);
        assertTrue(catchBlock > load);
        assertTrue(warn > catchBlock);
    }

    @Test
    public void pbrResourceTextureBindingResolvesHolderFromLiveBaseTextureInsideSupplier() throws Exception {
        String source = source();
        String body = methodBody(source, "private TextureBinding buildResourceBinding(CustomTextureData.ResourceData resourceData)");

        int lambda = body.indexOf("return TextureBinding.texture2D(() -> {");
        int baseTextureId = body.indexOf("int baseTextureId = texture.getGlTextureId();", lambda);
        int regularReturn = body.indexOf("return baseTextureId;", baseTextureId);
        int pbrHolder = body.indexOf("PBRTextureManager.INSTANCE.getOrLoadHolder(baseTextureId);", regularReturn);
        int textureFormat = body.indexOf("TextureFormat textureFormat = TextureFormatLoader.getFormat();", pbrHolder);
        int pbrParameters = body.indexOf("textureFormat.setupTextureParameters(reference.pbrType, pbrTextureId);", textureFormat);

        assertTrue(lambda >= 0);
        assertFalse(body.substring(0, lambda).contains("PBRTextureManager.INSTANCE.getOrLoadHolder("));
        assertTrue(baseTextureId > lambda);
        assertTrue(regularReturn > baseTextureId);
        assertTrue(pbrHolder > regularReturn);
        assertTrue(textureFormat > pbrHolder);
        assertTrue(pbrParameters > textureFormat);
    }

    @Test
    public void stageCustomTextureSamplerNamesPreserveShaderPackCase() throws Exception {
        EnumMap<TextureStage, Map<String, CustomTextureData>> textureData = new EnumMap<>(TextureStage.class);
        Map<String, CustomTextureData> compositeTextures = new HashMap<>();
        compositeTextures.put("textureAtlas", new CustomTextureData.LightmapMarker());
        textureData.put(TextureStage.COMPOSITE_AND_FINAL, compositeTextures);

        ShaderPack pack = ShaderPack.of(
            "case-test",
            null,
            null,
            null,
            null,
            path -> null,
            textureData,
            new CustomTextureData.LightmapMarker(),
            null,
            null,
            null);
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(pack);

        try {
            manager.initialize();
            Map<String, TextureBinding> bindings = stageBindings(manager).get(TextureStage.COMPOSITE_AND_FINAL);

            assertTrue(bindings.containsKey("textureAtlas"));
            assertFalse(bindings.containsKey("textureatlas"));
        } finally {
            manager.destroy();
        }
    }

    @Test
    public void destroyUnregistersOwnedGlobalNoiseSamplerAliases() throws Exception {
        TextureBindingRegistry.clear();

        ShaderPack pack = ShaderPack.of(
            "noise-lifecycle-test",
            null,
            null,
            null,
            null,
            path -> null,
            new EnumMap<>(TextureStage.class),
            new CustomTextureData.LightmapMarker(),
            null,
            null,
            null);
        CustomTextureManager manager = CustomTextureManager.fromShaderPack(pack);
        TextureBinding newerNoisetexBinding = TextureBinding.texture2D(() -> 91);

        try {
            manager.initialize();
            manager.applyGlobalOverrides();
            TextureBinding ownedNoiseBinding = noiseBinding(manager);

            assertSame(ownedNoiseBinding, TextureBindingRegistry.resolve("oculus_noise"));
            assertSame(ownedNoiseBinding, TextureBindingRegistry.resolve("custom_noise"));
            assertSame(ownedNoiseBinding, TextureBindingRegistry.resolve("noise_texture"));
            assertSame(ownedNoiseBinding, TextureBindingRegistry.resolve("noisetex"));

            TextureBindingRegistry.register("noisetex", newerNoisetexBinding);

            manager.destroy();

            assertSame(TextureBinding.unbound(), TextureBindingRegistry.resolve("oculus_noise"));
            assertSame(TextureBinding.unbound(), TextureBindingRegistry.resolve("custom_noise"));
            assertSame(TextureBinding.unbound(), TextureBindingRegistry.resolve("noise_texture"));
            assertSame(newerNoisetexBinding, TextureBindingRegistry.resolve("noisetex"));
        } finally {
            manager.destroy();
            TextureBindingRegistry.clear();
        }
    }

    @Test
    public void destroyClearsOwnedTextureStateEvenWhenDeleteFailsAtSourceLevel() throws Exception {
        String source = source();
        String destroyBody = methodBody(source, "public void destroy()");
        String unregisterBody = methodBody(source, "private Throwable unregisterNoiseSamplers(Throwable failure)");
        String deleteBody = methodBody(source,
            "private static void deleteOwnedTexture(int textureId, String description)");
        String collectBody = methodBody(source,
            "private static Throwable collectFailure(Throwable failure, Throwable exception)");
        String suppressBody = methodBody(source,
            "private static void suppressFailure(Throwable failure, Throwable exception)");
        String rethrowBody = methodBody(source, "private static void rethrowFailure(Throwable failure)");

        int failureInit = destroyBody.indexOf("Throwable failure = null;");
        int tryBlock = destroyBody.indexOf("try {", failureInit);
        int unregister = destroyBody.indexOf("failure = unregisterNoiseSamplers(failure);", tryBlock);
        int deleteTexture = destroyBody.indexOf("deleteOwnedTexture(textureId, \"custom texture\");", unregister);
        int finallyBlock = destroyBody.indexOf("} finally {", deleteTexture);
        int clearOwned = destroyBody.indexOf("ownedTextureIds.clear();", finallyBlock);
        int clearStages = destroyBody.indexOf("stageBindings.clear();", clearOwned);
        int clearNoise = destroyBody.indexOf("noiseBinding = null;", clearStages);
        int clearInitialized = destroyBody.indexOf("initialized = false;", clearNoise);
        int rethrow = destroyBody.indexOf("rethrowFailure(failure);", clearInitialized);

        int nullGuard = unregisterBody.indexOf("if (noiseBinding == null)");
        int nullReturn = unregisterBody.indexOf("return failure;", nullGuard);
        int aliasLoop = unregisterBody.indexOf("for (String alias : NOISE_SAMPLER_ALIASES)", nullReturn);
        int unregisterTry = unregisterBody.indexOf("try {", aliasLoop);
        int unregisterAlias = unregisterBody.indexOf("TextureBindingRegistry.unregister(alias, noiseBinding);",
            unregisterTry);
        int unregisterCatch = unregisterBody.indexOf("catch (RuntimeException | Error exception)", unregisterAlias);
        int collectFailure = unregisterBody.indexOf("failure = collectFailure(failure, exception);", unregisterCatch);
        int returnFailure = unregisterBody.indexOf("return failure;", collectFailure);

        int glDelete = deleteBody.indexOf("GL11.glDeleteTextures(textureId);");
        int catchBlock = deleteBody.indexOf("catch (RuntimeException | Error exception)", glDelete);
        int deleteFinally = deleteBody.indexOf("} finally {", catchBlock);
        int trackerNotify = deleteBody.indexOf("TextureLifecycleTracker.onDeleteTexture(textureId);", deleteFinally);

        assertTrue(failureInit >= 0);
        assertTrue(tryBlock > failureInit);
        assertTrue(unregister > tryBlock);
        assertTrue(deleteTexture > unregister);
        assertTrue(finallyBlock > deleteTexture);
        assertTrue(clearOwned > finallyBlock);
        assertTrue(clearStages > clearOwned);
        assertTrue(clearNoise > clearStages);
        assertTrue(clearInitialized > clearNoise);
        assertTrue(rethrow > clearInitialized);
        assertTrue(nullGuard >= 0);
        assertTrue(nullReturn > nullGuard);
        assertTrue(aliasLoop > nullReturn);
        assertTrue(unregisterTry > aliasLoop);
        assertTrue(unregisterAlias > unregisterTry);
        assertTrue(unregisterCatch > unregisterAlias);
        assertTrue(collectFailure > unregisterCatch);
        assertTrue(returnFailure > collectFailure);
        assertTrue(deleteBody.contains("if (textureId <= 0)"));
        assertTrue(glDelete >= 0);
        assertTrue(catchBlock > glDelete);
        assertTrue(deleteFinally > catchBlock);
        assertTrue(trackerNotify > deleteFinally);
        assertTrue(collectBody.contains("suppressFailure(failure, exception);"));
        assertTrue(suppressBody.contains("if (failure != exception)"));
        assertTrue(suppressBody.contains("failure.addSuppressed(exception);"));
        assertTrue(rethrowBody.contains("throw (RuntimeException) failure;"));
        assertTrue(rethrowBody.contains("throw (Error) failure;"));
    }

    @SuppressWarnings("unchecked")
    private static EnumMap<TextureStage, Map<String, TextureBinding>> stageBindings(CustomTextureManager manager)
        throws ReflectiveOperationException {
        Field field = CustomTextureManager.class.getDeclaredField("stageBindings");
        field.setAccessible(true);
        return (EnumMap<TextureStage, Map<String, TextureBinding>>) field.get(manager);
    }

    private static TextureBinding noiseBinding(CustomTextureManager manager) throws ReflectiveOperationException {
        Field field = CustomTextureManager.class.getDeclaredField("noiseBinding");
        field.setAccessible(true);
        return (TextureBinding) field.get(manager);
    }

    private static void markInitialized(CustomTextureManager manager) throws ReflectiveOperationException {
        Field field = CustomTextureManager.class.getDeclaredField("initialized");
        field.setAccessible(true);
        field.setBoolean(manager, true);
    }

    private static ProgramSamplers.Builder programSamplersBuilder(String programName, String... activeUniforms)
        throws ReflectiveOperationException {
        return programSamplersBuilderWithReserved(programName, Collections.emptySet(), activeUniforms);
    }

    private static ProgramSamplers.Builder programSamplersBuilderWithReserved(String programName,
                                                                              Set<Integer> reservedTextureUnits,
                                                                              String... activeUniforms)
        throws ReflectiveOperationException {
        Set<String> active = new HashSet<>(Arrays.asList(activeUniforms));
        ToIntBiFunction<Integer, String> uniformLocationResolver = (programId, uniformName) ->
            active.contains(uniformName) ? activeUniformLocation(activeUniforms, uniformName) : -1;
        Method method = ProgramSamplers.class.getDeclaredMethod("builder",
            String.class,
            int.class,
            SamplerOverrideMap.class,
            Set.class,
            IntSupplier.class,
            ToIntBiFunction.class);
        method.setAccessible(true);
        return (ProgramSamplers.Builder) method.invoke(null,
            programName,
            7,
            SamplerOverrideMap.empty(),
            reservedTextureUnits,
            (IntSupplier) () -> 32,
            uniformLocationResolver);
    }

    private static int activeUniformLocation(String[] activeUniforms, String uniformName) {
        for (int index = 0; index < activeUniforms.length; index++) {
            if (activeUniforms[index].equals(uniformName)) {
                return index + 1;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> samplerBindings(ProgramSamplers samplers) throws ReflectiveOperationException {
        Field bindingsField = ProgramSamplers.class.getDeclaredField("bindings");
        bindingsField.setAccessible(true);
        return (List<Object>) bindingsField.get(samplers);
    }

    private static Object firstSamplerBinding(ProgramSamplers samplers) throws ReflectiveOperationException {
        List<Object> bindings = samplerBindings(samplers);
        assertEquals(1, bindings.size());
        return bindings.get(0);
    }

    private static void assertSharedCustomBinding(List<Object> bindings, TextureBinding customBinding,
                                                  String uniformName, int unit) throws ReflectiveOperationException {
        for (Object binding : bindings) {
            if (uniformName.equals(fieldValue(binding, "uniformName"))) {
                assertEquals(unit, fieldValue(binding, "unit"));
                assertSame(customBinding, fieldValue(binding, "binding"));
                return;
            }
        }
        throw new AssertionError("Missing sampler binding for " + uniformName);
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Throwable collectFailure(Throwable failure, Throwable exception) throws ReflectiveOperationException {
        Method method = CustomTextureManager.class.getDeclaredMethod("collectFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        return (Throwable) method.invoke(null, failure, exception);
    }

    private static String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/texture/CustomTextureManager.java")), StandardCharsets.UTF_8);
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

    private static byte[] createReferenceNoisePixels(int resolution) {
        byte[] pixels = new byte[resolution * resolution * 4];
        Random random = new Random(0L);

        for (int x = 0; x < resolution; x++) {
            for (int y = 0; y < resolution; y++) {
                int color = random.nextInt() | 0xFF000000;
                int offset = ((y * resolution) + x) * 4;
                pixels[offset] = (byte) (color & 0xFF);
                pixels[offset + 1] = (byte) ((color >>> 8) & 0xFF);
                pixels[offset + 2] = (byte) ((color >>> 16) & 0xFF);
                pixels[offset + 3] = (byte) 0xFF;
            }
        }

        return pixels;
    }
}
