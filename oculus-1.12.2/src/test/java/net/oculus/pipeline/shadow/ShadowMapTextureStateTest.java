package net.oculus.pipeline.shadow;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.util.Config;
import org.junit.Test;
import org.lwjgl.opengl.GL11;

public class ShadowMapTextureStateTest {
    @Test
    public void depthTextureSwizzleMatchesIrisCompatibilityWorkaround() {
        assertArrayEquals(
            new int[] { GL11.GL_RED, GL11.GL_RED, GL11.GL_RED, GL11.GL_ONE },
            ShadowMap.depthSwizzleRgba());
    }

    @Test
    public void shadowTexturesUseNonMipmapMinFilterUntilMipmapGeneration() {
        assertArrayEquals(
            new int[] { GL11.GL_LINEAR, GL11.GL_NEAREST },
            new int[] { ShadowMap.baseMinFilter(false), ShadowMap.baseMinFilter(true) });
    }

    @Test
    public void shadowTexturesSwitchToMipmapMinFilterAfterMipmapGeneration() {
        assertArrayEquals(
            new int[] { GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_NEAREST_MIPMAP_NEAREST },
            new int[] { ShadowMap.mipmapMinFilter(false), ShadowMap.mipmapMinFilter(true) });
    }

    @Test
    public void shadowTextureAllocationUsesReferenceStyleBindWrappers() throws Exception {
        String renderSystem = read("src/main/java/net/oculus/gl/OculusRenderSystem.java");
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String depthAllocation = source.substring(
            source.indexOf("private static int createDepthTexture"),
            source.indexOf("private static int createColorTexture"));
        String colorAllocation = source.substring(
            source.indexOf("private static int createColorTexture"),
            source.indexOf("static int[] depthSwizzleRgba"));
        String mipmapGeneration = source.substring(
            source.indexOf("private static void generateMipmap"),
            source.indexOf("private static void deleteTexture"));

        assertTrue(renderSystem.contains("public static void texImage2D(int texture, int target, int level"));
        assertTrue(renderSystem.contains("public static void texParameter(int texture, int target, int pname, IntBuffer params)"));
        assertTrue(renderSystem.contains("TextureLifecycleTracker.onTexImage2D(texture, target, level, internalFormat"));

        assertTrue(depthAllocation.contains("OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(depthAllocation.contains("DepthBufferFormat format = DepthBufferFormat.DEPTH;"));
        assertTrue(depthAllocation.contains("format.getGlInternalFormat()"));
        assertTrue(depthAllocation.contains("format.getGlPixelFormat(), format.getGlPixelType(), null"));
        assertTrue(depthAllocation.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(depthAllocation.contains("configureDepthSwizzle(texture);"));
        assertTrue(source.contains("OculusRenderSystem.texParameter(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(depthAllocation.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        assertFalse(depthAllocation.contains("GlStateManager.bindTexture(0);"));
        assertFalse(depthAllocation.contains("GL14.GL_DEPTH_COMPONENT24"));
        assertFalse(depthAllocation.contains("GL11.GL_FLOAT"));
        assertFalse(depthAllocation.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(depthAllocation.contains("GL11.glTexImage2D"));
        assertFalse(depthAllocation.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));
        assertFalse(depthAllocation.contains("TextureLifecycleTracker.onTexImage2D"));

        assertTrue(colorAllocation.contains("OculusRenderSystem.texImage2D("));
        assertTrue(colorAllocation.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(colorAllocation.contains("format.getPixelFormat().getGlFormat()"));
        assertTrue(colorAllocation.contains("PixelType.UNSIGNED_BYTE.getGlFormat()"));
        assertTrue(colorAllocation.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        assertFalse(colorAllocation.contains("GlStateManager.bindTexture(0);"));
        assertFalse(colorAllocation.contains("format.getPixelFormat().isInteger() ? GL11.GL_INT"));
        assertFalse(colorAllocation.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(colorAllocation.contains("GL11.glTexImage2D"));
        assertFalse(colorAllocation.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));
        assertFalse(colorAllocation.contains("TextureLifecycleTracker.onTexImage2D"));

        assertTrue(mipmapGeneration.contains("OculusRenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);"));
        assertTrue(mipmapGeneration.contains("OculusRenderSystem.texParameteri("));
        assertTrue(mipmapGeneration.contains("GL11.GL_TEXTURE_MIN_FILTER"));
        assertFalse(mipmapGeneration.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)"));
        assertFalse(mipmapGeneration.contains("GL30.glGenerateMipmap"));
        assertFalse(mipmapGeneration.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));
    }

    @Test
    public void shadowColorTargetsTrackMainAltAndFlippedReadTexture() throws Exception {
        ShaderProperties properties = new ShaderProperties("shadow.enabled=false\n");
        ShadowMap shadowMap = new ShadowMap(new PackDirectives(properties), properties, Config.get(), false);

        setTextureIds(shadowMap, "colorTextures", 11, 12);
        setTextureIds(shadowMap, "altColorTextures", 21, 22);

        assertFalse(shadowMap.isColorTextureFlipped(0));
        assertEquals(11, shadowMap.getMainColorTexture(0));
        assertEquals(21, shadowMap.getAltColorTexture(0));
        assertEquals(11, shadowMap.getColorTexture(0));

        shadowMap.flipColorTexture(0);

        assertTrue(shadowMap.isColorTextureFlipped(0));
        assertEquals(21, shadowMap.getColorTexture(0));

        shadowMap.flipColorTexture(0);

        assertFalse(shadowMap.isColorTextureFlipped(0));
        assertEquals(11, shadowMap.getColorTexture(0));
    }

    @Test
    public void shadowTargetSnapshotAndClearListMirrorReferenceSurface() throws Exception {
        ShaderProperties properties = new ShaderProperties("shadow.enabled=false\n");
        ShadowMap shadowMap = new ShadowMap(new PackDirectives(properties), properties, Config.get(), false);

        assertTrue(shadowMap.snapshot().isEmpty());

        shadowMap.flipColorTexture(1);
        assertTrue(shadowMap.snapshot().contains(1));
        assertFalse(shadowMap.snapshot().contains(0));

        List<Integer> buffersToBeCleared = shadowMap.getBuffersToBeCleared();
        assertEquals(2, buffersToBeCleared.size());
        assertEquals(Integer.valueOf(0), buffersToBeCleared.get(0));
        assertEquals(Integer.valueOf(1), buffersToBeCleared.get(1));

        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String constructor = source.substring(
            source.indexOf("this.buffersToBeCleared = new ArrayList<>();"),
            source.indexOf("if (enabled && resolution > 0)"));
        String snapshot = source.substring(
            source.indexOf("public ImmutableSet<Integer> snapshot()"),
            source.indexOf("public List<Integer> getBuffersToBeCleared()"));
        String getBuffersToBeCleared = source.substring(
            source.indexOf("public List<Integer> getBuffersToBeCleared()"),
            source.indexOf("private void allocateTextures()"));

        assertTrue("ShadowMap must record clear-enabled shadow color buffers from directives",
            constructor.contains("settings.shouldClear()"));
        assertTrue(constructor.contains("buffersToBeCleared.add(i);"));
        assertTrue("ShadowMap snapshot must expose currently flipped shadow color targets",
            snapshot.contains("if (flippedColorTextures[i])"));
        assertTrue(snapshot.contains("builder.add(i);"));
        assertTrue("ShadowMap clear-list accessor must not expose mutable owner state",
            getBuffersToBeCleared.contains("return Collections.unmodifiableList(buffersToBeCleared);"));
    }

    @Test
    public void shadowColorAllocationOwnsMainAndAltTexturesLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String allocate = source.substring(
            source.indexOf("private void allocateTextures()"),
            source.indexOf("private void registerBindings()"));
        String destroy = source.substring(
            source.indexOf("public void destroy()"),
            source.indexOf("private void allocateTextures()"));

        int allocateMain = allocate.indexOf("colorTextures[i] = createColorTexture(resolution, settings.getFormat(), settings);");
        int allocateAlt = allocate.indexOf("altColorTextures[i] = createColorTexture(resolution, settings.getFormat(), settings);",
            allocateMain);
        int destroyMain = destroy.indexOf("failure = deleteTexture(failure, colorTextures[i]);");
        int destroyAlt = destroy.indexOf("failure = deleteTexture(failure, altColorTextures[i]);", destroyMain);
        int resetFlip = destroy.indexOf("flippedColorTextures[i] = false;", destroyAlt);

        assertTrue("ShadowMap must allocate a main shadow color texture", allocateMain >= 0);
        assertTrue("ShadowMap must allocate a paired alternate shadow color texture", allocateAlt > allocateMain);
        assertTrue("ShadowMap must delete the main shadow color texture", destroyMain >= 0);
        assertTrue("ShadowMap must delete the alternate shadow color texture", destroyAlt > destroyMain);
        assertTrue("ShadowMap must reset shadow color flip state during destroy", resetFlip > destroyAlt);
    }

    @Test
    public void shadowMapDestroyAggregatesCleanupFailuresBeforeRethrow() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String destroy = source.substring(
            source.indexOf("public void destroy()"),
            source.indexOf("private void allocateTextures()"));
        String helpers = source.substring(source.indexOf("private static Throwable deleteTexture"));

        int failureLocal = destroy.indexOf("Throwable failure = null;");
        int unregister = destroy.indexOf("failure = unregisterBindings(failure);", failureLocal);
        int destroyOwnedFramebuffers = destroy.indexOf("failure = destroyOwnedFramebuffers(failure);",
            unregister);
        int depthLoop = destroy.indexOf("for (int i = 0; i < depthTextures.length; i++)", destroyOwnedFramebuffers);
        int deleteDepth = destroy.indexOf("failure = deleteTexture(failure, depthTextures[i]);", depthLoop);
        int colorLoop = destroy.indexOf("for (int i = 0; i < colorTextures.length; i++)", deleteDepth);
        int deleteColor = destroy.indexOf("failure = deleteTexture(failure, colorTextures[i]);", colorLoop);
        int deleteAlt = destroy.indexOf("failure = deleteTexture(failure, altColorTextures[i]);", deleteColor);
        int rethrow = destroy.indexOf("rethrowCleanupFailure(failure);", deleteAlt);
        int finallyBlock = destroy.indexOf("} finally {", rethrow);
        int clearBindings = destroy.indexOf("clearRegisteredBindings();", finallyBlock);
        int clearOwnedFramebuffers = destroy.indexOf("ownedFramebuffers.clear();", clearBindings);
        int clearDepthFramebuffer = destroy.indexOf("depthSourceFramebuffer = null;", clearOwnedFramebuffers);
        int clearDepthTexture = destroy.indexOf("depthTextures[i] = 0;", clearDepthFramebuffer);
        int clearColorTexture = destroy.indexOf("colorTextures[i] = 0;", clearDepthTexture);
        int resetFlip = destroy.indexOf("flippedColorTextures[i] = false;", clearColorTexture);
        int ownedLoop = helpers.indexOf("for (GlFramebuffer framebuffer : new ArrayList<>(ownedFramebuffers))");
        int ownedDestroy = helpers.indexOf("failure = destroyFramebuffer(failure, framebuffer);", ownedLoop);
        int destroyHelper = helpers.indexOf("private Throwable destroyFramebuffer(Throwable failure, GlFramebuffer framebuffer)");
        int wrapperDestroy = helpers.indexOf("framebuffer.destroy();", destroyHelper);
        int recordDestroyFailure = helpers.indexOf("failure = addCleanupFailure(failure, exception);", wrapperDestroy);
        int destroyFinally = helpers.indexOf("finally {", recordDestroyFailure);
        int removeOwned = helpers.indexOf("ownedFramebuffers.remove(framebuffer);", destroyFinally);

        assertTrue(failureLocal >= 0);
        assertTrue("ShadowMap destroy must try unregistering bindings before deleting GL objects",
            unregister > failureLocal);
        assertTrue("ShadowMap destroy must continue through framebuffer and texture deletion paths",
            destroyOwnedFramebuffers > unregister
                && deleteDepth > depthLoop && deleteColor > colorLoop && deleteAlt > deleteColor);
        assertTrue("ShadowMap destroy must rethrow only after all cleanup attempts are recorded",
            rethrow > deleteAlt);
        assertTrue("ShadowMap destroy must clear owned Java state from a finally path even when cleanup rethrows",
            finallyBlock > rethrow && clearBindings > finallyBlock && clearOwnedFramebuffers > clearBindings
                && clearDepthFramebuffer > clearOwnedFramebuffers
                && clearDepthTexture > clearDepthFramebuffer && clearColorTexture > clearDepthTexture
                && resetFlip > clearColorTexture);
        assertTrue("ShadowMap must destroy all tracked framebuffers through the owner list",
            ownedLoop >= 0 && ownedDestroy > ownedLoop);
        assertTrue("ShadowMap.destroyFramebuffer must clear stale ownership even when wrapper destroy reports failure",
            wrapperDestroy > destroyHelper && recordDestroyFailure > wrapperDestroy
                && destroyFinally > recordDestroyFailure && removeOwned > destroyFinally);
        assertTrue(helpers.contains("failure.addSuppressed(exception);"));
        assertTrue(helpers.contains("throw (RuntimeException) failure;"));
        assertTrue(helpers.contains("throw (Error) failure;"));
    }

    @Test
    public void destroyedShadowMapFailsClearlyOnRuntimeEntryPoints() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String destroy = methodBody(source, "public void destroy()");
        String requireLive = methodBody(source, "private void requireLive");
        String isEnabled = methodBody(source, "public boolean isEnabled()");
        String consumeFullClear = methodBody(source, "public boolean consumeFullClearRequired()");
        String getDepthTexture = methodBody(source, "public int getDepthTexture()");
        String getColorTexture = methodBody(source, "public int getColorTexture(int index)");
        String flipColorTexture = methodBody(source, "public void flipColorTexture(int index)");
        String applySamplerBindings = methodBody(source, "public boolean applySamplerBindings");
        String snapshot = methodBody(source, "public ImmutableSet<Integer> snapshot()");
        String clearBuffers = methodBody(source, "public List<Integer> getBuffersToBeCleared()");
        String createShadowFramebuffer = methodBody(source, "public GlFramebuffer createShadowFramebuffer");
        String getDepthSourceFramebuffer = methodBody(source, "public GlFramebuffer getDepthSourceFramebuffer()");
        String copyDepth = methodBody(source, "public void copyDepthToNoTranslucents()");
        String generateMipmaps = methodBody(source, "public void generateMipmaps()");

        int destroyedField = source.indexOf("private boolean destroyed;");
        int destroyGuard = destroy.indexOf("if (destroyed)");
        int destroyReturn = destroy.indexOf("return;", destroyGuard);
        int tryBlock = destroy.indexOf("try {", destroyReturn);
        int finallyBlock = destroy.indexOf("} finally {", tryBlock);
        int clearFull = destroy.indexOf("fullClearRequired = false;", finallyBlock);
        int clearDirty = destroy.indexOf("translucentDepthDirty = false;", clearFull);
        int markDestroyed = destroy.indexOf("destroyed = true;", clearDirty);
        int requireGuard = requireLive.indexOf("if (destroyed)");
        int failClearly = requireLive.indexOf(
            "throw new IllegalStateException(\"Cannot \" + operation + \" after shadow render targets were destroyed\");",
            requireGuard);
        int copyGuard = copyDepth.indexOf("requireLive(\"copy no-translucents shadow depth\");");
        int copyDisabledReturn = copyDepth.indexOf("if (!enabled)", copyGuard);
        int mipmapGuard = generateMipmaps.indexOf("requireLive(\"generate shadow mipmaps\");");
        int mipmapDisabledReturn = generateMipmaps.indexOf("if (!enabled)", mipmapGuard);

        assertTrue("ShadowMap must remember completed teardown", destroyedField >= 0);
        assertTrue("ShadowMap destroy must be idempotent after the first teardown",
            destroyGuard >= 0 && destroyReturn > destroyGuard && destroyReturn < tryBlock);
        assertTrue("ShadowMap destroy must mark lifecycle state from finally after owner state is cleared",
            finallyBlock > tryBlock && clearFull > finallyBlock && clearDirty > clearFull && markDestroyed > clearDirty);
        assertTrue("Destroyed shadow target access must fail with a precise lifecycle error",
            requireGuard >= 0 && failClearly > requireGuard);

        assertTrue(isEnabled.contains("requireLive(\"check shadow target allocation\");"));
        assertTrue(consumeFullClear.contains("requireLive(\"consume shadow full-clear state\");"));
        assertTrue(getDepthTexture.contains("requireLive(\"read shadow depth texture\");"));
        assertTrue(getColorTexture.contains("requireLive(\"read shadow color texture\");"));
        assertTrue(flipColorTexture.contains("requireLive(\"flip shadow color texture\");"));
        assertTrue(applySamplerBindings.contains("requireLive(\"bind shadow samplers\");"));
        assertTrue(snapshot.contains("requireLive(\"snapshot shadow color flip state\");"));
        assertTrue(clearBuffers.contains("requireLive(\"read shadow clear buffers\");"));
        assertTrue(createShadowFramebuffer.contains("requireLive(\"create shadow render framebuffer\");"));
        assertTrue(getDepthSourceFramebuffer.contains("requireLive(\"read shadow depth source framebuffer\");"));
        assertTrue("Destroyed shadow depth copies must fail before the disabled-target fast path",
            copyGuard >= 0 && copyDisabledReturn > copyGuard);
        assertTrue("Destroyed shadow mipmap generation must fail before the disabled-target fast path",
            mipmapGuard >= 0 && mipmapDisabledReturn > mipmapGuard);
        assertFalse("Destroyed shadow target operations must not silently no-op",
            copyDepth.contains("if (destroyed) {\n            return"));
        assertFalse(generateMipmaps.contains("if (destroyed) {\n            return"));
    }

    @Test
    public void destroyedDisabledShadowMapRuntimeAccessThrowsBeforeDisabledFastPaths() {
        ShaderProperties properties = new ShaderProperties("shadow.enabled=false\n");
        ShadowMap shadowMap = new ShadowMap(new PackDirectives(properties), properties, Config.get(), false);

        shadowMap.destroy();

        assertDestroyedShadowMapThrows(() -> shadowMap.isEnabled(), "check shadow target allocation");
        assertDestroyedShadowMapThrows(() -> shadowMap.copyDepthToNoTranslucents(),
            "copy no-translucents shadow depth");
        assertDestroyedShadowMapThrows(() -> shadowMap.generateMipmaps(), "generate shadow mipmaps");
        shadowMap.destroy();
    }

    @Test
    public void shadowMapConstructorCleansPartialAllocationBeforeRethrow() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String constructor = source.substring(
            source.indexOf("if (enabled && resolution > 0)"),
            source.indexOf("public static boolean usesShadowTargets"));

        int enabledGuard = constructor.indexOf("if (enabled && resolution > 0)");
        int tryBlock = constructor.indexOf("try {", enabledGuard);
        int allocateTextures = constructor.indexOf("allocateTextures();", tryBlock);
        int allocateFramebuffers = constructor.indexOf("allocateCopyFramebuffers();", allocateTextures);
        int registerBindings = constructor.indexOf("registerBindings();", allocateFramebuffers);
        int catchBlock = constructor.indexOf("catch (RuntimeException | Error exception)", registerBindings);
        int destroy = constructor.indexOf("destroy();", catchBlock);
        int cleanupCatch = constructor.indexOf("catch (RuntimeException | Error cleanupException)", destroy);
        int suppressed = constructor.indexOf("exception.addSuppressed(cleanupException);", cleanupCatch);
        int rethrow = constructor.indexOf("throw exception;", suppressed);

        assertTrue("ShadowMap must guard texture/framebuffer setup after deciding targets are enabled",
            tryBlock > enabledGuard);
        assertTrue("ShadowMap must allocate textures before framebuffer wrappers",
            allocateTextures > tryBlock && allocateFramebuffers > allocateTextures);
        assertTrue("ShadowMap must only register global aliases after all GL objects are allocated",
            registerBindings > allocateFramebuffers);
        assertTrue("ShadowMap must destroy partially allocated objects if setup fails", destroy > catchBlock);
        assertTrue("ShadowMap must preserve the original setup failure when cleanup also fails",
            cleanupCatch > destroy && suppressed > cleanupCatch);
        assertTrue("ShadowMap must rethrow the original setup failure", rethrow > destroy);
    }

    @Test
    public void shadowSamplerUsersFailClearlyWhenShadowTargetsAreUnavailable() throws Exception {
        String shadowMap = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String shaderLoader = read("src/main/java/net/oculus/shader/ShaderLoader.java");
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String requireTargets = shadowMap.substring(
            shadowMap.indexOf("public static ShadowMap requireShadowTargets"),
            shadowMap.indexOf("public boolean isFullClearRequired"));

        assertTrue(requireTargets.contains(
            "String unsupportedResource = ShadowSamplerBindings.findUnsupportedShadowResource(query);"));
        assertTrue(requireTargets.contains("if (unsupportedResource != null)"));
        assertTrue(requireTargets.contains(
            "references unsupported shadow resource \" + unsupportedResource"));
        assertTrue(requireTargets.contains(
            "Oculus 1.16.5 exposes two shadow depth targets and two shadow color targets"));
        assertTrue(requireTargets.contains("if (!ShadowSamplerBindings.usesShadowTargets(query))"));
        assertTrue(requireTargets.contains("return null;"));
        assertTrue(requireTargets.contains(
            "ShadowMap shadowMap = shadowMapSupplier == null ? null : shadowMapSupplier.get();"));
        assertTrue(requireTargets.contains("if (shadowMap == null || !shadowMap.isEnabled())"));
        assertTrue(requireTargets.contains("throw new ProgramLoadException(\"Program \" + safeProgramName"));
        assertTrue(requireTargets.contains(
            "requires shadow samplers or images, but no allocated shadow render targets are available"));

        assertTrue(shaderLoader.contains("applyShadowSamplerBindings(builder, source.getName());"));
        assertTrue(shaderLoader.contains("applyImageBindings(builder, source.getName());"));
        assertTrue(shaderLoader.contains(
            "ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);"));
        assertTrue(shaderLoader.contains("shadowMap.applySamplerBindings(builder, programName);"));
        assertFalse("World program shadow bindings must not silently skip a required missing shadow map",
            shaderLoader.contains("if (ShadowMap.usesShadowTargets(builder)) {\n"
                + "            ShadowMap shadowMap = shadowMapSupplier == null ? null : shadowMapSupplier.get();"));

        assertTrue(composite.contains("bindShadowTargetSamplerBindings(builder, source.getName());"));
        assertTrue(composite.contains(
            "ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);"));
        assertTrue(composite.contains("shadowMap.applySamplerBindings(builder, programName);"));
        assertFalse("Composite shadow bindings must not silently skip a required missing shadow map",
            composite.contains("if (ShadowMap.usesShadowTargets(builder)) {\n"
                + "            ShadowMap shadowMap = shadowMapSupplier == null ? null : shadowMapSupplier.get();"));

        assertTrue(finalPass.contains("bindShadowTargetSamplerBindings(builder, source.getName());"));
        assertTrue(finalPass.contains(
            "ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);"));
        assertTrue(finalPass.contains("shadowMap.applySamplerBindings(builder, programName);"));
        assertFalse("Final pass shadow bindings must not silently skip a required missing shadow map",
            finalPass.contains("if (ShadowMap.usesShadowTargets(builder)) {\n"
                + "            ShadowMap shadowMap = shadowMapSupplier == null ? null : shadowMapSupplier.get();"));
    }

    @Test
    public void directShadowSamplerBindingPathFailsUnsupportedResourcesBeforeBinding() throws Exception {
        String shadowMap = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String applySamplerBindings = methodBody(shadowMap, "public boolean applySamplerBindings");

        int builderNullGuard = applySamplerBindings.indexOf("if (builder == null)");
        int safeProgramName = applySamplerBindings.indexOf(
            "String safeProgramName = programName == null ? \"<unknown>\" : programName;",
            builderNullGuard);
        int query = applySamplerBindings.indexOf(
            "ShadowSamplerBindings.ResourceQuery query = createShadowResourceQuery(builder);",
            safeProgramName);
        int unsupportedLookup = applySamplerBindings.indexOf(
            "String unsupportedResource = ShadowSamplerBindings.findUnsupportedShadowResource(query);",
            query);
        int unsupportedGuard = applySamplerBindings.indexOf("if (unsupportedResource != null)", unsupportedLookup);
        int unsupportedProgramName = applySamplerBindings.indexOf(
            "throw new ProgramLoadException(\"Program \" + safeProgramName",
            unsupportedGuard);
        int unsupportedThrow = applySamplerBindings.indexOf(
            "references unsupported shadow resource \" + unsupportedResource",
            unsupportedProgramName);
        int usesShadowTargets = applySamplerBindings.indexOf(
            "if (!ShadowSamplerBindings.usesShadowTargets(query))",
            unsupportedThrow);
        int disabledGuard = applySamplerBindings.indexOf("if (!enabled)", usesShadowTargets);
        int disabledProgramName = applySamplerBindings.indexOf(
            "throw new ProgramLoadException(\"Program \" + safeProgramName",
            disabledGuard);
        int firstBinding = applySamplerBindings.indexOf("TextureBinding.texture2D", disabledGuard);

        assertTrue("Direct shadow binding must ignore the no-builder path before inspecting uniforms",
            builderNullGuard >= 0);
        assertTrue("Direct shadow binding errors must name the owning shader program",
            safeProgramName > builderNullGuard);
        assertTrue("Direct shadow binding must inspect active sampler/image resources",
            query > safeProgramName);
        assertTrue("Unsupported shadow resources must fail before binding any texture units",
            unsupportedLookup > query && unsupportedGuard > unsupportedLookup
                && unsupportedProgramName > unsupportedGuard && unsupportedThrow > unsupportedProgramName);
        assertTrue("Programs without active shadow resources should still skip direct shadow bindings",
            usesShadowTargets > unsupportedThrow);
        assertTrue("A disabled shadow target must fail before binding when active shadow resources are present",
            disabledGuard > usesShadowTargets && disabledProgramName > disabledGuard);
        assertTrue("Shadow texture bindings must only be built after all fail-fast checks",
            firstBinding > disabledProgramName);
    }

    @Test
    public void shadowDepthCopyFramebufferConstructionCleansPartialFramebufferBeforeRethrow() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String createDepthCopyFramebuffer = source.substring(
            source.indexOf("private GlFramebuffer createDepthCopyFramebuffer"),
            source.indexOf("private void registerBindings()"));

        int allocate = createDepthCopyFramebuffer.indexOf("GlFramebuffer framebuffer = createOwnedFramebuffer();");
        int tryBlock = createDepthCopyFramebuffer.indexOf("try {", allocate);
        int addDepth = createDepthCopyFramebuffer.indexOf("framebuffer.addDepthAttachment(depthTexture);", tryBlock);
        int addColor = createDepthCopyFramebuffer.indexOf("framebuffer.addColorAttachment(0, colorTextures[0]);", addDepth);
        int drawBuffers = createDepthCopyFramebuffer.indexOf("framebuffer.drawBuffers(logicalDrawBuffers(1));", addColor);
        int readBuffer = createDepthCopyFramebuffer.indexOf("framebuffer.readBuffer(0);", drawBuffers);
        int completeness = createDepthCopyFramebuffer.indexOf("if (!framebuffer.isComplete())", readBuffer);
        int fail = createDepthCopyFramebuffer.indexOf(
            "throw new IllegalStateException(\"Shadow depth copy framebuffer incomplete\");", completeness);
        int returnFramebuffer = createDepthCopyFramebuffer.indexOf("return framebuffer;", fail);
        int catchBlock = createDepthCopyFramebuffer.indexOf("catch (RuntimeException | Error exception)",
            returnFramebuffer);
        int destroy = createDepthCopyFramebuffer.indexOf("Throwable failure = destroyFramebuffer(null, framebuffer);",
            catchBlock);
        int suppress = createDepthCopyFramebuffer.indexOf("addSuppressedCleanupFailure(exception, failure);",
            destroy);
        int rethrow = createDepthCopyFramebuffer.indexOf("throw exception;", suppress);

        assertTrue("Shadow depth copy framebuffer must allocate through the owner list before guarded setup",
            allocate >= 0);
        assertTrue("Shadow depth copy framebuffer setup must be guarded", tryBlock > allocate);
        assertTrue(addDepth > tryBlock);
        assertTrue(addColor > addDepth);
        assertTrue("Shadow depth copy framebuffer must keep the reference color draw-buffer state",
            drawBuffers > addColor);
        assertTrue("Shadow depth copy framebuffer must keep read buffer 0 like reference color framebuffers",
            readBuffer > drawBuffers);
        assertTrue(completeness > readBuffer);
        assertTrue("Shadow depth copy framebuffer must fail fast before returning an incomplete framebuffer",
            fail > completeness && returnFramebuffer > fail);
        assertTrue("Shadow depth copy framebuffer must catch RuntimeException and Error setup failures",
            catchBlock > returnFramebuffer);
        assertTrue("Shadow depth copy framebuffer must destroy partial setup on any runtime or error failure",
            destroy > catchBlock);
        assertTrue("Shadow depth copy framebuffer cleanup failures must be suppressed onto setup failures",
            suppress > destroy);
        assertTrue("Shadow depth copy framebuffer must rethrow the original setup failure", rethrow > destroy);
    }

    @Test
    public void shadowFramebufferOwnershipRegistrationDestroysUnpublishedFramebufferOnFailure() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String createOwned = methodBody(source, "private GlFramebuffer createOwnedFramebuffer()");
        String createEmpty = methodBody(source, "private GlFramebuffer createEmptyFramebuffer()");
        String createColor = methodBody(source,
            "private GlFramebuffer createColorFramebuffer(Set<Integer> stageWritesToMain");
        String createDepthCopy = methodBody(source, "private GlFramebuffer createDepthCopyFramebuffer");

        int allocate = createOwned.indexOf("GlFramebuffer framebuffer = new GlFramebuffer();");
        int tryBlock = createOwned.indexOf("try {", allocate);
        int register = createOwned.indexOf("ownedFramebuffers.add(framebuffer);", tryBlock);
        int returnFramebuffer = createOwned.indexOf("return framebuffer;", register);
        int catchBlock = createOwned.indexOf("catch (RuntimeException | Error exception)", returnFramebuffer);
        int destroy = createOwned.indexOf("Throwable failure = destroyFramebuffer(null, framebuffer);", catchBlock);
        int suppress = createOwned.indexOf("addSuppressedCleanupFailure(exception, failure);", destroy);
        int rethrow = createOwned.indexOf("throw exception;", suppress);

        assertTrue("Shadow owned framebuffer allocation must create the GL object before guarded registration",
            allocate >= 0 && tryBlock > allocate);
        assertTrue("Shadow owned framebuffer registration must happen inside the guarded block",
            register > tryBlock && returnFramebuffer > register);
        assertTrue("Failed shadow framebuffer registration must destroy the unpublished GL framebuffer",
            catchBlock > returnFramebuffer && destroy > catchBlock);
        assertTrue("Shadow framebuffer registration cleanup failures must suppress onto the registration failure",
            suppress > destroy && rethrow > suppress);
        assertTrue("Shadow empty framebuffer setup must use guarded owned-framebuffer allocation",
            createEmpty.contains("GlFramebuffer framebuffer = createOwnedFramebuffer();"));
        assertTrue("Shadow color framebuffer setup must use guarded owned-framebuffer allocation",
            createColor.contains("GlFramebuffer framebuffer = createOwnedFramebuffer();"));
        assertTrue("Shadow depth-copy framebuffer setup must use guarded owned-framebuffer allocation",
            createDepthCopy.contains("GlFramebuffer framebuffer = createOwnedFramebuffer();"));
        assertFalse("Shadow framebuffer setup paths must not register raw framebuffers directly",
            createEmpty.contains("ownedFramebuffers.add(framebuffer);"));
        assertFalse("Shadow framebuffer setup paths must not register raw framebuffers directly",
            createColor.contains("ownedFramebuffers.add(framebuffer);"));
        assertFalse("Shadow framebuffer setup paths must not register raw framebuffers directly",
            createDepthCopy.contains("ownedFramebuffers.add(framebuffer);"));
    }

