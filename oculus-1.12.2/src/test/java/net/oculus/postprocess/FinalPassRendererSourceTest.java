package net.oculus.postprocess;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class FinalPassRendererSourceTest {
    @Test
    public void finalPassDepthTestIsOwnedByFullscreenQuadRenderer() throws Exception {
        String finalPassSource = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String fullscreenQuadSource = read("src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java");

        assertFalse(finalPassSource.contains("GlStateManager.disableDepth();"));
        assertTrue(finalPassSource.contains("GlStateManager.depthMask(false);"));
        assertTrue(fullscreenQuadSource.contains("GlStateManager.disableDepth();"));
        assertTrue(fullscreenQuadSource.contains("GlStateManager.enableDepth();"));
    }

    @Test
    public void fullscreenQuadDisablesDepthBeforeMatrixSetupLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java");

        int disableDepth = source.indexOf("GlStateManager.disableDepth();");
        int projectionMode = source.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);", disableDepth);
        int projectionPush = source.indexOf("GlStateManager.pushMatrix();", projectionMode);
        int modelViewMode = source.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW);", projectionPush);
        int modelViewLoad = source.indexOf("GlStateManager.loadIdentity();", modelViewMode);
        int color = source.indexOf("GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);", modelViewLoad);

        assertTrue(disableDepth >= 0);
        assertTrue(projectionMode > disableDepth);
        assertTrue(projectionPush > projectionMode);
        assertTrue(modelViewMode > projectionPush);
        assertTrue(modelViewLoad > modelViewMode);
        assertTrue(color > modelViewLoad);
    }

    @Test
    public void fullscreenQuadEnablesDepthBeforeMatrixRestoreLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java");

        int end = source.indexOf("public static void end()");
        int enableDepth = source.indexOf("GlStateManager.enableDepth();", end);
        int projectionMode = source.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);", enableDepth);
        int projectionPop = source.indexOf("GlStateManager.popMatrix();", projectionMode);
        int modelViewMode = source.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW);", projectionPop);
        int modelViewPop = source.indexOf("GlStateManager.popMatrix();", modelViewMode);

        assertTrue(end >= 0);
        assertTrue(enableDepth > end);
        assertTrue(projectionMode > enableDepth);
        assertTrue(projectionPop > projectionMode);
        assertTrue(modelViewMode > projectionPop);
        assertTrue(modelViewPop > modelViewMode);
    }

    @Test
    public void fullscreenQuadBeginAndEndKeepRestoringAfterPartialFailures() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java");
        String begin = between(source, "public void begin()", "    /**\n     * Restores OpenGL state");
        String end = between(source, "public static void end()", "private static Throwable rollbackBeginFailure");
        String rollback = between(source, "private static Throwable rollbackBeginFailure",
            "private static Throwable runCleanup");
        String helpers = source.substring(source.indexOf("private static Throwable runCleanup"));

        int depthFlag = begin.indexOf("boolean depthDisabled = false;");
        int projectionModeFlag = begin.indexOf("boolean projectionModeSelected = false;", depthFlag);
        int projectionFlag = begin.indexOf("boolean projectionPushed = false;", projectionModeFlag);
        int modelViewFlag = begin.indexOf("boolean modelViewPushed = false;", projectionFlag);
        int tryBlock = begin.indexOf("try {", modelViewFlag);
        int disableDepth = begin.indexOf("GlStateManager.disableDepth();", tryBlock);
        int markDepth = begin.indexOf("depthDisabled = true;", disableDepth);
        int projectionMode = begin.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);", markDepth);
        int markProjectionMode = begin.indexOf("projectionModeSelected = true;", projectionMode);
        int projectionPush = begin.indexOf("GlStateManager.pushMatrix();", markProjectionMode);
        int markProjection = begin.indexOf("projectionPushed = true;", projectionPush);
        int modelViewPush = begin.indexOf("GlStateManager.pushMatrix();", markProjection);
        int markModelView = begin.indexOf("modelViewPushed = true;", modelViewPush);
        int catchBlock = begin.indexOf("} catch (RuntimeException | Error exception) {", markModelView);
        int rollbackCall = begin.indexOf(
            "rollbackBeginFailure(depthDisabled, projectionModeSelected, projectionPushed, modelViewPushed)",
            catchBlock);
        int rethrow = begin.indexOf("throw exception;", rollbackCall);

        assertTrue("Fullscreen begin must track each state mutation before it can fail",
            depthFlag >= 0 && projectionModeFlag > depthFlag && projectionFlag > projectionModeFlag
                && modelViewFlag > projectionFlag
                && tryBlock > modelViewFlag);
        assertTrue("Fullscreen begin must mark depth and matrix pushes before later setup work",
            disableDepth > tryBlock && markDepth > disableDepth
                && projectionMode > markDepth && markProjectionMode > projectionMode
                && projectionPush > markDepth && markProjection > projectionPush
                && modelViewPush > markProjection && markModelView > modelViewPush);
        assertTrue("Fullscreen begin failures must rollback partial state and preserve the original failure",
            catchBlock > markModelView && rollbackCall > catchBlock && rethrow > rollbackCall);

        int modelGuard = rollback.indexOf("if (modelViewPushed)");
        int modelMode = rollback.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW)", modelGuard);
        int modelPop = rollback.indexOf("GlStateManager::popMatrix", modelMode);
        int projectionGuard = rollback.indexOf("if (projectionPushed)", modelPop);
        int rollbackProjectionMode = rollback.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION)", projectionGuard);
        int projectionPop = rollback.indexOf("GlStateManager::popMatrix", rollbackProjectionMode);
        int returnModelMode = rollback.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW)", projectionPop);
        int projectionModeOnly = rollback.indexOf("} else if (projectionModeSelected)", returnModelMode);
        int returnModelWithoutPush = rollback.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW)",
            projectionModeOnly);
        int depthGuard = rollback.indexOf("if (depthDisabled)", returnModelWithoutPush);
        int enableDepth = rollback.indexOf("GlStateManager::enableDepth", depthGuard);
        assertTrue("Fullscreen begin rollback must unwind model-view, projection, then depth state",
            modelGuard >= 0 && modelMode > modelGuard && modelPop > modelMode
                && projectionGuard > modelPop && rollbackProjectionMode > projectionGuard
                && projectionPop > rollbackProjectionMode && returnModelMode > projectionPop
                && projectionModeOnly > returnModelMode && returnModelWithoutPush > projectionModeOnly
                && depthGuard > returnModelWithoutPush && enableDepth > depthGuard);

        int endFailure = end.indexOf("Throwable failure = null;");
        int endEnableDepth = end.indexOf("GlStateManager.enableDepth();", endFailure);
        int endEnableCatch = end.indexOf("failure = addCleanupFailure(failure, exception);", endEnableDepth);
        int endProjectionMode = end.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);", endEnableCatch);
        int endProjectionPop = end.indexOf("GlStateManager.popMatrix();", endProjectionMode);
        int endModelMode = end.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW);", endProjectionPop);
        int endModelPop = end.indexOf("GlStateManager.popMatrix();", endModelMode);
        int endRethrow = end.indexOf("rethrowCleanupFailure(failure);", endModelPop);
        assertTrue("Fullscreen end must attempt every restore step before rethrowing cleanup failures",
            endFailure >= 0 && endEnableDepth > endFailure && endEnableCatch > endEnableDepth
                && endProjectionMode > endEnableCatch && endProjectionPop > endProjectionMode
                && endModelMode > endProjectionPop && endModelPop > endModelMode
                && endRethrow > endModelPop);

        assertTrue(helpers.contains("failure.addSuppressed(exception);"));
        assertTrue(helpers.contains("primary.addSuppressed(cleanupFailure);"));
        assertTrue(helpers.contains("throw (RuntimeException) failure;"));
        assertTrue(helpers.contains("throw (Error) failure;"));
    }

    @Test
    public void fullscreenQuadConvenienceRenderEndsFromFinallyAndPreservesPrimaryFailures() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java");
        String render = source.substring(source.indexOf("public void render()"));

        int begunFlag = render.indexOf("boolean begun = false;");
        int failureLocal = render.indexOf("Throwable failure = null;", begunFlag);
        int tryBlock = render.indexOf("try {", failureLocal);
        int beginCall = render.indexOf("begin();", tryBlock);
        int markBegun = render.indexOf("begun = true;", beginCall);
        int renderQuad = render.indexOf("renderQuad();", markBegun);
        int catchBlock = render.indexOf("} catch (RuntimeException | Error exception) {", renderQuad);
        int recordFailure = render.indexOf("failure = exception;", catchBlock);
        int rethrow = render.indexOf("throw exception;", recordFailure);
        int finallyBlock = render.indexOf("} finally {", rethrow);
        int begunGuard = render.indexOf("if (begun)", finallyBlock);
        int cleanupFailure = render.indexOf(
            "Throwable cleanupFailure = runCleanup(null, FullScreenQuadRenderer::end);", begunGuard);
        int suppressCleanup = render.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", cleanupFailure);
        int rethrowCleanupOnly = render.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressCleanup);

        assertTrue("Fullscreen convenience render must only end if begin completed",
            begunFlag >= 0 && failureLocal > begunFlag && tryBlock > failureLocal
                && beginCall > tryBlock && markBegun > beginCall && renderQuad > markBegun);
        assertTrue("Fullscreen convenience render must preserve renderQuad failures before cleanup",
            catchBlock > renderQuad && recordFailure > catchBlock && rethrow > recordFailure);
        assertTrue("Fullscreen convenience render must run end from finally and aggregate cleanup failures",
            finallyBlock > rethrow && begunGuard > finallyBlock && cleanupFailure > begunGuard
                && suppressCleanup > cleanupFailure && rethrowCleanupOnly > suppressCleanup);
    }

    @Test
    public void finalRasterPassBeginsFullscreenScopeBeforeFinalComputeDispatch() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        int colorHolderBind = source.indexOf("colorHolder.bind();");
        int begin = source.indexOf("FullScreenQuadRenderer.INSTANCE.begin();", colorHolderBind);
        int dispatch = source.indexOf("dispatchComputes(viewportWidth, viewportHeight);", colorHolderBind);
        int barrier = source.indexOf("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);", dispatch);
        int renderQuad = source.indexOf("FullScreenQuadRenderer.INSTANCE.renderQuad();", barrier);
        int end = source.indexOf("FullScreenQuadRenderer.INSTANCE.end();", renderQuad);

        assertTrue(colorHolderBind >= 0);
        assertTrue(begin > colorHolderBind);
        assertTrue(dispatch > begin);
        assertTrue(barrier > dispatch);
        assertTrue(renderQuad > barrier);
        assertTrue(end > renderQuad);
    }

    @Test
    public void finalComputeBarrierIsUnconditionalBeforeMipmapsAndProgramUseLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");

        int finalProgramBranch = render.indexOf("if (program != null)");
        int dispatch = render.indexOf("dispatchComputes(viewportWidth, viewportHeight);", finalProgramBranch);
        int barrier = render.indexOf("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);", dispatch);
        int mipmapGuard = render.indexOf("if (!mipmappedBuffers.isEmpty())", barrier);
        int programUse = render.indexOf("program.use();", mipmapGuard);
        int renderQuad = render.indexOf("FullScreenQuadRenderer.INSTANCE.renderQuad();", programUse);
        int passthroughBranch = render.indexOf("} else {", renderQuad);

        assertTrue("Final pass must dispatch final computes inside the custom final raster branch",
            finalProgramBranch >= 0 && dispatch > finalProgramBranch);
        assertTrue("Final pass must issue the 1.16.5-style barrier immediately after final compute dispatch",
            barrier > dispatch && mipmapGuard > barrier);
        assertTrue("Final mipmap setup and raster program use must remain after the compute barrier",
            programUse > mipmapGuard && renderQuad > programUse);
        assertTrue("The passthrough branch must start only after the custom final raster branch",
            passthroughBranch > renderQuad);
        assertFalse("Final pass barriers are intentionally not gated by a ran-compute boolean in 1.16.5",
            render.substring(finalProgramBranch, passthroughBranch).contains("if (ranCompute)"));
    }

    @Test
    public void finalComputesAreOnlyCreatedForARealFinalRasterPassLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String constructorBody = between(source,
            "public FinalPassRenderer(PackDirectives packDirectives,",
            "private void bindCenterDepthSmooth");

        int passthroughBranch = constructorBody.indexOf("if (source == null || !source.isValid())");
        int passthroughLog = constructorBody.indexOf("LOGGER.info(\"No final pass source provided, using passthrough\");",
            passthroughBranch);
        int finalSourceBranch = constructorBody.indexOf("} else {", passthroughLog);
        int buildProgram = constructorBody.indexOf("createdProgram = builder.build();", finalSourceBranch);
        int createComputes = constructorBody.indexOf("createdComputes = createComputes(computeSources,",
            buildProgram);
        int createBaseline = constructorBody.indexOf("createdBaseline = renderTargets.createGbufferFramebuffer",
            createComputes);

        assertTrue("The passthrough final path must not construct final compute programs",
            passthroughBranch >= 0 && passthroughLog > passthroughBranch && finalSourceBranch > passthroughLog);
        assertTrue("Final computes must be staged only after the final raster program is built",
            buildProgram > finalSourceBranch && createComputes > buildProgram);
        assertTrue("Final framebuffer setup must happen after final raster/compute setup",
            createBaseline > createComputes);
        assertFalse("The passthrough branch must not call createComputes before the final source branch",
            constructorBody.substring(passthroughBranch, finalSourceBranch).contains("createComputes("));
    }

    @Test
    public void finalPassthroughCopyDoesNotDispatchFinalComputes() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");

        int finalProgramBranch = render.indexOf("if (program != null)");
        int dispatch = render.indexOf("dispatchComputes(viewportWidth, viewportHeight);", finalProgramBranch);
        int barrier = render.indexOf("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);", dispatch);
        int passthroughBranch = render.indexOf("} else {", barrier);
        int copyBaseline = render.indexOf("copyBaselineToMain(mainFramebuffer, viewportWidth, viewportHeight);",
            passthroughBranch);
        int afterProgramBranch = render.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();",
            copyBaseline);

        assertTrue("Final computes must dispatch only inside the custom final raster branch",
            finalProgramBranch >= 0 && dispatch > finalProgramBranch && barrier > dispatch);
        assertTrue("The passthrough branch must copy colortex0 to the main framebuffer without final computes",
            passthroughBranch > barrier && copyBaseline > passthroughBranch);
        assertTrue(afterProgramBranch > copyBaseline);
        assertFalse(render.substring(passthroughBranch, afterProgramBranch).contains("dispatchComputes("));
    }

    @Test
    public void finalPassRenderFailsClearlyAfterDestroyInsteadOfSilentlySkipping() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");

        int guard = render.indexOf("if (destroyed)");
        int failClearly = render.indexOf(
            "throw new IllegalStateException(\"Cannot render a destroyed final pass renderer\");", guard);
        int requireFramebuffer = render.indexOf("Framebuffer mainFramebuffer = requireMainFramebuffer(minecraft);",
            failClearly);

        assertTrue("Destroyed final-pass rendering must fail clearly instead of becoming a silent no-op",
            guard >= 0 && failClearly > guard && requireFramebuffer > failClearly);
        assertFalse(render.contains("if (destroyed) {\n            return;\n        }"));
    }

    @Test
    public void finalPassResizeFailsClearlyAfterDestroyInsteadOfSilentlySkipping() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String resize = between(source, "public void recalculateSwapPassSize()",
            "private void destroySwapPassReplacements");

        int guard = resize.indexOf("if (destroyed)");
        int failClearly = resize.indexOf(
            "throw new IllegalStateException(\"Cannot resize a destroyed final pass renderer\");", guard);
        int replacements = resize.indexOf("List<SwapPassReplacement> replacements = new ArrayList<>();",
            failClearly);

        assertTrue("Destroyed final-pass resize must fail clearly before staging replacement framebuffers",
            guard >= 0 && failClearly > guard && replacements > failClearly);
        assertFalse("Destroyed final-pass resize must not silently no-op",
            resize.contains("if (destroyed) {\n            return;\n        }"));
    }

    @Test
    public void finalPassDoesNotApplyCompositeViewportScale() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertFalse(source.contains("getViewportScale()"));
        assertFalse(source.contains("viewportScale"));
        assertFalse(source.contains("getDrawBuffers()"));
        assertFalse(source.contains("GL11.glViewport(0, 0, scaledWidth, scaledHeight);"));
    }

    @Test
    public void finalPassClearsProgramStateAfterMipmapResetAndSwapCopies() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertFalse(source.contains("Program.unbind();"));

        int renderQuad = source.indexOf("FullScreenQuadRenderer.INSTANCE.renderQuad();");
        int end = source.indexOf("FullScreenQuadRenderer.INSTANCE.end();", renderQuad);
        int reset = source.indexOf("resetRenderTargetMipmaps();", end);
        int swaps = source.indexOf("runSwapPasses();", reset);
        int clearUniforms = source.indexOf("ProgramUniforms::clearActiveUniforms", swaps);
        int clearSamplers = source.indexOf("ProgramSamplers::clearActiveSamplers", clearUniforms);
        int clearImages = source.indexOf("ProgramImages::clearActiveImages", clearSamplers);
        int glUseProgramZero = source.indexOf("GL20.glUseProgram(0)", clearImages);

        assertTrue(renderQuad >= 0);
        assertTrue(end > renderQuad);
        assertTrue(reset > end);
        assertTrue(swaps > reset);
        assertTrue(clearUniforms > swaps);
        assertTrue(clearSamplers > clearUniforms);
        assertTrue(clearImages > clearSamplers);
        assertTrue(glUseProgramZero > clearImages);
    }

    @Test
    public void finalPassRestoresFullscreenAndSamplerStateFromFinally() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");
        String restore = between(source, "private Throwable restoreAfterRender", "private void dispatchComputes");

        int mainFramebuffer = render.indexOf("Framebuffer mainFramebuffer = requireMainFramebuffer(minecraft);");
        int flag = render.indexOf("boolean fullscreenQuadBegun = false;", mainFramebuffer);
        int tryBlock = render.indexOf("try {", flag);
        int begin = render.indexOf("FullScreenQuadRenderer.INSTANCE.begin();", tryBlock);
        int markBegun = render.indexOf("fullscreenQuadBegun = true;", begin);
        int renderQuad = render.indexOf("FullScreenQuadRenderer.INSTANCE.renderQuad();", markBegun);
        int normalEnd = render.indexOf("FullScreenQuadRenderer.INSTANCE.end();", renderQuad);
        int markEnded = render.indexOf("fullscreenQuadBegun = false;", normalEnd);
        int resetMipmaps = render.indexOf("resetRenderTargetMipmaps();", markEnded);
        int runSwaps = render.indexOf("runSwapPasses();", resetMipmaps);
        int finallyBlock = render.indexOf("finally {", runSwaps);
        int guardedEnd = render.indexOf("FullScreenQuadRenderer.INSTANCE.end();", finallyBlock);
        int restoreCall = render.indexOf(
            "cleanupFailure = restoreAfterRender(cleanupFailure, mainFramebuffer, previousDepthMask, blendWasEnabled,",
            guardedEnd);
        int restoreActiveTextureArg = render.indexOf("previousActiveTexture", restoreCall);

        assertTrue(mainFramebuffer >= 0);
        assertTrue(flag > mainFramebuffer);
        assertTrue(tryBlock > flag);
        assertTrue(begin > tryBlock);
        assertTrue(markBegun > begin);
        assertTrue(renderQuad > markBegun);
        assertTrue(normalEnd > renderQuad);
        assertTrue(markEnded > normalEnd);
        assertTrue(resetMipmaps > markEnded);
        assertTrue(runSwaps > resetMipmaps);
        assertTrue(finallyBlock > runSwaps);
        assertTrue(guardedEnd > finallyBlock);
        assertTrue(restoreCall > guardedEnd);
        assertTrue(restoreActiveTextureArg > restoreCall);
        assertTrue(restore.contains("mainFramebuffer.bindFramebuffer(true)"));
        assertTrue(restore.contains("ProgramUniforms::clearActiveUniforms"));
        assertTrue(restore.contains("ProgramSamplers::clearActiveSamplers"));
        assertTrue(restore.contains("ProgramImages::clearActiveImages"));
        assertTrue(restore.contains("GL20.glUseProgram(0)"));
        assertTrue(restore.contains("FinalPassRenderer::unbindAllSamplerTextures"));
        assertTrue(restore.contains("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)"));
    }

    @Test
    public void finalPassRenderPreservesPrimaryFailureWhenCleanupFails() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");

        int failureLocal = render.indexOf("Throwable failure = null;");
        int primaryCatch = render.indexOf("} catch (RuntimeException | Error exception) {", failureLocal);
        int recordPrimary = render.indexOf("failure = exception;", primaryCatch);
        int rethrowPrimary = render.indexOf("throw exception;", recordPrimary);
        int finallyBlock = render.indexOf("finally {", rethrowPrimary);
        int cleanupFailure = render.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int fullscreenCleanup = render.indexOf("FullScreenQuadRenderer.INSTANCE.end();", cleanupFailure);
        int fullscreenCatch = render.indexOf("cleanupFailure = addCleanupFailure(cleanupFailure, exception);",
            fullscreenCleanup);
        int restoreCall = render.indexOf(
            "cleanupFailure = restoreAfterRender(cleanupFailure, mainFramebuffer, previousDepthMask, blendWasEnabled,",
            fullscreenCatch);
        int suppress = render.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", restoreCall);
        int rethrowCleanupOnly = render.indexOf("rethrowCleanupFailure(cleanupFailure);", suppress);

        assertTrue("Final pass render must record the primary render failure before cleanup",
            failureLocal >= 0 && primaryCatch > failureLocal && recordPrimary > primaryCatch
                && rethrowPrimary > recordPrimary);
        assertTrue("Final pass cleanup must aggregate fullscreen and restore failures",
            finallyBlock > rethrowPrimary && cleanupFailure > finallyBlock && fullscreenCleanup > cleanupFailure
                && fullscreenCatch > fullscreenCleanup && restoreCall > fullscreenCatch);
        assertTrue("Final pass cleanup failures must suppress onto primary failures or rethrow alone",
            suppress > restoreCall && rethrowCleanupOnly > suppress);
    }

    @Test
    public void finalPassRestoresCallerOwnedDrawStateFromFinally() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");
        String restore = between(source, "private Throwable restoreAfterRender", "private void dispatchComputes");

        int saveDepthMask = render.indexOf("boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);");
        int saveBlend = render.indexOf("boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);",
            saveDepthMask);
        int saveAlpha = render.indexOf("boolean alphaWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);",
            saveBlend);
        int saveActiveTexture = render.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);",
            saveAlpha);
        int failureLocal = render.indexOf("Throwable failure = null;", saveActiveTexture);
        int tryBlock = render.indexOf("try {", failureLocal);
        int forceDepthMask = render.indexOf("GlStateManager.depthMask(false);", tryBlock);
        int disableBlend = render.indexOf("GlStateManager.disableBlend();", forceDepthMask);
        int disableAlpha = render.indexOf("GlStateManager.disableAlpha();", disableBlend);
        int finallyBlock = render.indexOf("finally {", disableAlpha);
        int restoreCall = render.indexOf(
            "cleanupFailure = restoreAfterRender(cleanupFailure, mainFramebuffer, previousDepthMask, blendWasEnabled,",
            finallyBlock);

        assertTrue(saveDepthMask >= 0);
        assertTrue(saveBlend > saveDepthMask);
        assertTrue(saveAlpha > saveBlend);
        assertTrue("Final pass must save caller-owned active texture state before mutating samplers",
            saveActiveTexture > saveAlpha);
        assertTrue("Final pass must enter the cleanup-guarded block before mutating draw state",
            failureLocal > saveActiveTexture && tryBlock > failureLocal
                && forceDepthMask > tryBlock && disableBlend > forceDepthMask && disableAlpha > disableBlend);
        assertTrue("Final pass draw-state restoration must run from the cleanup path",
            restoreCall > finallyBlock);

        int clearImages = restore.indexOf("failure = runCleanup(failure, ProgramImages::clearActiveImages);");
        int clearProgram = restore.indexOf("failure = runCleanup(failure, () -> GL20.glUseProgram(0));",
            clearImages);
        int unbindTextures = restore.indexOf("failure = runCleanup(failure, FinalPassRenderer::unbindAllSamplerTextures);",
            clearProgram);
        int restoreActiveTexture = restore.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)",
            unbindTextures);
        int restoreDepthMaskCall = restore.indexOf("GlStateManager.depthMask(previousDepthMask)",
            restoreActiveTexture);
        int restoreBlend = restore.indexOf("restoreBlendState(blendWasEnabled)", restoreDepthMaskCall);
        int restoreAlpha = restore.indexOf("restoreAlphaState(alphaWasEnabled)", restoreBlend);
        int enableBlend = restore.indexOf("GlStateManager.enableBlend();", restoreAlpha);
        int disableBlendRestore = restore.indexOf("GlStateManager.disableBlend();", enableBlend);
        int enableAlpha = restore.indexOf("GlStateManager.enableAlpha();", disableBlendRestore);
        int disableAlphaRestore = restore.indexOf("GlStateManager.disableAlpha();", enableAlpha);

        assertTrue("Draw-state restore should run even if image cleanup fails", clearImages >= 0);
        assertTrue(clearProgram > clearImages);
        assertTrue(unbindTextures > clearProgram);
        assertTrue("Final pass cleanup must restore the caller active texture unit before draw-state restore",
            restoreActiveTexture > unbindTextures && restoreDepthMaskCall > restoreActiveTexture
                && restoreBlend > restoreDepthMaskCall && restoreAlpha > restoreBlend);
        assertTrue(enableBlend > restoreAlpha);
        assertTrue(disableBlendRestore > enableBlend);
        assertTrue(enableAlpha > disableBlendRestore);
        assertTrue(disableAlphaRestore > enableAlpha);
    }

    @Test
    public void finalPassForcesAndRestoresColorMaskFromFinally() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");
        String restore = between(source, "private Throwable restoreAfterRender", "private void dispatchComputes");
        String restoreColorMask = source.substring(
            source.indexOf("private static void restoreColorMask"),
            source.indexOf("private void dispatchComputes"));

        int saveActiveTexture = render.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int allocateColorMask = render.indexOf("ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);",
            saveActiveTexture);
        int readColorMask = render.indexOf("GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);",
            allocateColorMask);
        int tryBlock = render.indexOf("try {", readColorMask);
        int forceDepthMask = render.indexOf("GlStateManager.depthMask(false);", tryBlock);
        int disableBlend = render.indexOf("GlStateManager.disableBlend();", forceDepthMask);
        int disableAlpha = render.indexOf("GlStateManager.disableAlpha();", disableBlend);
        int forceColorMask = render.indexOf("GlStateManager.colorMask(true, true, true, true);", disableAlpha);
        int finallyBlock = render.indexOf("finally {", forceColorMask);
        int restoreCall = render.indexOf(
            "cleanupFailure = restoreAfterRender(cleanupFailure, mainFramebuffer, previousDepthMask, blendWasEnabled,",
            finallyBlock);
        int restoreColorMaskArg = render.indexOf("previousColorMask", restoreCall);

        assertTrue("Final pass must allocate color-mask storage after active texture capture",
            allocateColorMask > saveActiveTexture);
        assertTrue("Final pass must read the caller color mask before mutating draw state",
            readColorMask > allocateColorMask);
        assertTrue("Final fullscreen output must force all color channels writable inside cleanup guard",
            tryBlock > readColorMask && forceColorMask > disableAlpha);
        assertTrue("Final cleanup must receive the saved color mask from the finally path",
            restoreCall > finallyBlock && restoreColorMaskArg > restoreCall);

        int restoreActiveTexture = restore.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)");
        int restoreDepthMaskCall = restore.indexOf("GlStateManager.depthMask(previousDepthMask)", restoreActiveTexture);
        int restoreBlend = restore.indexOf("restoreBlendState(blendWasEnabled)", restoreDepthMaskCall);
        int restoreAlpha = restore.indexOf("restoreAlphaState(alphaWasEnabled)", restoreBlend);
        int restoreColorMaskCall = restore.indexOf("restoreColorMask(previousColorMask)", restoreAlpha);

        assertTrue("Final cleanup must restore the color mask even if draw-state restore fails",
            restoreColorMaskCall > restoreAlpha);
        assertTrue(restoreColorMask.contains("previousColorMask.get(0) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(1) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(2) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(3) != 0"));
    }

    @Test
    public void baselineCopyDefersProgramCleanupUntilFinalCleanupLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String baselineCopy = between(source, "private void copyBaselineToMain", "public boolean hasCustomShader");

        int baselineBind = baselineCopy.indexOf("baseline.bindAsReadBuffer();");
        int restoreTexture = baselineCopy.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();", baselineBind);
        int copy = baselineCopy.indexOf("OculusRenderSystem.copyTexSubImage2D(", restoreTexture);

        int callBaseline = source.indexOf("copyBaselineToMain(mainFramebuffer, viewportWidth, viewportHeight);");
        int resetMipmaps = source.indexOf("resetRenderTargetMipmaps();", callBaseline);
        int runSwaps = source.indexOf("runSwapPasses();", resetMipmaps);
        int clearSamplers = source.indexOf("ProgramSamplers::clearActiveSamplers", runSwaps);
        int clearImages = source.indexOf("ProgramImages::clearActiveImages", clearSamplers);
        int glUseProgramZero = source.indexOf("GL20.glUseProgram(0)", clearImages);

        assertTrue(baselineBind >= 0);
        assertTrue(restoreTexture > baselineBind);
        assertTrue(copy > restoreTexture);
        assertFalse(baselineCopy.contains("GL20.glUseProgram(0);"));
        assertTrue(resetMipmaps > callBaseline);
        assertTrue(runSwaps > resetMipmaps);
        assertTrue(clearSamplers > runSwaps);
        assertTrue(clearImages > clearSamplers);
        assertTrue(glUseProgramZero > clearImages);
    }

    @Test
    public void finalSwapCopiesUseBackendAwareTextureCopyAndStillRunFinalSamplerCleanup() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String swapCopies = between(source, "private void runSwapPasses()", "private void ensureColorHolderAttachment");

        int bindFramebuffer = swapCopies.indexOf("swapPass.from.bind();");
        int copy = swapCopies.indexOf("OculusRenderSystem.copyTexSubImage2D(", bindFramebuffer);
        int textureArg = swapCopies.indexOf("swapPass.targetTexture,", copy);
        int targetArg = swapCopies.indexOf("GL11.GL_TEXTURE_2D,", textureArg);

        int runSwaps = source.indexOf("runSwapPasses();");
        int bindMain = source.indexOf("mainFramebuffer.bindFramebuffer(true)", runSwaps);
        int clearSamplers = source.indexOf("ProgramSamplers::clearActiveSamplers", bindMain);
        int clearImages = source.indexOf("ProgramImages::clearActiveImages", clearSamplers);
        int unbindAll = source.indexOf("FinalPassRenderer::unbindAllSamplerTextures", clearImages);

        assertTrue(bindFramebuffer >= 0);
        assertTrue("Final swap copies must keep the reference GL_FRAMEBUFFER binding rule before copying",
            copy > bindFramebuffer);
        assertTrue("Final swap copies must route through the 1.12-aware copy helper with the target texture id",
            textureArg > copy && targetArg > textureArg);
        assertFalse(swapCopies.contains("GL11.glCopyTexSubImage2D("));
        assertFalse(swapCopies.contains("GlStateManager.bindTexture(0);"));
        assertTrue(bindMain > runSwaps);
        assertTrue(clearSamplers > bindMain);
        assertTrue(clearImages > clearSamplers);
        assertTrue(unbindAll > clearImages);
    }

    @Test
    public void finalSwapCopiesRunAfterDefaultTextureUnitResetAndMipmapReset() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");
        String swapCopies = between(source, "private void runSwapPasses()", "private void ensureColorHolderAttachment");

        int copyBaseline = render.indexOf("copyBaselineToMain(mainFramebuffer, viewportWidth, viewportHeight);");
        int afterBranchDefaultUnit = render.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();", copyBaseline);
        int resetMipmaps = render.indexOf("resetRenderTargetMipmaps();", afterBranchDefaultUnit);
        int runSwaps = render.indexOf("runSwapPasses();", resetMipmaps);

        int bindFramebuffer = swapCopies.indexOf("swapPass.from.bind();");
        int copy = swapCopies.indexOf("OculusRenderSystem.copyTexSubImage2D(", bindFramebuffer);

        assertTrue("Final cleanup work must restart from the default texture unit after custom/baseline output",
            copyBaseline >= 0 && afterBranchDefaultUnit > copyBaseline);
        assertTrue("Final render-target mipmap reset must happen before final swap copyback like 1.16.5",
            resetMipmaps > afterBranchDefaultUnit && runSwaps > resetMipmaps);
        assertTrue("Final swap copies must bind GL_FRAMEBUFFER, not GL_READ_FRAMEBUFFER, before copying",
            bindFramebuffer >= 0 && copy > bindFramebuffer);
        assertFalse("Final swap copies must not regress to the read-framebuffer binding path that broke TAA",
            swapCopies.contains("bindAsReadBuffer"));
    }

    @Test
    public void finalColorHolderTracksMainColorBufferVersionLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");
        String ensureColorHolder = between(source, "private void ensureColorHolderAttachment", "private static int getColorBufferVersion");

        int requireMainFramebuffer = render.indexOf("Framebuffer mainFramebuffer = requireMainFramebuffer(minecraft);");
        int ensureBeforeBranch = render.indexOf("ensureColorHolderAttachment(mainFramebuffer);", requireMainFramebuffer);
        int finalProgramBranch = render.indexOf("if (program != null)", ensureBeforeBranch);

        int colorTexture = ensureColorHolder.indexOf(
            "int colorTexture = requireMainColorTexture(mainFramebuffer, \"Final pass color holder\");");
        int version = ensureColorHolder.indexOf("int colorTextureVersion = getColorBufferVersion(mainFramebuffer);", colorTexture);
        int guard = ensureColorHolder.indexOf(
            "colorTexture == lastColorTextureId && colorTextureVersion == lastColorTextureVersion", version);
        int addAttachment = ensureColorHolder.indexOf("colorHolder.addColorAttachment(0, colorTexture);", guard);
        int drawBuffer = ensureColorHolder.indexOf("colorHolder.drawBuffers(new int[] {0});", addAttachment);
        int readBuffer = ensureColorHolder.indexOf("colorHolder.readBuffer(0);", drawBuffer);
        int completeness = ensureColorHolder.indexOf("if (!colorHolder.isComplete())", readBuffer);
        int updateId = ensureColorHolder.indexOf("lastColorTextureId = colorTexture;", completeness);
        int updateVersion = ensureColorHolder.indexOf("lastColorTextureVersion = colorTextureVersion;", updateId);

        assertTrue(source.contains("private int lastColorTextureVersion;"));
        assertTrue(source.contains("this.lastColorTextureVersion = -1;"));
        assertTrue("Final pass must refresh the main color attachment before raster/baseline branching like 1.16.5",
            requireMainFramebuffer >= 0 && ensureBeforeBranch > requireMainFramebuffer
                && finalProgramBranch > ensureBeforeBranch);
        assertTrue(colorTexture >= 0);
        assertTrue(version > colorTexture);
        assertTrue(guard > version);
        assertTrue(addAttachment > guard);
        assertTrue(drawBuffer > addAttachment);
        assertTrue("Final color holder framebuffer must select the main color attachment for read operations before validation",
            readBuffer > drawBuffer);
        assertTrue("Final color holder framebuffer completeness must be checked after draw/read buffers are configured",
            completeness > readBuffer);
        assertTrue(updateId > completeness);
        assertTrue(updateVersion > updateId);
        assertFalse("Final pass should fail fast instead of skipping an invalid main color texture",
            ensureColorHolder.contains("colorTexture <= 0"));
        assertTrue(source.contains("mainFramebuffer instanceof MinecraftFramebufferExt"));
        assertTrue(source.contains("oculus$getColorBufferVersion()"));
    }

    @Test
    public void finalPassRequiresValidMainColorTextureForBothOutputPaths() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String ensureColorHolder = between(source, "private void ensureColorHolderAttachment", "private static int getColorBufferVersion");
        String requireTexture = between(source, "private static int requireMainColorTexture", "private static void unbindAllSamplerTextures");
        String baselineCopy = between(source, "private void copyBaselineToMain", "public boolean hasCustomShader");

        int helperRead = requireTexture.indexOf("int colorTexture = mainFramebuffer.framebufferTexture;");
        int helperGuard = requireTexture.indexOf("if (colorTexture <= 0) {", helperRead);
        int helperThrow = requireTexture.indexOf(
            "throw new IllegalStateException(context + \" requires a valid Minecraft main color texture\");",
            helperGuard);
        int helperReturn = requireTexture.indexOf("return colorTexture;", helperThrow);

        int colorHolderRequire = ensureColorHolder.indexOf(
            "int colorTexture = requireMainColorTexture(mainFramebuffer, \"Final pass color holder\");");
        int colorHolderAttach = ensureColorHolder.indexOf("colorHolder.addColorAttachment(0, colorTexture);",
            colorHolderRequire);

        int baselineRequire = baselineCopy.indexOf(
            "int colorTexture = requireMainColorTexture(mainFramebuffer, \"Final pass baseline copy\");");
        int baselineBind = baselineCopy.indexOf("baseline.bindAsReadBuffer();", baselineRequire);
        int baselineCopyCall = baselineCopy.indexOf("OculusRenderSystem.copyTexSubImage2D(", baselineBind);
        int baselineTextureArg = baselineCopy.indexOf("colorTexture,", baselineCopyCall);

        assertTrue(helperRead >= 0);
        assertTrue(helperGuard > helperRead);
        assertTrue(helperThrow > helperGuard);
        assertTrue(helperReturn > helperThrow);
        assertTrue(colorHolderRequire >= 0);
        assertTrue(colorHolderAttach > colorHolderRequire);
        assertTrue(baselineRequire >= 0);
        assertTrue(baselineBind > baselineRequire);
        assertTrue(baselineCopyCall > baselineBind);
        assertTrue(baselineTextureArg > baselineCopyCall);
        assertFalse(source.contains("mainFramebuffer.framebufferTexture,\n            GL11.GL_TEXTURE_2D"));
    }

    @Test
    public void finalPassRequiresMainFramebufferInsteadOfSkippingRender() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(source, "public void render()", "private Throwable restoreAfterRender");
        String requireMainFramebuffer = between(source, "private static Framebuffer requireMainFramebuffer",
            "private static int requireMainColorTexture");

        int minecraft = render.indexOf("Minecraft minecraft = Minecraft.getMinecraft();");
        int require = render.indexOf("Framebuffer mainFramebuffer = requireMainFramebuffer(minecraft);", minecraft);
        int viewportWidth = render.indexOf("int viewportWidth = Math.max(1, mainFramebuffer.framebufferWidth);",
            require);

        assertTrue("Final pass render must fetch Minecraft before requiring the main framebuffer",
            minecraft >= 0);
        assertTrue("Final pass render must fail clearly instead of skipping when the main framebuffer is unavailable",
            require > minecraft && viewportWidth > require);
        assertFalse(render.contains("if (minecraft == null) {\n            return;"));
        assertFalse(render.contains("if (mainFramebuffer == null) {\n            return;"));
        assertTrue(requireMainFramebuffer.contains("throw new IllegalStateException(\"Final pass requires a Minecraft instance\");"));
        assertTrue(requireMainFramebuffer.contains("throw new IllegalStateException(\"Final pass requires a Minecraft main framebuffer\");"));
    }

    @Test
    public void finalPassTargetPathsFailFastInsteadOfSkippingMissingTargets() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertTrue(source.contains(
            "RenderTarget target = requireRenderTarget(swapPass.target, \"Final swap pass resize\");"));
        assertTrue(source.contains(
            "throw new IllegalStateException(\"Final swap pass references a null render target index\");"));
        assertTrue(source.contains("int bufferIndex = buffer;"));
        assertTrue(source.contains("RenderTarget target = requireRenderTarget(bufferIndex, \"Final swap pass\");"));
        assertTrue(source.contains(
            "setupMipmapping(renderTargets.get(index), index, stageReadsFromAlt.contains(index));"));
        assertTrue(source.contains("resetRenderTarget(renderTargets.get(i), i);"));
        assertTrue(source.contains("private static void resetRenderTarget(RenderTarget target, int index)"));
        String resizeBody = between(source, "public void recalculateSwapPassSize()", "private List<SwapPass> createSwapPasses");
        int replacements = resizeBody.indexOf("List<SwapPassReplacement> replacements = new ArrayList<>();");
        int pendingReplacement = resizeBody.indexOf("GlFramebuffer pendingReplacementFramebuffer = null;", replacements);
        int tryBlock = resizeBody.indexOf("try {", replacements);
        int createReplacement = resizeBody.indexOf("GlFramebuffer replacementFramebuffer =", tryBlock);
        int trackPending = resizeBody.indexOf("pendingReplacementFramebuffer = replacementFramebuffer;",
            createReplacement);
        int addReplacement = resizeBody.indexOf("replacements.add(new SwapPassReplacement(", trackPending);
        int targetTextureSnapshot = resizeBody.indexOf("target.getMainTexture()));", addReplacement);
        int clearPending = resizeBody.indexOf("pendingReplacementFramebuffer = null;", targetTextureSnapshot);
        int cleanupCatch = resizeBody.indexOf("catch (RuntimeException | Error exception)", clearPending);
        int destroyPending = resizeBody.indexOf(
            "Throwable failure = destroyFramebuffer(null, pendingReplacementFramebuffer);", cleanupCatch);
        int destroyReplacements = resizeBody.indexOf(
            "failure = destroySwapPassReplacements(failure, replacements);", destroyPending);
        int suppressCleanup = resizeBody.indexOf("addSuppressedCleanupFailure(exception, failure);",
            destroyReplacements);
        int rethrow = resizeBody.indexOf("throw exception;", suppressCleanup);
        int previousList = resizeBody.indexOf("List<GlFramebuffer> previousFramebuffers = new ArrayList<>();",
            rethrow);
        int collectLoop = resizeBody.indexOf("for (SwapPassReplacement replacement : replacements)", previousList);
        int recordPrevious = resizeBody.indexOf("previousFramebuffers.add(replacement.swapPass.from);", collectLoop);
        int destroyPrevious = resizeBody.indexOf(
            "rethrowCleanupFailure(destroyFramebuffers(null, previousFramebuffers));",
            recordPrevious);
        int commitLoop = resizeBody.indexOf("for (SwapPassReplacement replacement : replacements)", recordPrevious);
        int assignReplacement = resizeBody.indexOf("swapPass.from = replacement.framebuffer;", commitLoop);
        int clearReplacement = resizeBody.indexOf("replacement.framebuffer = null;", assignReplacement);
        int updateTargetTexture = resizeBody.indexOf("swapPass.targetTexture = replacement.targetTexture;",
            clearReplacement);

        assertTrue("Final swap resize must stage all replacement framebuffers before mutating swap passes",
            replacements >= 0);
        assertTrue(pendingReplacement > replacements && tryBlock > pendingReplacement);
        assertTrue(createReplacement > tryBlock && trackPending > createReplacement && addReplacement > trackPending);
        assertTrue("Replacement metadata must be snapshotted before resize staging completes",
            targetTextureSnapshot > addReplacement && clearPending > targetTextureSnapshot
                && cleanupCatch > clearPending);
        assertTrue("Unpublished final swap framebuffers must be destroyed before staged replacements",
            destroyPending > cleanupCatch && destroyReplacements > destroyPending);
        assertTrue("Failed final swap resize staging must destroy any replacement framebuffers it created",
            cleanupCatch > clearPending && destroyReplacements > cleanupCatch);
        assertTrue("Final swap resize cleanup failures must be suppressed onto the resize failure",
            suppressCleanup > destroyReplacements && rethrow > suppressCleanup);
        assertTrue("Previous swap framebuffers must be collected before replacements publish",
            previousList > rethrow && collectLoop > previousList && recordPrevious > collectLoop
                && commitLoop > recordPrevious);
        assertTrue("Swap pass state must update before old framebuffer cleanup can fail",
            assignReplacement > commitLoop && clearReplacement > assignReplacement);
        assertTrue(updateTargetTexture > clearReplacement);
        assertTrue("Old swap framebuffer cleanup must run after replacement state is live",
            destroyPrevious > updateTargetTexture);
        assertTrue(resizeBody.contains("private void destroySwapPassReplacements(List<SwapPassReplacement> replacements)"));
        assertTrue(resizeBody.contains(
            "private Throwable destroySwapPassReplacements(Throwable failure, List<SwapPassReplacement> replacements)"));
        assertTrue(resizeBody.contains("failure = destroyFramebuffer(failure, replacement.framebuffer);"));
        assertTrue(resizeBody.contains("private Throwable destroyFramebuffers(Throwable failure, List<GlFramebuffer> framebuffers)"));
        assertTrue(resizeBody.contains("failure = destroyFramebuffer(failure, framebuffer);"));
        assertTrue(resizeBody.contains("private static final class SwapPassReplacement"));
        assertFalse(source.contains(
            "if (target == null) {\n                continue;\n            }\n\n            renderTargets.destroyFramebuffer(swapPass.from);"));
        assertFalse(source.contains("public void recalculateSwapPassSizeOld()"));
        assertFalse(source.contains(
            "if (target == null) {\n                continue;\n            }\n\n            int filter = target.getInternalFormat()"));
        assertFalse(source.contains(
            "if (target == null) {\n            return;\n        }\n\n        int texture = readFromAlt"));
    }

    @Test
    public void finalPassConstructorCleansOwnedResourcesOnFailure() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String constructorBody = between(source,
            "public FinalPassRenderer(PackDirectives packDirectives,",
            "private void bindCenterDepthSmooth");

        int swapPassLocal = constructorBody.indexOf("List<SwapPass> createdSwapPasses = Collections.emptyList();");
        int baselineLocal = constructorBody.indexOf("GlFramebuffer createdBaseline = null;", swapPassLocal);
        int colorHolderLocal = constructorBody.indexOf("GlFramebuffer createdColorHolder = null;", baselineLocal);
        int programLocal = constructorBody.indexOf("Program createdProgram = null;", colorHolderLocal);
        int computesLocal = constructorBody.indexOf("ComputeProgram[] createdComputes = new ComputeProgram[0];",
            programLocal);
        int completeFlag = constructorBody.indexOf("boolean complete = false;", computesLocal);
        int tryBlock = constructorBody.indexOf("try {", completeFlag);
        int buildProgram = constructorBody.indexOf("createdProgram = builder.build();", tryBlock);
        int createComputes = constructorBody.indexOf("createdComputes = createComputes", buildProgram);
        int createBaseline = constructorBody.indexOf("createdBaseline = renderTargets.createGbufferFramebuffer",
            createComputes);
        int createColorHolder = constructorBody.indexOf("createdColorHolder = new GlFramebuffer();", createBaseline);
        int createSwapPasses = constructorBody.indexOf("createdSwapPasses = createSwapPasses", createColorHolder);
        int markComplete = constructorBody.indexOf("complete = true;", createSwapPasses);
        int cleanupCatch = constructorBody.indexOf("catch (RuntimeException | Error exception)", markComplete);
        int failureLocal = constructorBody.indexOf("Throwable failure = null;", cleanupCatch);
        int destroyProgram = constructorBody.indexOf("failure = destroyProgram(failure, createdProgram);",
            failureLocal);
        int destroyComputes = constructorBody.indexOf("failure = destroyComputePrograms(failure, createdComputes);",
            destroyProgram);
        int destroyBaseline = constructorBody.indexOf("failure = destroyFramebuffer(failure, createdBaseline);",
            destroyComputes);
        int destroyColorHolder = constructorBody.indexOf("failure = destroyGlFramebuffer(failure, createdColorHolder);",
            destroyBaseline);
        int destroySwapPasses = constructorBody.indexOf("failure = destroySwapPasses(failure, createdSwapPasses);",
            destroyColorHolder);
        int suppressedCleanup = constructorBody.indexOf("addSuppressedCleanupFailure(exception, failure);",
            destroySwapPasses);
        int rethrow = constructorBody.indexOf("throw exception;", suppressedCleanup);
        int assignProgram = constructorBody.indexOf("this.program = createdProgram;", rethrow);

        assertTrue(swapPassLocal >= 0);
        assertTrue(baselineLocal > swapPassLocal);
        assertTrue(colorHolderLocal > baselineLocal);
        assertTrue(programLocal > colorHolderLocal);
        assertTrue(computesLocal > programLocal);
        assertTrue(completeFlag > computesLocal);
        assertTrue(tryBlock > completeFlag);
        assertTrue("Final pass raster program must be built before compute programs like 1.16.5",
            buildProgram > tryBlock);
        assertTrue(createComputes > buildProgram);
        assertTrue("Final pass framebuffers and swap passes must stage only after program/compute setup",
            createBaseline > createComputes && createColorHolder > createBaseline
                && createSwapPasses > createColorHolder && markComplete > createSwapPasses);
        assertTrue(cleanupCatch > markComplete);
        assertTrue(failureLocal > cleanupCatch);
        assertTrue(destroyProgram > failureLocal);
        assertTrue(destroyComputes > destroyProgram);
        assertTrue(destroyBaseline > destroyComputes);
        assertTrue(destroyColorHolder > destroyBaseline);
        assertTrue(destroySwapPasses > destroyColorHolder);
        assertTrue("Final pass setup-failure cleanup must not destroy the color holder twice before swap cleanup",
            constructorBody.indexOf("failure = destroyGlFramebuffer(failure, createdColorHolder);",
                destroyColorHolder + 1) > destroySwapPasses);
        assertTrue(suppressedCleanup > destroySwapPasses);
        assertTrue(rethrow > suppressedCleanup);
        assertTrue(assignProgram > rethrow);
    }

    @Test
    public void finalPassDestroyAggregatesCleanupFailuresWithoutSkippingLaterResources() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String destroy = between(source, "public void destroy()", "private ComputeProgram[] createComputes");
        String helpers = source.substring(source.indexOf("private void destroySwapPasses"));

        int tryBlock = destroy.indexOf("try {");
        int failureLocal = destroy.indexOf("Throwable failure = null;", tryBlock);
        int destroyProgram = destroy.indexOf("failure = destroyProgram(failure, program);", failureLocal);
        int destroyComputes = destroy.indexOf("failure = destroyComputePrograms(failure, computes);",
            destroyProgram);
        int destroyBaseline = destroy.indexOf("failure = destroyFramebuffer(failure, baseline);", destroyComputes);
        int destroyColorHolder = destroy.indexOf("failure = destroyGlFramebuffer(failure, colorHolder);",
            destroyBaseline);
        int destroySwapPasses = destroy.indexOf("failure = destroySwapPasses(failure, swapPasses);",
            destroyColorHolder);
        int rethrow = destroy.indexOf("rethrowCleanupFailure(failure);", destroySwapPasses);
        int finallyBlock = destroy.indexOf("} finally {", rethrow);
        int clearSwapPasses = destroy.indexOf("clearSwapPassFramebuffers(swapPasses);", finallyBlock);
        int markDestroyed = destroy.indexOf("destroyed = true;", clearSwapPasses);

        int swapHelper = helpers.indexOf("private Throwable destroySwapPasses(Throwable failure, List<SwapPass> passes)");
        int swapLoop = helpers.indexOf("for (SwapPass swapPass : passes)", swapHelper);
        int swapDestroy = helpers.indexOf("failure = destroyFramebuffer(failure, swapPass.from);", swapLoop);
        int swapReturn = helpers.indexOf("return failure;", swapDestroy);
        int clearHelper = helpers.indexOf("private static void clearSwapPassFramebuffers", swapReturn);
        int swapNull = helpers.indexOf("swapPass.from = null;", clearHelper);

        assertTrue(tryBlock >= 0 && failureLocal > tryBlock);
        assertTrue("Final destroy must attempt program, compute, baseline, color-holder, and swap cleanup in order",
            destroyProgram > failureLocal && destroyComputes > destroyProgram && destroyBaseline > destroyComputes
                && destroyColorHolder > destroyBaseline && destroySwapPasses > destroyColorHolder);
        assertTrue("Final destroy must rethrow only after every owned cleanup path is attempted",
            rethrow > destroySwapPasses);
        assertTrue("Final pass renderer must drop stale swap FBO handles and mark destroyed even when cleanup fails",
            finallyBlock > rethrow && clearSwapPasses > finallyBlock && markDestroyed > clearSwapPasses);
        assertTrue("Swap cleanup must keep iterating while recording framebuffer destroy failures",
            swapHelper >= 0 && swapLoop > swapHelper && swapDestroy > swapLoop && swapReturn > swapDestroy);
        assertTrue("Swap cleanup must preserve framebuffer handles until cleanup succeeds",
            clearHelper > swapReturn && swapNull > clearHelper);
        assertTrue(helpers.contains("failure.addSuppressed(exception);"));
        assertTrue(helpers.contains("primary.addSuppressed(cleanupFailure);"));
        assertTrue(helpers.contains("throw (RuntimeException) failure;"));
        assertTrue(helpers.contains("throw (Error) failure;"));
    }

    @Test
    public void finalSwapPassCreationDestroysPartialFramebuffersOnFailure() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String createSwapPasses = between(source,
            "private List<SwapPass> createSwapPasses",
            "private void runSwapPasses()");

        int passesList = createSwapPasses.indexOf("List<SwapPass> passes = new ArrayList<>();");
        int pendingLocal = createSwapPasses.indexOf("SwapPass pendingSwapPass = null;", passesList);
        int tryBlock = createSwapPasses.indexOf("try {", pendingLocal);
        int createPass = createSwapPasses.indexOf("SwapPass swapPass = new SwapPass();", tryBlock);
        int trackPending = createSwapPasses.indexOf("pendingSwapPass = swapPass;", createPass);
        int createFramebuffer = createSwapPasses.indexOf("swapPass.from = renderTargets.createColorFramebuffer",
            trackPending);
        int targetTexture = createSwapPasses.indexOf("swapPass.targetTexture = target.getMainTexture();",
            createFramebuffer);
        int addPass = createSwapPasses.indexOf("passes.add(swapPass);", targetTexture);
        int clearPending = createSwapPasses.indexOf("pendingSwapPass = null;", addPass);
        int cleanup = createSwapPasses.indexOf("} catch (RuntimeException | Error exception) {", clearPending);
        int destroyPending = createSwapPasses.indexOf(
            "Throwable failure = destroyFramebuffer(null, pendingSwapPass == null ? null : pendingSwapPass.from);",
            cleanup);
        int pendingGuard = createSwapPasses.indexOf("if (pendingSwapPass != null)", destroyPending);
        int clearPendingFramebuffer = createSwapPasses.indexOf("pendingSwapPass.from = null;", pendingGuard);
        int destroySwapPasses = createSwapPasses.indexOf(
            "failure = destroySwapPasses(failure, passes);", clearPendingFramebuffer);
        int suppressCleanup = createSwapPasses.indexOf("addSuppressedCleanupFailure(exception, failure);",
            destroySwapPasses);
        int rethrow = createSwapPasses.indexOf("throw exception;", destroySwapPasses);

        assertTrue(passesList >= 0);
        assertTrue("Final swap pass creation must track an unpublished pass before GL framebuffer allocation",
            pendingLocal > passesList && tryBlock > pendingLocal && createPass > tryBlock
                && trackPending > createPass && createFramebuffer > trackPending);
        assertTrue("Final swap pass must record the target texture before publishing the pass like 1.16.5",
            targetTexture > createFramebuffer && addPass > targetTexture);
        assertTrue("Published swap passes must clear the pending tracker after list ownership begins",
            clearPending > addPass && cleanup > clearPending);
        assertTrue("Final swap setup failures must destroy an unpublished framebuffer before list cleanup",
            destroyPending > cleanup && pendingGuard > destroyPending
                && clearPendingFramebuffer > pendingGuard && destroySwapPasses > clearPendingFramebuffer);
        assertTrue("Final swap cleanup failures must be suppressed onto the original setup failure",
            suppressCleanup > destroySwapPasses);
        assertTrue(rethrow > suppressCleanup);
    }

    @Test
    public void finalComputeCompilationDestroysPartialProgramsOnFailure() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String createComputes = between(source,
            "private ComputeProgram[] createComputes",
            "private void destroySwapPasses");

        int programsArray = createComputes.indexOf("ComputeProgram[] programs = new ComputeProgram[computeSources.length];");
        int tryBlock = createComputes.indexOf("try {", programsArray);
        int buildCompute = createComputes.indexOf("ComputeProgram program = builder.buildCompute();", tryBlock);
        int assignProgram = createComputes.indexOf("programs[i] = program;", buildCompute);
        int setWorkgroups = createComputes.indexOf(
            "program.setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());", assignProgram);
        int cleanup = createComputes.indexOf("} catch (RuntimeException | Error exception) {", setWorkgroups);
        int destroyPrograms = createComputes.indexOf(
            "addSuppressedCleanupFailure(exception, destroyComputePrograms(null, programs));", cleanup);
        int rethrow = createComputes.indexOf("throw exception;", destroyPrograms);

        assertTrue(programsArray >= 0);
        assertTrue(tryBlock > programsArray);
        assertTrue(buildCompute > tryBlock);
        assertTrue(assignProgram > buildCompute);
        assertTrue(setWorkgroups > assignProgram);
        assertTrue(cleanup > setWorkgroups);
        assertTrue("Compute cleanup failures must be suppressed onto the original compile failure",
            destroyPrograms > cleanup);
        assertTrue(rethrow > destroyPrograms);
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
