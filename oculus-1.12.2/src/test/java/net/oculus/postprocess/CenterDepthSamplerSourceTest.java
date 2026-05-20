package net.oculus.postprocess;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class CenterDepthSamplerSourceTest {
    @Test
    public void setupColorTextureUsesReferenceStyleRenderSystemWrappers() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String setup = between(source, "private static void setupColorTexture", "private static String prepareFragmentSource");

        assertTrue(setup.contains("OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(setup.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER"));
        assertTrue(setup.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER"));
        assertTrue(setup.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S"));
        assertTrue(setup.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T"));
        assertFalse(setup.contains("GL11.glBindTexture"));
        assertFalse(setup.contains("GL11.glTexImage2D"));
        assertFalse(setup.contains("GL11.glTexParameteri"));
        assertFalse(setup.contains("TextureLifecycleTracker.onTexImage2D"));

        int firstSetup = source.indexOf("setupColorTexture(textureId, internalFormat, pixelFormat);");
        int secondSetup = source.indexOf("setupColorTexture(setupAltTextureId, internalFormat, pixelFormat);",
            firstSetup);
        int restoreWrapper = source.lastIndexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {",
            firstSetup);
        assertTrue(firstSetup >= 0);
        assertTrue(secondSetup > firstSetup);
        assertTrue("Center-depth texture setup must restore caller texture binding state",
            restoreWrapper >= 0 && restoreWrapper < firstSetup);
        assertFalse(source.contains("GlStateManager.bindTexture(0)"));
    }

    @Test
    public void constructorCleansTexturesFramebufferAndProgramOnSetupFailure() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String constructor = between(source,
            "public CenterDepthSampler(IntSupplier depthSupplier, float halfLife)",
            "/**\n     * Gets the smoothed center-depth texture ID");

        int createdTexture = constructor.indexOf("int createdTexture = 0;");
        int createdAltTexture = constructor.indexOf("int createdAltTexture = 0;", createdTexture);
        int createdFramebuffer = constructor.indexOf("GlFramebuffer createdFramebuffer = null;", createdAltTexture);
        int createdProgram = constructor.indexOf("Program createdProgram = null;", createdFramebuffer);
        int completeFlag = constructor.indexOf("boolean complete = false;", createdProgram);
        int tryBlock = constructor.indexOf("try {", completeFlag);
        int genTexture = constructor.indexOf("createdTexture = createTexture(\"center depth texture\");", tryBlock);
        int genAltTexture = constructor.indexOf(
            "createdAltTexture = createTexture(\"center depth alternate texture\");", genTexture);
        int newFramebuffer = constructor.indexOf("createdFramebuffer = new GlFramebuffer();", genAltTexture);
        int restoreWrapper = constructor.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {",
            newFramebuffer);
        int textureIdLocal = constructor.indexOf("final int textureId = createdTexture;", newFramebuffer);
        int setupAltTextureIdLocal = constructor.indexOf("final int setupAltTextureId = createdAltTexture;",
            textureIdLocal);
        int setupTexture = constructor.indexOf("setupColorTexture(textureId, internalFormat, pixelFormat);",
            restoreWrapper);
        int setupAltTexture = constructor.indexOf("setupColorTexture(setupAltTextureId, internalFormat, pixelFormat);",
            setupTexture);
        int addAttachment = constructor.indexOf("createdFramebuffer.addColorAttachment(0, createdTexture);",
            setupAltTexture);
        int drawBuffers = constructor.indexOf("createdFramebuffer.drawBuffers(new int[] {0});", addAttachment);
        int readBuffer = constructor.indexOf("createdFramebuffer.readBuffer(0);", drawBuffers);
        int completeness = constructor.indexOf("if (!createdFramebuffer.isComplete())", readBuffer);
        int incompleteThrow = constructor.indexOf(
            "throw new IllegalStateException(\"Center-depth framebuffer is incomplete\");", completeness);
        int altTextureLocal = constructor.indexOf("final int altTextureId = createdAltTexture;", incompleteThrow);
        int beginProgram = constructor.indexOf("ProgramBuilder builder = ProgramBuilder.beginExplicit(",
            altTextureLocal);
        int reservedUnits = constructor.indexOf("IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);", beginProgram);
        int buildProgram = constructor.indexOf("createdProgram = builder.build();", reservedUnits);
        int markComplete = constructor.indexOf("complete = true;", buildProgram);
        int cleanupCatch = constructor.indexOf("catch (RuntimeException | Error exception)", markComplete);
        int failureGuard = constructor.indexOf("if (!complete)", cleanupCatch);
        int destroyCreated = constructor.indexOf("destroyCreatedResources(null, createdProgram,", failureGuard);
        int suppressCleanup = constructor.indexOf("addSuppressedCleanupFailure(exception,", cleanupCatch);
        int rethrow = constructor.indexOf("throw exception;", suppressCleanup);
        int assignTexture = constructor.indexOf("this.texture = createdTexture;", rethrow);
        int assignProgram = constructor.indexOf("this.program = createdProgram;", assignTexture);

        assertTrue(createdTexture >= 0);
        assertTrue(createdAltTexture > createdTexture);
        assertTrue(createdFramebuffer > createdAltTexture);
        assertTrue(createdProgram > createdFramebuffer);
        assertTrue(completeFlag > createdProgram);
        assertTrue(tryBlock > completeFlag);
        assertTrue(genTexture > tryBlock);
        assertTrue(genAltTexture > genTexture);
        assertTrue(newFramebuffer > genAltTexture);
        assertTrue("Center-depth textures must fail clearly if the GL backend does not allocate a texture name",
            source.contains("throw new IllegalStateException(\"Failed to create \" + context);"));
        assertTrue("Texture setup must snapshot generated texture names for lambda setup",
            textureIdLocal > newFramebuffer && setupAltTextureIdLocal > textureIdLocal);
        assertTrue("Texture setup must restore default-unit binding and caller active texture",
            restoreWrapper > setupAltTextureIdLocal && setupTexture > restoreWrapper && setupAltTexture > setupTexture);
        assertTrue(addAttachment > setupAltTexture);
        assertTrue(drawBuffers > addAttachment);
        assertTrue("Center-depth copy framebuffer must explicitly read from color attachment 0",
            readBuffer > drawBuffers);
        assertTrue("Center-depth framebuffer setup must fail before shader compilation if incomplete",
            completeness > readBuffer && incompleteThrow > completeness);
        assertTrue("The alt-depth dynamic sampler must capture the local texture id before final fields are assigned",
            altTextureLocal > incompleteThrow);
        assertTrue("Center-depth helper samplers must reserve vanilla world texture units like 1.16.5",
            beginProgram > altTextureLocal && reservedUnits > beginProgram);
        assertTrue(source.contains("import net.oculus.samplers.IrisSamplers;"));
        assertTrue(buildProgram > reservedUnits);
        assertTrue(markComplete > buildProgram);
        assertTrue("Failed construction must catch RuntimeException and Error setup failures",
            cleanupCatch > markComplete);
        assertTrue("Failed construction must destroy all successfully-created owned resources",
            failureGuard > cleanupCatch && destroyCreated > failureGuard);
        assertTrue("Construction cleanup failures must be suppressed onto the setup failure",
            suppressCleanup > cleanupCatch && rethrow > suppressCleanup);
        assertTrue("Final fields must only take ownership after guarded setup completes",
            assignTexture > rethrow && assignProgram > assignTexture);
        assertFalse(constructor.contains("Throwable textureSetupFailure = null;"));
        assertFalse(constructor.contains("GlStateManager.bindTexture(0)"));
        assertTrue(source.contains("private static Throwable destroyCreatedResources(Throwable failure, Program program, GlFramebuffer framebuffer,"));
        assertTrue(source.contains("failure = runCleanup(failure, program::destroy);"));
        assertTrue(source.contains("failure = runCleanup(failure, framebuffer::destroy);"));
        assertTrue(source.contains("failure = runCleanup(failure, () -> deleteTexture(texture));"));
        assertTrue(source.contains("failure = runCleanup(failure, () -> deleteTexture(altTexture));"));
    }

    @Test
    public void centerDepthDecayUsesReferenceDecisecondHalfLifeFormula() throws Exception {
        String sampler = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String smoothing = read("src/main/java/net/oculus/uniforms/transforms/ExponentialSmoothing.java");
        String constructor = between(sampler,
            "public CenterDepthSampler(IntSupplier depthSupplier, float halfLife)",
            "/**\n     * Gets the smoothed center-depth texture ID");

        int lastFrameTime = constructor.indexOf(
            "builder.uniforms().addFloat(\"lastFrameTime\", SystemTimeUniforms.TIMER::getLastFrameTime);");
        int decay = constructor.indexOf(
            "builder.uniforms().addFloat(\"decay\", () -> ExponentialSmoothing.decayFromHalfLifeSeconds(halfLife * 0.1F));",
            lastFrameTime);

        assertTrue("Center-depth decay must be registered after the last-frame-time uniform like 1.16.5",
            lastFrameTime >= 0 && decay > lastFrameTime);
        assertTrue("Center-depth decay must convert shader-pack decisecond half-life values to seconds",
            constructor.contains("halfLife * 0.1F"));
        assertTrue("Shared smoothing helper must implement 1.16.5's LN2 / half-life decay constant",
            smoothing.contains("private static final double LN_OF_2 = Math.log(2.0);")
                && smoothing.contains("return (float) (1.0F / (halfLifeSeconds / LN_OF_2));"));
    }

    @Test
    public void destroyAggregatesCleanupFailuresAcrossOwnedResources() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String destroy = between(source, "public void destroy()", "private static void deleteTexture");

        int destroyedGuard = destroy.indexOf("if (destroyed)");
        int tryBlock = destroy.indexOf("try {", destroyedGuard);
        int failureLocal = destroy.indexOf("Throwable failure = null;", tryBlock);
        int destroyProgram = destroy.indexOf("failure = runCleanup(failure, program::destroy);", failureLocal);
        int destroyFramebuffer = destroy.indexOf("failure = runCleanup(failure, framebuffer::destroy);",
            destroyProgram);
        int deleteTexture = destroy.indexOf("failure = runCleanup(failure, () -> deleteTexture(texture));",
            destroyFramebuffer);
        int deleteAltTexture = destroy.indexOf("failure = runCleanup(failure, () -> deleteTexture(altTexture));",
            deleteTexture);
        int rethrow = destroy.indexOf("rethrowCleanupFailure(failure);", deleteAltTexture);
        int finallyBlock = destroy.indexOf("} finally {", rethrow);
        int markDestroyed = destroy.indexOf("destroyed = true;", finallyBlock);

        assertTrue(destroyedGuard >= 0);
        assertTrue(tryBlock > destroyedGuard);
        assertTrue("Center-depth destroy must aggregate from the first owned cleanup path",
            failureLocal > destroyedGuard && destroyProgram > failureLocal);
        assertTrue("Center-depth destroy must attempt framebuffer and texture cleanup after program cleanup",
            destroyFramebuffer > destroyProgram && deleteTexture > destroyFramebuffer
                && deleteAltTexture > deleteTexture);
        assertTrue("Center-depth destroy must rethrow only after every owned cleanup path has run",
            rethrow > deleteAltTexture);
        assertTrue("Center-depth sampler must mark destroyed from finally even when cleanup fails",
            finallyBlock > rethrow && markDestroyed > finallyBlock);
        assertTrue(source.contains("failure.addSuppressed(exception);"));
        assertTrue(source.contains("throw (RuntimeException) failure;"));
        assertTrue(source.contains("throw (Error) failure;"));
    }

    @Test
    public void samplingAfterDestroyFailsClearlyInsteadOfSilentlySkipping() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String sample = between(source, "public void sampleCenterDepth()", "private static Throwable cleanupAfterSample");

        int destroyedGuard = sample.indexOf("if (destroyed)");
        int throwDestroyed = sample.indexOf(
            "throw new IllegalStateException(\"Cannot sample center depth after the center-depth sampler was destroyed\");",
            destroyedGuard);
        int unusedFirstSampleGuard = sample.indexOf("if (hasFirstSample && !hasUsage)", throwDestroyed);
        int unusedReturn = sample.indexOf("return;", unusedFirstSampleGuard);
        int markFirstSample = sample.indexOf("hasFirstSample = true;", unusedReturn);

        assertTrue("Destroyed center-depth sampling must fail clearly instead of silently skipping",
            destroyedGuard >= 0 && throwDestroyed > destroyedGuard);
        assertTrue("The no-usage fast path should remain separate from the destroyed-resource failure",
            unusedFirstSampleGuard > throwDestroyed && unusedReturn > unusedFirstSampleGuard
                && markFirstSample > unusedReturn);
        assertFalse(sample.contains("(hasFirstSample && !hasUsage) || destroyed"));
    }

    @Test
    public void samplerBindingAccessorsFailClearlyAfterDestroy() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String getTexture = between(source, "public int getCenterDepthTexture()", "public void setUsage");
        String setUsage = between(source, "public void setUsage(boolean usage)", "public boolean hasUsage");
        String hasUsage = between(source, "public boolean hasUsage()", "public void sampleCenterDepth");

        int getGuard = getTexture.indexOf("if (destroyed)");
        int getThrow = getTexture.indexOf(
            "throw new IllegalStateException(\"Cannot bind center depth after the center-depth sampler was destroyed\");",
            getGuard);
        int returnTexture = getTexture.indexOf("return altTexture;", getThrow);
        int setGuard = setUsage.indexOf("if (destroyed)");
        int setThrow = setUsage.indexOf(
            "throw new IllegalStateException(\"Cannot update center-depth usage after the center-depth sampler was destroyed\");",
            setGuard);
        int setState = setUsage.indexOf("this.hasUsage |= usage;", setThrow);
        int hasGuard = hasUsage.indexOf("if (destroyed)");
        int hasThrow = hasUsage.indexOf(
            "throw new IllegalStateException(\"Cannot query center-depth usage after the center-depth sampler was destroyed\");",
            hasGuard);
        int returnUsage = hasUsage.indexOf("return hasUsage;", hasThrow);

        assertTrue("Destroyed center-depth texture binding must fail before returning a deleted texture id",
            getGuard >= 0 && getThrow > getGuard && returnTexture > getThrow);
        assertTrue("Destroyed center-depth usage updates must fail before mutating usage state",
            setGuard >= 0 && setThrow > setGuard && setState > setThrow);
        assertTrue("Destroyed center-depth usage queries must fail clearly",
            hasGuard >= 0 && hasThrow > hasGuard && returnUsage > hasThrow);
    }

    @Test
    public void textureDeleteNotifiesLifecycleEvenWhenGlDeleteFails() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String deleteTexture = between(source, "private static void deleteTexture", "private static Throwable destroyCreatedResources");

        int positiveGuard = deleteTexture.indexOf("if (texture <= 0)");
        int failureLocal = deleteTexture.indexOf("Throwable failure = null;", positiveGuard);
        int tryBlock = deleteTexture.indexOf("try {", failureLocal);
        int glDelete = deleteTexture.indexOf("GL11.glDeleteTextures(texture);", tryBlock);
        int catchDelete = deleteTexture.indexOf("catch (RuntimeException | Error exception)", glDelete);
        int collectDelete = deleteTexture.indexOf("failure = addCleanupFailure(failure, exception);", catchDelete);
        int finallyBlock = deleteTexture.indexOf("} finally {", collectDelete);
        int tryLifecycle = deleteTexture.indexOf("try {", finallyBlock);
        int trackerNotify = deleteTexture.indexOf("TextureLifecycleTracker.onDeleteTexture(texture);", tryLifecycle);
        int catchTracker = deleteTexture.indexOf("catch (RuntimeException | Error exception)", trackerNotify);
        int collectTracker = deleteTexture.indexOf("failure = addCleanupFailure(failure, exception);", catchTracker);
        int rethrow = deleteTexture.indexOf("rethrowCleanupFailure(failure);", collectTracker);

        assertTrue(positiveGuard >= 0);
        assertTrue(failureLocal > positiveGuard);
        assertTrue(glDelete > tryBlock);
        assertTrue(catchDelete > glDelete);
        assertTrue(collectDelete > catchDelete);
        assertTrue("Center-depth texture lifecycle cleanup must run after GL delete failure capture",
            finallyBlock > collectDelete);
        assertTrue(tryLifecycle > finallyBlock);
        assertTrue(trackerNotify > tryLifecycle);
        assertTrue(catchTracker > trackerNotify);
        assertTrue(collectTracker > catchTracker);
        assertTrue("Center-depth delete must rethrow only after lifecycle invalidation is attempted",
            rethrow > collectTracker);
    }

    @Test
    public void samplingCopyUsesReferenceStyleRenderSystemHelperAndReturnsToMainFramebuffer() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String sampling = between(source, "public void sampleCenterDepth()", "private static void bindMainFramebufferForPostprocess");

        int unbindProgram = sampling.indexOf("Program.unbind();");
        int strategy = sampling.indexOf("DepthCopyStrategy.fastest(DepthBufferFormat.DEPTH)", unbindProgram);
        int copy = sampling.indexOf(".copy(framebuffer, texture, null, altTexture, 1, 1);", strategy);
        int bindMain = sampling.indexOf("CenterDepthSampler::bindMainFramebufferForPostprocess", copy);

        assertTrue(unbindProgram >= 0);
        assertTrue(strategy > unbindProgram);
        assertTrue(copy > strategy);
        assertTrue(bindMain > copy);
        assertTrue(source.contains("mainFramebuffer.bindFramebuffer(true);"));
        assertTrue(source.contains("OculusRenderSystem.restoreFramebufferBindings(0, 0, 0);"));
        assertFalse(source.contains("OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);"));
        assertTrue(source.contains("import net.oculus.gl.texture.DepthBufferFormat;"));
        assertTrue(source.contains("import net.oculus.rendertarget.DepthCopyStrategy;"));
        assertFalse(sampling.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(sampling.contains("GL11.glCopyTexSubImage2D"));
        assertFalse(source.contains("GL30.GL_FRAMEBUFFER_BINDING"));
        assertFalse(source.contains("GL11.glGetInteger(GL11.GL_VIEWPORT"));
    }

    @Test
    public void samplingUnbindsProgramAndClearsProgramCachesBeforeDepthCopy() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");

        int begin = source.indexOf("FullScreenQuadRenderer.INSTANCE.begin();");
        int render = source.indexOf("FullScreenQuadRenderer.INSTANCE.renderQuad();", begin);
        int end = source.indexOf("FullScreenQuadRenderer.INSTANCE.end();", render);
        int unbindProgram = source.indexOf("Program.unbind();", end);
        int copy = source.indexOf("DepthCopyStrategy.fastest(DepthBufferFormat.DEPTH)", unbindProgram);

        assertTrue(begin >= 0);
        assertTrue(render > begin);
        assertTrue(end > render);
        assertTrue(unbindProgram > end);
        assertTrue(copy > unbindProgram);
    }

    @Test
    public void samplingCleanupRunsFromFinallyAndRestoresCallerActiveTexture() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String sampling = between(source, "public void sampleCenterDepth()", "private static Throwable cleanupAfterSample");
        String cleanup = between(source, "private static Throwable cleanupAfterSample", "private static void restoreBlendAlphaState");

        int saveActiveTexture = sampling.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int fullscreenFlag = sampling.indexOf("boolean fullscreenQuadBegun = false;", saveActiveTexture);
        int programClearedFlag = sampling.indexOf("boolean programStateCleared = false;", fullscreenFlag);
        int failureLocal = sampling.indexOf("Throwable failure = null;", programClearedFlag);
        int begin = sampling.indexOf("FullScreenQuadRenderer.INSTANCE.begin();", programClearedFlag);
        int markBegun = sampling.indexOf("fullscreenQuadBegun = true;", begin);
        int renderQuad = sampling.indexOf("FullScreenQuadRenderer.INSTANCE.renderQuad();", markBegun);
        int normalEnd = sampling.indexOf("FullScreenQuadRenderer.INSTANCE.end();", renderQuad);
        int markEnded = sampling.indexOf("fullscreenQuadBegun = false;", normalEnd);
        int markProgramCleared = sampling.indexOf("programStateCleared = true;", markEnded);
        int catchFailure = sampling.indexOf("catch (RuntimeException | Error exception)", markProgramCleared);
        int rememberFailure = sampling.indexOf("failure = exception;", catchFailure);
        int finallyBlock = sampling.indexOf("finally {", markProgramCleared);
        int cleanupFailureLocal = sampling.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int guardedEnd = sampling.indexOf("cleanupFailure = runCleanup(cleanupFailure, () -> FullScreenQuadRenderer.INSTANCE.end());",
            cleanupFailureLocal);
        int cleanupCall = sampling.indexOf(
            "cleanupFailure = cleanupAfterSample(cleanupFailure, programStateCleared, previousActiveTexture,",
            guardedEnd);
        int suppressCleanup = sampling.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);",
            cleanupCall);
        int rethrowCleanup = sampling.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressCleanup);

        assertTrue("Center-depth sampling must save the caller active texture unit",
            saveActiveTexture >= 0);
        assertTrue(fullscreenFlag > saveActiveTexture);
        assertTrue(programClearedFlag > fullscreenFlag);
        assertTrue("Center-depth sampling must track primary failures before render work starts",
            failureLocal > programClearedFlag);
        assertTrue("Fullscreen quad scope must be tracked before rendering",
            begin > programClearedFlag && markBegun > begin && renderQuad > markBegun);
        assertTrue("Normal fullscreen cleanup must clear the tracked scope before post-copy work",
            normalEnd > renderQuad && markEnded > normalEnd);
        assertTrue("Program state should be marked clear only after normal program unbind",
            markProgramCleared > markEnded);
        assertTrue("Primary sampling failures must be remembered before cleanup runs",
            catchFailure > markProgramCleared && rememberFailure > catchFailure);
        assertTrue("Failed fullscreen rendering must still close the fullscreen scope",
            finallyBlock > rememberFailure && cleanupFailureLocal > finallyBlock && guardedEnd > cleanupFailureLocal
                && cleanupCall > guardedEnd);
        assertTrue("Cleanup failures must be suppressed onto primary failures instead of replacing them",
            suppressCleanup > cleanupCall && rethrowCleanup > suppressCleanup);

        int cleanupGuard = cleanup.indexOf("if (!programStateCleared)");
        int unbindProgram = cleanup.indexOf("failure = runCleanup(failure, Program::unbind);",
            cleanupGuard);
        int bindMain = cleanup.indexOf("failure = runCleanup(failure, CenterDepthSampler::bindMainFramebufferForPostprocess);",
            unbindProgram);
        int restoreActive = cleanup.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)",
            bindMain);
        int returnFailure = cleanup.indexOf("return failure;", restoreActive);

        assertTrue("Failure cleanup must unbind the active program when normal cleanup did not run",
            cleanupGuard >= 0 && unbindProgram > cleanupGuard);
        assertTrue("Center-depth cleanup must aggregate main-framebuffer restoration after program cleanup",
            bindMain > unbindProgram);
        assertTrue("Center-depth cleanup must restore the caller active texture unit after framebuffer restore",
            restoreActive > bindMain);
        assertTrue("Center-depth cleanup must return the aggregate cleanup failure after all restoration attempts",
            returnFailure > restoreActive);
    }

    @Test
    public void samplingForcesAndRestoresBlendAlphaAndColorMaskForCenterDepthTexture() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");
        String sampling = between(source, "public void sampleCenterDepth()", "private static Throwable cleanupAfterSample");
        String cleanup = between(source, "private static Throwable cleanupAfterSample", "private static void restoreBlendAlphaState");
        String restoreBlendAlpha = between(source, "private static void restoreBlendAlphaState", "private static void restoreColorMask");
        String restoreColorMask = between(source, "private static void restoreColorMask", "private static void bindMainFramebufferForPostprocess");

        int saveActiveTexture = sampling.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int saveBlend = sampling.indexOf("boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);",
            saveActiveTexture);
        int saveAlpha = sampling.indexOf("boolean alphaWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);",
            saveBlend);
        int allocateColorMask = sampling.indexOf("ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);",
            saveAlpha);
        int readColorMask = sampling.indexOf("GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);",
            allocateColorMask);
        int viewport = sampling.indexOf("GL11.glViewport(0, 0, 1, 1);", readColorMask);
        int disableBlend = sampling.indexOf("GlStateManager.disableBlend();", viewport);
        int disableAlpha = sampling.indexOf("GlStateManager.disableAlpha();", disableBlend);
        int forceColorMask = sampling.indexOf("GlStateManager.colorMask(true, true, true, true);", disableAlpha);
        int renderQuad = sampling.indexOf("FullScreenQuadRenderer.INSTANCE.renderQuad();", forceColorMask);
        int cleanupCall = sampling.indexOf(
            "cleanupAfterSample(cleanupFailure, programStateCleared, previousActiveTexture,",
            renderQuad);
        int cleanupAlphaArg = sampling.indexOf("alphaWasEnabled, previousColorMask", cleanupCall);

        assertTrue("Center-depth sampling must save incoming blend state after active texture state",
            saveBlend > saveActiveTexture);
        assertTrue("Center-depth sampling must save incoming alpha-test state after blend state",
            saveAlpha > saveBlend);
        assertTrue("Center-depth sampling must allocate and read caller color-mask state before rendering",
            allocateColorMask > saveAlpha && readColorMask > allocateColorMask);
        assertTrue("Center-depth sampling must force deterministic draw state before rendering its 1x1 texture",
            disableBlend > viewport && disableAlpha > disableBlend && forceColorMask > disableAlpha
                && renderQuad > forceColorMask);
        assertTrue("Center-depth cleanup must receive all saved draw state from the finally path",
            cleanupCall > renderQuad && cleanupAlphaArg > cleanupCall);

        int restoreActive = cleanup.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)");
        int restoreBlendAlphaCall = cleanup.indexOf("restoreBlendAlphaState(blendWasEnabled, alphaWasEnabled)",
            restoreActive);
        int restoreColorMaskCall = cleanup.indexOf("restoreColorMask(previousColorMask)", restoreBlendAlphaCall);

        assertTrue("Center-depth cleanup must restore active texture before caller draw state",
            restoreActive >= 0 && restoreBlendAlphaCall > restoreActive);
        assertTrue("Center-depth cleanup must restore the color mask even if blend/alpha restore fails",
            restoreColorMaskCall > restoreBlendAlphaCall);
        assertTrue(restoreBlendAlpha.contains("GlStateManager.enableBlend();"));
        assertTrue(restoreBlendAlpha.contains("GlStateManager.disableBlend();"));
        assertTrue(restoreBlendAlpha.contains("GlStateManager.enableAlpha();"));
        assertTrue(restoreBlendAlpha.contains("GlStateManager.disableAlpha();"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(0) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(1) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(2) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(3) != 0"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0);
        assertTrue(endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }
}