    @Test
    public void shadowClearPassFramebuffersAttachMainOrAltTargetsWithMainDepthLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String clearFramebuffers = source.substring(
            source.indexOf("public GlFramebuffer createFramebufferWritingToMain"),
            source.indexOf("private GlFramebuffer createDepthCopyFramebuffer"));

        int createMain = clearFramebuffers.indexOf("return createFullFramebuffer(false, drawBuffers);");
        int createAlt = clearFramebuffers.indexOf("return createFullFramebuffer(true, drawBuffers);", createMain);
        int requireAllocated = clearFramebuffers.indexOf("requireAllocatedShadowTargets(\"Shadow clear framebuffer\");",
            createAlt);
        int addMainWrites = clearFramebuffers.indexOf("stageWritesToMain.add(drawBuffer);", requireAllocated);
        int createWithDepth = clearFramebuffers.indexOf(
            "return createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);", addMainWrites);
        int addDepth = clearFramebuffers.indexOf("framebuffer.addDepthAttachment(requireValidShadowTexture(depthTextures[0],",
            createWithDepth);
        int textureSelection = clearFramebuffers.indexOf("int textureId = stageWritesToMain.contains(bufferIndex)",
            addDepth);
        int mainTexture = clearFramebuffers.indexOf("? colorTextures[bufferIndex]", textureSelection);
        int altTexture = clearFramebuffers.indexOf(": altColorTextures[bufferIndex];", mainTexture);
        int addColor = clearFramebuffers.indexOf("framebuffer.addColorAttachment(i, requireValidShadowTexture(textureId,",
            altTexture);
        int drawBuffers = clearFramebuffers.indexOf("framebuffer.drawBuffers(logicalDrawBuffers(drawBuffers.length));",
            addColor);
        int readBuffer = clearFramebuffers.indexOf("framebuffer.readBuffer(0);", drawBuffers);
        int completeness = clearFramebuffers.indexOf("if (!framebuffer.isComplete())", readBuffer);
        int destroyOnFailure = clearFramebuffers.indexOf("Throwable failure = destroyFramebuffer(null, framebuffer);",
            completeness);

        assertTrue("ShadowMap must expose source-style clear framebuffers for main targets", createMain >= 0);
        assertTrue("ShadowMap must expose source-style clear framebuffers for alternate targets", createAlt > createMain);
        assertTrue("Shadow clear framebuffers must fail clearly if shadow targets are not allocated",
            requireAllocated > createAlt);
        assertTrue("Main clear framebuffers must record stage writes to the main texture set",
            addMainWrites > requireAllocated && createWithDepth > addMainWrites);
        assertTrue("Shadow clear framebuffers must attach the main shadow depth texture", addDepth > createWithDepth);
        assertTrue("Shadow clear framebuffers must choose main or alternate color textures by requested clear side",
            textureSelection > addDepth && mainTexture > textureSelection && altTexture > mainTexture);
        assertTrue("Shadow clear framebuffers must attach selected color targets to dense logical attachments",
            addColor > altTexture && drawBuffers > addColor);
        assertTrue("Shadow clear framebuffers must select dense draw buffers and a deterministic read buffer",
            readBuffer > drawBuffers);
        assertTrue("Shadow clear framebuffers must fail before use if incomplete", completeness > readBuffer);
        assertTrue("Shadow clear framebuffer setup failures must destroy partial framebuffer wrappers",
            destroyOnFailure > completeness);
        assertTrue(source.contains("public void destroyFramebuffer(GlFramebuffer framebuffer)"));
        assertTrue(source.contains("private static int[] logicalDrawBuffers(int count)"));
        assertFalse("Shadow color clears must not keep the old temporary per-texture framebuffer path",
            source.contains("clearColorBufferTexture("));
        assertFalse("Enabled shadow rendering must clear through persistent clear passes",
            source.contains("clearColorBuffersForRender()"));
    }

    @Test
    public void shadowRenderFramebufferUsesShadowMapOwnershipAndDepthSourceLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String createShadowFramebuffer = source.substring(
            source.indexOf("public GlFramebuffer createShadowFramebuffer"),
            source.indexOf("public GlFramebuffer getDepthSourceFramebuffer"));
        String getDepthSourceFramebuffer = source.substring(
            source.indexOf("public GlFramebuffer getDepthSourceFramebuffer"),
            source.indexOf("public void destroyFramebuffer"));
        String invert = source.substring(
            source.indexOf("private Set<Integer> invert"),
            source.indexOf("private GlFramebuffer createEmptyFramebuffer"));

        int requireAllocated = createShadowFramebuffer.indexOf(
            "requireAllocatedShadowTargets(\"Shadow render framebuffer\");");
        int emptyGuard = createShadowFramebuffer.indexOf("if (drawBuffers.length == 0)", requireAllocated);
        int emptyFramebuffer = createShadowFramebuffer.indexOf("return createEmptyFramebuffer();", emptyGuard);
        int invertedFramebuffer = createShadowFramebuffer.indexOf(
            "return createColorFramebufferWithDepth(invert(stageWritesToAlt, drawBuffers), drawBuffers);",
            emptyFramebuffer);
        int depthNullGuard = getDepthSourceFramebuffer.indexOf("if (depthSourceFramebuffer == null)");
        int depthThrow = getDepthSourceFramebuffer.indexOf(
            "throw new IllegalStateException(\"Shadow depth source framebuffer is not initialized\");",
            depthNullGuard);
        int depthReturn = getDepthSourceFramebuffer.indexOf("return depthSourceFramebuffer;", depthThrow);
        int invertLoop = invert.indexOf("for (int buffer : relevant)");
        int invertContains = invert.indexOf("if (base == null || !base.contains(buffer))", invertLoop);
        int invertAdd = invert.indexOf("inverted.add(buffer);", invertContains);

        assertTrue("Shadow render framebuffer creation must fail clearly when targets are unallocated",
            requireAllocated >= 0);
        assertTrue("Shadow render framebuffer creation must keep the empty framebuffer path for no draw buffers",
            emptyGuard > requireAllocated && emptyFramebuffer > emptyGuard);
        assertTrue("Shadow render framebuffer creation must invert the stage's alt-write snapshot like 1.16.5",
            invertedFramebuffer > emptyFramebuffer);
        assertTrue("Shadow target preparation must expose the owned depth-source framebuffer",
            depthNullGuard >= 0 && depthThrow > depthNullGuard && depthReturn > depthThrow);
        assertTrue("ShadowMap.invert must select draw buffers not already writing to alternate targets",
            invertLoop >= 0 && invertContains > invertLoop && invertAdd > invertContains);

        ShadowMap disabledShadowMap = new ShadowMap(
            new PackDirectives(new ShaderProperties("shadow.enabled=false\n")),
            new ShaderProperties("shadow.enabled=false\n"),
            Config.get(),
            false);
        boolean threw = false;
        try {
            disabledShadowMap.getDepthSourceFramebuffer();
        } catch (IllegalStateException exception) {
            threw = exception.getMessage().contains("Shadow depth source framebuffer is not initialized");
        }
        assertTrue("Disabled/unallocated shadow maps must fail clearly when a depth-source framebuffer is requested",
            threw);
    }

    @Test
    public void shadowTextureCreationDeletesGeneratedTextureWhenSetupFails() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String createDepthTexture = source.substring(
            source.indexOf("private static int createDepthTexture"),
            source.indexOf("private static int createColorTexture"));
        String createColorTexture = source.substring(
            source.indexOf("private static int createColorTexture"),
            source.indexOf("static int[] depthSwizzleRgba"));

        assertTextureCreationCleanup(createDepthTexture, "Shadow depth texture");
        assertTextureCreationCleanup(createColorTexture, "Shadow color texture");
    }

    @Test
    public void shadowNoTranslucentsDepthCopyOwnsFramebuffersAndUsesDepthCopyStrategyLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String copy = source.substring(
            source.indexOf("public void copyDepthToNoTranslucents()"),
            source.indexOf("private static int createDepthTexture"));
        String copyFramebuffers = source.substring(
            source.indexOf("private void allocateCopyFramebuffers()"),
            source.indexOf("private void registerBindings()"));

        assertTrue(source.contains("import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;"));
        assertTrue(source.contains("import net.oculus.rendertarget.DepthCopyStrategy;"));
        assertTrue(source.contains("private GlFramebuffer depthSourceFramebuffer;"));
        assertTrue(source.contains("private GlFramebuffer noTranslucentsDestFramebuffer;"));
        assertTrue(source.contains("private boolean translucentDepthDirty;"));

        assertTrue(copyFramebuffers.contains("depthSourceFramebuffer = createDepthCopyFramebuffer(depthTextures[0]);"));
        assertTrue(copyFramebuffers.contains("noTranslucentsDestFramebuffer = createDepthCopyFramebuffer(depthTextures[1]);"));
        assertTrue(copyFramebuffers.contains("translucentDepthDirty = true;"));
        assertTrue(copyFramebuffers.contains("framebuffer.addDepthAttachment(depthTexture);"));
        assertTrue(copyFramebuffers.contains("framebuffer.drawBuffers(logicalDrawBuffers(1));"));
        assertTrue(copyFramebuffers.contains("framebuffer.readBuffer(0);"));
        assertTrue(copyFramebuffers.contains("if (!framebuffer.isComplete())"));

        int saveCombined = copy.indexOf("int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();");
        int saveRead = copy.indexOf("int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();",
            saveCombined);
        int saveDraw = copy.indexOf("int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();",
            saveRead);
        int saveActiveTexture = copy.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);",
            saveDraw);
        int restoreDefaultActive = copy.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();", saveActiveTexture);
        int saveTexture = copy.indexOf("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            restoreDefaultActive);
        int snapshotDirty = copy.indexOf(
            "boolean useBlit = translucentDepthDirty && OculusRenderSystem.supportsFramebufferBlit();", saveTexture);
        int blit = copy.indexOf("OculusRenderSystem.blitFramebuffer(", snapshotDirty);
        int strategy = copy.indexOf("depthCopyStrategy.copy(depthSourceFramebuffer, depthTextures[0], noTranslucentsDestFramebuffer,",
            blit);
        int markCopyCompleted = copy.indexOf("copyCompleted = true;", strategy);
        int restoreTexture = copy.indexOf("restoreDefaultTextureBinding(cleanupFailure, previousTexture);",
            markCopyCompleted);
        int clean = copy.indexOf("OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,",
            restoreTexture);
        int restoreActiveTexture = copy.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);",
            clean);
        int rethrowCleanup = copy.indexOf("rethrowCleanupFailure(cleanupFailure);", restoreActiveTexture);
        int copyCompletedGuard = copy.indexOf("if (copyCompleted)", rethrowCleanup);
        int clearDirty = copy.indexOf("translucentDepthDirty = false;", copyCompletedGuard);

        assertTrue("Shadow depth copy must save the incoming combined framebuffer binding", saveCombined >= 0);
        assertTrue("Shadow depth copy must save the incoming read binding after the combined binding", saveRead > saveCombined);
        assertTrue("Shadow depth copy must save the incoming draw binding after the read binding", saveDraw > saveRead);
        assertTrue("Shadow depth copy must save the incoming active texture unit", saveActiveTexture > saveDraw);
        assertTrue("Shadow depth copy must use the default texture unit for copy fallback safety",
            restoreDefaultActive > saveActiveTexture);
        assertTrue("Shadow depth copy must save the default unit's previous texture binding",
            saveTexture > restoreDefaultActive);
        assertTrue("Shadow depth copy must snapshot the 1.16.5 dirty flag before copy work",
            snapshotDirty > saveTexture);
        assertTrue("First shadow depth copy must use framebuffer blit when the backend supports it", blit > snapshotDirty);
        assertTrue("Subsequent shadow depth copies must route through DepthCopyStrategy", strategy > blit);
        assertTrue("Shadow depth copy must mark copy completion only after the copy path succeeds",
            markCopyCompleted > strategy);
        assertTrue("Shadow depth copy must restore the default unit texture binding", restoreTexture > markCopyCompleted);
        assertTrue("Shadow depth copy must restore framebuffer bindings in the finally path", clean > restoreTexture);
        assertTrue("Shadow depth copy must restore the caller's active texture unit",
            restoreActiveTexture > clean);
        assertTrue("Shadow depth copy must clear the dirty flag only after copy and cleanup both succeed",
            copyCompletedGuard > rethrowCleanup && clearDirty > copyCompletedGuard);
        String restoreHelper = methodBody(source,
            "private static Throwable restoreDefaultTextureBinding(Throwable failure, int previousTexture)");
        assertTrue("Shadow depth copy cleanup must explicitly reselect texture0 before restoring its saved binding",
            restoreHelper.contains("restoreTextureUnitBinding(failure, GL13.GL_TEXTURE0, previousTexture);"));
        assertFalse(copy.contains("OculusRenderSystem.copyTexSubImage2D("));
        assertFalse(copy.contains("GL11.glCopyTexSubImage2D"));
    }

    @Test
    public void shadowDepthCopyAggregatesCleanupFailuresWithoutMaskingPrimaryFailure() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String copy = source.substring(
            source.indexOf("public void copyDepthToNoTranslucents()"),
            source.indexOf("private static int createDepthTexture"));

        int strategy = copy.indexOf("depthCopyStrategy.copy(depthSourceFramebuffer, depthTextures[0], noTranslucentsDestFramebuffer,");
        int catchBlock = copy.indexOf("catch (RuntimeException | Error exception)", strategy);
        int recordPrimary = copy.indexOf("failure = exception;", catchBlock);
        int outerFinally = copy.indexOf("finally {", recordPrimary);
        int cleanupLocal = copy.indexOf("Throwable cleanupFailure = null;", outerFinally);
        int restoreTextureGuard = copy.indexOf("if (restoreTexture)", cleanupLocal);
        int restoreTexture = copy.indexOf("restoreDefaultTextureBinding(cleanupFailure, previousTexture);",
            restoreTextureGuard);
        int restoreHelper = source.indexOf("private static Throwable restoreDefaultTextureBinding");
        int textureCatch = source.indexOf("failure = addCleanupFailure(failure, exception);",
            restoreHelper);
        int helperReturn = source.indexOf("return failure;", textureCatch);
        int restoreFramebuffer = copy.indexOf(
            "OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,",
            restoreTexture);
        int framebufferCatch = copy.indexOf("cleanupFailure = addCleanupFailure(cleanupFailure, exception);",
            restoreFramebuffer);
        int restoreActiveTexture = copy.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);",
            framebufferCatch);
        int activeTextureCatch = copy.indexOf("cleanupFailure = addCleanupFailure(cleanupFailure, exception);",
            restoreActiveTexture);
        int primaryGuard = copy.indexOf("if (failure != null)", activeTextureCatch);
        int suppressCleanup = copy.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", primaryGuard);
        int rethrowCleanupOnly = copy.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressCleanup);

        assertTrue("Shadow depth copy must preserve the primary copy failure before cleanup",
            catchBlock > strategy && recordPrimary > catchBlock);
        assertTrue("Shadow depth copy cleanup must run from the finally path",
            outerFinally > recordPrimary && cleanupLocal > outerFinally);
        assertTrue("Shadow depth copy must attempt default-unit texture restore first when it was captured",
            restoreTextureGuard > cleanupLocal && restoreTexture > restoreTextureGuard
                && textureCatch > restoreHelper && helperReturn > textureCatch);
        assertTrue("Shadow depth copy must attempt framebuffer restore even if texture restore fails",
            restoreFramebuffer > restoreTexture && framebufferCatch > restoreFramebuffer);
        assertTrue("Shadow depth copy must restore caller active unit even if framebuffer restore fails",
            restoreActiveTexture > framebufferCatch && activeTextureCatch > restoreActiveTexture);
        assertTrue("Shadow depth copy cleanup failures must suppress onto primary failures or rethrow alone",
            primaryGuard > activeTextureCatch && suppressCleanup > primaryGuard
                && rethrowCleanupOnly > suppressCleanup);
    }

    @Test
    public void shadowColorSamplerBindingsFollowFlippedShadowTargetsLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String applyBindings = source.substring(
            source.indexOf("public boolean applySamplerBindings"),
            source.indexOf("public void destroy()"));
        String registerBindings = source.substring(
            source.indexOf("private void registerBindings()"),
            source.indexOf("private static TextureBinding bindingFor"));

        assertTrue(applyBindings.contains("TextureBinding.texture2D(() -> getColorTexture(0));"));
        assertTrue(applyBindings.contains("TextureBinding.texture2D(() -> getColorTexture(1));"));
        assertTrue(registerBindings.contains("TextureBinding.texture2D(() -> getColorTexture(0));"));
        assertTrue(registerBindings.contains("TextureBinding.texture2D(() -> getColorTexture(1));"));
        assertFalse(applyBindings.contains("TextureBinding.texture2D(() -> colorTextures[0])"));
        assertFalse(registerBindings.contains("TextureBinding.texture2D(() -> colorTextures[0])"));
    }

    @Test
    public void shadowDepthSamplerBindingsSetCompareModePerProgramSamplerType() throws Exception {
        String textureBinding = read("src/main/java/net/oculus/gl/program/TextureBinding.java");
        String programBuilder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String applyBindings = source.substring(
            source.indexOf("public boolean applySamplerBindings"),
            source.indexOf("public void destroy()"));
        String bindingFor = methodBody(source, "private static TextureBinding bindingFor");
        String isShadowCompareSampler = methodBody(source, "private static boolean isShadowCompareSampler");
        String configureDepthCompareMode = methodBody(source, "private static void configureDepthCompareMode");

        assertTrue(textureBinding.contains("private final IntConsumer textureConfigurator;"));
        assertTrue(textureBinding.contains("textureConfigurator.accept(texture);"));
        assertTrue(programBuilder.contains("private Map<String, Integer> activeSamplerUniformTypes;"));
        assertTrue(programBuilder.contains("activeSamplerUniformTypes.put(uniformName, glType);"));
        assertTrue(programBuilder.contains("public int getActiveSamplerUniformType(String name)"));

        assertTrue(applyBindings.contains("TextureBinding depthRaw = TextureBinding.texture2D("));
        assertTrue(applyBindings.contains("texture -> configureDepthCompareMode(texture, false)"));
        assertTrue(applyBindings.contains("TextureBinding depthCompare = TextureBinding.texture2D("));
        assertTrue(applyBindings.contains("texture -> configureDepthCompareMode(texture, true)"));
        assertTrue(applyBindings.contains("TextureBinding depthNoTranslucentsRaw = TextureBinding.texture2D("));
        assertTrue(applyBindings.contains("TextureBinding depthNoTranslucentsCompare = TextureBinding.texture2D("));
        assertTrue(applyBindings.contains("builder.overrideSamplerBinding(name, bindingFor("));

        assertTrue(bindingFor.contains("isShadowCompareSampler(builder, samplerName) ? depthCompare : depthRaw"));
        assertTrue(bindingFor.contains("isShadowCompareSampler(builder, samplerName)"));
        assertTrue(bindingFor.contains("depthNoTranslucentsCompare"));
        assertTrue(bindingFor.contains("depthNoTranslucentsRaw"));
        assertTrue(isShadowCompareSampler.contains("isHardwareShadowSamplerName(samplerName)"));
        assertTrue(isShadowCompareSampler.contains("builder.getActiveSamplerUniformType(samplerName) == GL20.GL_SAMPLER_2D_SHADOW"));
        assertTrue(configureDepthCompareMode.contains("GL14.GL_TEXTURE_COMPARE_MODE"));
        assertTrue(configureDepthCompareMode.contains("compare ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE"));
    }

    @Test
    public void shadowGlobalSamplerBindingsUnregisterByOwnedIdentityOnDestroy() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String destroy = source.substring(
            source.indexOf("public void destroy()"),
            source.indexOf("private void allocateTextures()"));
        String registerBindings = source.substring(
            source.indexOf("private void registerBindings()"),
            source.indexOf("private static TextureBinding bindingFor"));
        String unregisterNoArg = methodBody(source, "private void unregisterBindings()");

        int unregisterBeforeFramebufferDestroy = destroy.indexOf("failure = unregisterBindings(failure);");
        int destroyOwnedFramebuffers = destroy.indexOf(
            "failure = destroyOwnedFramebuffers(failure);", unregisterBeforeFramebufferDestroy);
        int depthBindingField = source.indexOf("private TextureBinding registeredDepthBinding;");
        int colorBindingField = source.indexOf("private TextureBinding registeredColor0Binding;", depthBindingField);
        int createDepthBinding = registerBindings.indexOf("registeredDepthBinding = TextureBinding.texture2D(() -> depthTextures[0]);");
        int createColorBinding = registerBindings.indexOf("registeredColor0Binding = TextureBinding.texture2D(() -> getColorTexture(0));",
            createDepthBinding);
        int registerDepthAliases = registerBindings.indexOf("registerBindings(DEPTH_BINDING_NAMES, registeredDepthBinding);",
            createColorBinding);
        int registerDepthHardwareGuard = registerBindings.indexOf("if (isHardwareFiltered(0))", registerDepthAliases);
        int registerDepthHardwareAliases = registerBindings.indexOf(
            "registerBindings(DEPTH_HARDWARE_BINDING_NAMES, registeredDepthBinding);", registerDepthHardwareGuard);
        int unregisterMethod = registerBindings.indexOf("private void unregisterBindings()", registerDepthAliases);
        int unregisterDepthAliases = registerBindings.indexOf(
            "failure = unregisterBindings(failure, DEPTH_BINDING_NAMES, registeredDepthBinding);", unregisterMethod);
        int unregisterDepthHardwareAliases = registerBindings.indexOf(
            "failure = unregisterBindings(failure, DEPTH_HARDWARE_BINDING_NAMES, registeredDepthBinding);",
            unregisterDepthAliases);
        int unregisterDepthNoTranslucentsAliases = registerBindings.indexOf(
            "failure = unregisterBindings(failure, DEPTH_NO_TRANSLUCENTS_BINDING_NAMES, registeredDepthNoTranslucentsBinding);",
            unregisterDepthHardwareAliases);
        int unregisterDepthNoTranslucentsHardwareAliases = registerBindings.indexOf(
            "failure = unregisterBindings(failure, DEPTH_NO_TRANSLUCENTS_HARDWARE_BINDING_NAMES,",
            unregisterDepthNoTranslucentsAliases);
        int unregisterColorAliases = registerBindings.indexOf(
            "failure = unregisterBindings(failure, COLOR0_BINDING_NAMES, registeredColor0Binding);",
            unregisterDepthNoTranslucentsHardwareAliases);
        int unregisterColor1Aliases = registerBindings.indexOf(
            "failure = unregisterBindings(failure, COLOR1_BINDING_NAMES, registeredColor1Binding);",
            unregisterColorAliases);
        int identityUnregister = registerBindings.indexOf("TextureBindingRegistry.unregister(name, binding)",
            unregisterColorAliases);
        int returnFailure = registerBindings.indexOf("return failure;", unregisterColorAliases);
        int clearHelper = registerBindings.indexOf("private void clearRegisteredBindings()", returnFailure);
        int clearDepthBinding = registerBindings.indexOf("registeredDepthBinding = null;", clearHelper);
        int noArgRethrow = unregisterNoArg.indexOf("rethrowCleanupFailure(failure);");
        int noArgFinally = unregisterNoArg.indexOf("} finally {", noArgRethrow);
        int noArgClear = unregisterNoArg.indexOf("clearRegisteredBindings();", noArgFinally);

        assertTrue("ShadowMap must unregister global sampler bindings before deleting owned GL objects",
            unregisterBeforeFramebufferDestroy >= 0 && destroyOwnedFramebuffers > unregisterBeforeFramebufferDestroy);
        assertTrue(depthBindingField >= 0);
        assertTrue(colorBindingField > depthBindingField);
        assertTrue(createDepthBinding >= 0);
        assertTrue(createColorBinding > createDepthBinding);
        assertTrue(registerDepthAliases > createColorBinding);
        assertTrue("Hardware shadow aliases must only enter the global registry when hardware filtering is enabled",
            registerDepthHardwareGuard > registerDepthAliases
                && registerDepthHardwareAliases > registerDepthHardwareGuard);
        assertTrue(unregisterMethod > registerDepthAliases);
        assertTrue(unregisterDepthAliases > unregisterMethod);
        assertTrue(unregisterDepthHardwareAliases > unregisterDepthAliases);
        assertTrue(unregisterDepthNoTranslucentsAliases > unregisterDepthHardwareAliases);
        assertTrue(unregisterDepthNoTranslucentsHardwareAliases > unregisterDepthNoTranslucentsAliases);
        assertTrue(unregisterColorAliases > unregisterDepthNoTranslucentsHardwareAliases);
        assertTrue(unregisterColor1Aliases > unregisterColorAliases);
        assertTrue("ShadowMap must unregister by binding identity so newer aliases survive reload teardown",
            identityUnregister > unregisterColorAliases);
        assertTrue("ShadowMap must keep owned binding identities available through all unregister attempts",
            returnFailure > unregisterColor1Aliases && clearHelper > returnFailure
                && clearDepthBinding > clearHelper);
        assertTrue("ShadowMap no-arg unregister must clear owned binding identities even when cleanup fails",
            noArgRethrow >= 0 && noArgFinally > noArgRethrow && noArgClear > noArgFinally);
        assertFalse(registerBindings.contains("TextureBindingRegistry.unregister(name);"));
    }

    @Test
    public void shadowGlobalHardwareSamplerAliasesFollowHardwareFilteringLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String depthAliases = source.substring(
            source.indexOf("private static final String[] DEPTH_BINDING_NAMES"),
            source.indexOf("private static final String[] DEPTH_HARDWARE_BINDING_NAMES"));
        String depthHardwareAliases = source.substring(
            source.indexOf("private static final String[] DEPTH_HARDWARE_BINDING_NAMES"),
            source.indexOf("private static final String[] DEPTH_NO_TRANSLUCENTS_BINDING_NAMES"));
        String noTransAliases = source.substring(
            source.indexOf("private static final String[] DEPTH_NO_TRANSLUCENTS_BINDING_NAMES"),
            source.indexOf("private static final String[] DEPTH_NO_TRANSLUCENTS_HARDWARE_BINDING_NAMES"));
        String noTransHardwareAliases = source.substring(
            source.indexOf("private static final String[] DEPTH_NO_TRANSLUCENTS_HARDWARE_BINDING_NAMES"),
            source.indexOf("private static final String[] COLOR0_BINDING_NAMES"));
        String registerBindings = source.substring(
            source.indexOf("private void registerBindings()"),
            source.indexOf("private void unregisterBindings()"));

        assertFalse("Base depth aliases must not expose hardware-filtered shadowtex0 names",
            depthAliases.contains("shadowtex0HW") || depthAliases.contains("shadowtex0hw"));
        assertFalse("Base no-translucents aliases must not expose hardware-filtered shadowtex1 names",
            noTransAliases.contains("shadowtex1HW") || noTransAliases.contains("shadowtex1hw"));
        assertTrue(depthHardwareAliases.contains("\"shadowtex0HW\""));
        assertTrue(depthHardwareAliases.contains("\"shadowtex0hw\""));
        assertTrue(noTransHardwareAliases.contains("\"shadowtex1HW\""));
        assertTrue(noTransHardwareAliases.contains("\"shadowtex1hw\""));
        assertTrue(registerBindings.contains("if (isHardwareFiltered(0))"));
        assertTrue(registerBindings.contains("registerBindings(DEPTH_HARDWARE_BINDING_NAMES, registeredDepthBinding);"));
        assertTrue(registerBindings.contains("if (isHardwareFiltered(1))"));
        assertTrue(registerBindings.contains(
            "registerBindings(DEPTH_NO_TRANSLUCENTS_HARDWARE_BINDING_NAMES, registeredDepthNoTranslucentsBinding);"));
    }

    @Test
    public void shadowColorMipmapsFollowCurrentReadableTextureLikeSamplersAndImages() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String generateMipmaps = source.substring(
            source.indexOf("public void generateMipmaps()"),
            source.indexOf("private static void generateMipmap"));

        int saveActiveTexture = generateMipmaps.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int previousTextureLocal = generateMipmaps.indexOf("int previousTexture = 0;", saveActiveTexture);
        int textureCapturedFlag = generateMipmaps.indexOf("boolean textureCaptured = false;", previousTextureLocal);
        int failureLocal = generateMipmaps.indexOf("Throwable failure = null;", textureCapturedFlag);
        int tryBlock = generateMipmaps.indexOf("try {", failureLocal);
        int switchMipmapUnit = generateMipmaps.indexOf("OculusRenderSystem.setActiveTextureUnit(GL13.GL_TEXTURE4);",
            tryBlock);
        int saveTexture = generateMipmaps.indexOf("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            switchMipmapUnit);
        int markTextureCaptured = generateMipmaps.indexOf("textureCaptured = true;", saveTexture);
        int restoreTexture = generateMipmaps.indexOf(
            "restoreTextureUnitBinding(cleanupFailure, GL13.GL_TEXTURE4, previousTexture)", saveTexture);
        int restoreActiveTexture = generateMipmaps.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)",
            restoreTexture);

        assertTrue("Shadow mipmaps must save the caller's active texture unit", saveActiveTexture >= 0);
        assertTrue("Shadow mipmap generation must initialize capture state before entering guarded GL work",
            previousTextureLocal > saveActiveTexture && textureCapturedFlag > previousTextureLocal
                && failureLocal > textureCapturedFlag && tryBlock > failureLocal);
        assertTrue("Shadow mipmaps must use the reference dedicated mipmap texture unit",
            switchMipmapUnit > tryBlock);
        assertTrue("Shadow mipmaps must save the dedicated unit texture binding",
            saveTexture > switchMipmapUnit);
        assertTrue("Shadow mipmaps must mark texture capture only after reading the dedicated unit binding",
            markTextureCaptured > saveTexture);
        assertTrue(generateMipmaps.contains("generateMipmap(getColorTexture(i), settings);"));
        assertTrue("Shadow mipmaps must restore the dedicated unit texture binding",
            restoreTexture > markTextureCaptured);
        assertTrue("Shadow mipmaps must restore the caller's active texture unit",
            restoreActiveTexture > restoreTexture);
        String restoreHelper = methodBody(source,
            "private static Throwable restoreTextureUnitBinding(Throwable failure, int textureUnit, int previousTexture)");
        assertTrue("Shadow mipmap cleanup must reselect the dedicated texture unit before rebinding its saved texture",
            restoreHelper.contains("OculusRenderSystem.setActiveTextureUnit(textureUnit);")
                && restoreHelper.contains("GlStateManager.bindTexture(previousTexture);"));
        assertFalse(generateMipmaps.contains("generateMipmap(colorTextures[i], settings);"));
        assertFalse(generateMipmaps.contains("generateMipmap(altColorTextures[i], settings);"));
    }

    @Test
    public void shadowMipmapGenerationRestoresActiveTextureEvenIfTextureRestoreFails() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String generateMipmaps = source.substring(
            source.indexOf("public void generateMipmaps()"),
            source.indexOf("private static void generateMipmap"));

        int failureLocal = generateMipmaps.indexOf("Throwable failure = null;");
        int tryBlock = generateMipmaps.indexOf("try {", failureLocal);
        int saveTexture = generateMipmaps.indexOf("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            tryBlock);
        int markTextureCaptured = generateMipmaps.indexOf("textureCaptured = true;", saveTexture);
        int generateDepthMipmap = generateMipmaps.indexOf("generateMipmap(depthTextures[i], settings);", failureLocal);
        int catchBlock = generateMipmaps.indexOf("catch (RuntimeException | Error exception)", generateDepthMipmap);
        int recordPrimary = generateMipmaps.indexOf("failure = exception;", catchBlock);
        int restoreFinally = generateMipmaps.indexOf("finally {", recordPrimary);
        int cleanupLocal = generateMipmaps.indexOf("Throwable cleanupFailure = null;", restoreFinally);
        int restoreTextureGuard = generateMipmaps.indexOf("if (textureCaptured)", cleanupLocal);
        int restoreTexture = generateMipmaps.indexOf(
            "restoreTextureUnitBinding(cleanupFailure, GL13.GL_TEXTURE4, previousTexture)",
            restoreTextureGuard);
        int restoreHelper = source.indexOf("private static Throwable restoreTextureUnitBinding");
        int textureCatch = source.indexOf("failure = addCleanupFailure(failure, exception);", restoreHelper);
        int helperReturn = source.indexOf("return failure;", textureCatch);
        int restoreActiveTexture = generateMipmaps.indexOf(
            "OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);", restoreTexture);
        int activeTextureCatch = generateMipmaps.indexOf(
            "cleanupFailure = addCleanupFailure(cleanupFailure, exception);", restoreActiveTexture);
        int suppressCleanup = generateMipmaps.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);",
            activeTextureCatch);
        int rethrowCleanupOnly = generateMipmaps.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressCleanup);

        assertTrue("Shadow mipmap cleanup must guard the texture-unit switch and binding capture",
            tryBlock > failureLocal && saveTexture > tryBlock && markTextureCaptured > saveTexture);
        assertTrue("Shadow mipmap cleanup must run after saving the dedicated unit texture binding",
            restoreFinally > recordPrimary);
        assertTrue("Shadow mipmap cleanup must first attempt to restore the dedicated unit texture binding when captured",
            cleanupLocal > restoreFinally && restoreTextureGuard > cleanupLocal
                && restoreTexture > restoreTextureGuard && textureCatch > restoreHelper && helperReturn > textureCatch);
        assertTrue("Shadow mipmap cleanup must restore caller active unit even if texture restore fails",
            restoreActiveTexture > restoreTexture && activeTextureCatch > restoreActiveTexture);
        assertTrue("Shadow mipmap cleanup failures must suppress onto primary failures or rethrow alone",
            catchBlock > generateDepthMipmap && recordPrimary > catchBlock
                && suppressCleanup > activeTextureCatch && rethrowCleanupOnly > suppressCleanup);
    }

    @Test
    public void shadowOperationsFailClearlyWhenRequiredTexturesAreMissing() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String createDepthCopyFramebuffer = source.substring(
            source.indexOf("private GlFramebuffer createDepthCopyFramebuffer"),
            source.indexOf("private void registerBindings()"));
        String copyDepth = source.substring(
            source.indexOf("public void copyDepthToNoTranslucents()"),
            source.indexOf("private static int createDepthTexture"));
        String generateMipmap = source.substring(
            source.indexOf("private static void generateMipmap"),
            source.indexOf("private static int requireValidShadowTexture"));
        String requireTexture = source.substring(
            source.indexOf("private static int requireValidShadowTexture"),
            source.indexOf("private static void deleteTexture"));

        int requireDepth = createDepthCopyFramebuffer.indexOf(
            "requireValidShadowTexture(depthTexture, \"Shadow depth copy framebuffer depth texture\");");
        int requireColor = createDepthCopyFramebuffer.indexOf(
            "requireValidShadowTexture(colorTextures[0], \"Shadow depth copy framebuffer color attachment\");",
            requireDepth);
        int createFramebuffer = createDepthCopyFramebuffer.indexOf("GlFramebuffer framebuffer = createOwnedFramebuffer();",
            requireColor);
        assertTrue("Shadow depth copy framebuffer must validate its depth texture before FBO setup",
            requireDepth >= 0);
        assertTrue("Shadow depth copy framebuffer must validate the required legacy color attachment",
            requireColor > requireDepth);
        assertTrue("Shadow depth copy validation must run before allocating the framebuffer wrapper",
            createFramebuffer > requireColor);

        int disabledGuard = copyDepth.indexOf("if (!enabled)");
        int requireSourceDepth = copyDepth.indexOf(
            "requireValidShadowTexture(depthTextures[0], \"Shadow source depth texture\");",
            disabledGuard);
        int requireNoTranslucentsDepth = copyDepth.indexOf(
            "requireValidShadowTexture(depthTextures[1], \"Shadow no-translucents depth texture\");",
            requireSourceDepth);
        int framebufferGuard = copyDepth.indexOf("if (depthSourceFramebuffer == null || noTranslucentsDestFramebuffer == null)",
            requireNoTranslucentsDepth);
        assertTrue("Disabled shadow maps may still skip depth copy work", disabledGuard >= 0);
        assertTrue("Enabled shadow depth copy must validate the source depth texture",
            requireSourceDepth > disabledGuard);
        assertTrue("Enabled shadow depth copy must validate the no-translucents depth texture",
            requireNoTranslucentsDepth > requireSourceDepth);
        assertTrue("Texture validation must happen before framebuffer validation",
            framebufferGuard > requireNoTranslucentsDepth);
        assertFalse("Enabled shadow depth copy must not silently skip a missing destination texture",
            copyDepth.contains("!enabled || depthTextures[1] <= 0"));

        assertTrue("Shadow mipmap generation must fail clearly for a missing required texture",
            generateMipmap.contains("requireValidShadowTexture(texture, \"Shadow mipmap texture\");"));
        assertFalse("Shadow mipmap generation must not silently skip missing required textures",
            generateMipmap.contains("if (texture <= 0)"));

        assertTrue("Shadow texture validation must throw a precise allocation error",
            requireTexture.contains("throw new IllegalStateException(context + \" is not allocated\");"));
    }

    @Test
    public void shadowTextureDeletesNotifyLifecycleEvenWhenGlDeleteFails() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String deleteTexture = methodBody(source, "private static void deleteTexture(int texture)");

        int positiveGuard = deleteTexture.indexOf("if (texture <= 0)");
        int failureLocal = deleteTexture.indexOf("Throwable failure = null;", positiveGuard);
        int deleteTry = deleteTexture.indexOf("try {", failureLocal);
        int glDelete = deleteTexture.indexOf("GL11.glDeleteTextures(texture);", deleteTry);
        int deleteCatch = deleteTexture.indexOf("catch (RuntimeException | Error exception)", glDelete);
        int recordDeleteFailure = deleteTexture.indexOf("failure = addCleanupFailure(failure, exception);",
            deleteCatch);
        int finallyBlock = deleteTexture.indexOf("finally {", recordDeleteFailure);
        int lifecycleTry = deleteTexture.indexOf("try {", finallyBlock);
        int lifecycleNotify = deleteTexture.indexOf("TextureLifecycleTracker.onDeleteTexture(texture);",
            lifecycleTry);
        int lifecycleCatch = deleteTexture.indexOf("catch (RuntimeException | Error exception)", lifecycleNotify);
        int recordLifecycleFailure = deleteTexture.indexOf("failure = addCleanupFailure(failure, exception);",
            lifecycleCatch);
        int rethrow = deleteTexture.indexOf("rethrowCleanupFailure(failure);", recordLifecycleFailure);

        assertTrue("Shadow texture delete must ignore invalid texture ids", positiveGuard >= 0);
        assertTrue("Shadow texture delete must record GL delete failures",
            failureLocal > positiveGuard && deleteTry > failureLocal && glDelete > deleteTry
                && deleteCatch > glDelete && recordDeleteFailure > deleteCatch);
        assertTrue("Shadow texture delete must notify lifecycle from finally after GL delete is attempted",
            finallyBlock > recordDeleteFailure && lifecycleTry > finallyBlock
                && lifecycleNotify > lifecycleTry);
        assertTrue("Shadow texture delete must aggregate lifecycle notification failures before rethrowing",
            lifecycleCatch > lifecycleNotify && recordLifecycleFailure > lifecycleCatch
                && rethrow > recordLifecycleFailure);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void setTextureIds(ShadowMap shadowMap, String fieldName, int first, int second) throws Exception {
        Field field = ShadowMap.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        int[] textures = (int[]) field.get(shadowMap);
        textures[0] = first;
        textures[1] = second;
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

    private static void assertDestroyedShadowMapThrows(Runnable action, String operation) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Cannot " + operation
                + " after shadow render targets were destroyed"));
            return;
        }

        throw new AssertionError("Expected destroyed ShadowMap access to fail for " + operation);
    }

    private static void assertTextureCreationCleanup(String body, String context) {
        int allocate = body.indexOf("int texture = createTexture(");
        int tryBlock = body.indexOf("try {", allocate);
        int restoreWrapper = body.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {",
            tryBlock);
        int returnTexture = body.indexOf("return texture;", restoreWrapper);
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", returnTexture);
        int cleanup = body.indexOf("runCleanup(null, () -> deleteTexture(texture))", catchBlock);
        int suppress = body.indexOf("addSuppressedCleanupFailure(exception,", catchBlock);
        int rethrow = body.indexOf("throw exception;", suppress);

        assertTrue(context + " must allocate and validate the GL texture before setup", allocate >= 0);
        assertTrue(context + " setup must run inside a cleanup guard", tryBlock > allocate);
        assertTrue(context + " must restore texture binding state during setup", restoreWrapper > tryBlock);
        assertTrue(context + " must return only after restoring texture binding", returnTexture > restoreWrapper);
        assertTrue(context + " must catch setup failures after the success return", catchBlock > returnTexture);
        assertTrue(context + " must delete the generated texture when setup fails", cleanup > catchBlock);
        assertTrue(context + " must suppress deletion failures onto setup failures",
            suppress > catchBlock && rethrow > suppress);
    }
}
