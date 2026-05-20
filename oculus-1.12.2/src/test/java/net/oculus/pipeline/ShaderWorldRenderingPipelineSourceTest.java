package net.oculus.pipeline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ShaderWorldRenderingPipelineSourceTest {
    @Test
    public void constructorConfiguresGameplayUniformSmoothingFromPackDirectives() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("GameplayUniforms.configure(directives);"));
    }

    @Test
    public void frameCachedUniformsRefreshAfterCameraSetup() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("public void afterCameraSetup(float partialTicks)"));
        assertTrue(source.contains("CapturedRenderingState.INSTANCE.capturePostCameraSetup(partialTicks);"));
        assertTrue(source.contains("if (!frameRuntimeUniformsPrepared) {"));
        assertTrue(source.contains("GameplayUniforms.onFrameStart();"));
        assertTrue(source.contains("CompatibilityUniforms.onFrameStart();"));
        assertTrue(source.contains("customUniforms.beginFrame();"));
        assertTrue(source.contains("frameRuntimeUniformsPrepared = true;"));
        assertTrue(source.contains("syncProgram();"));

        int beginFrame = source.indexOf("public void beginWorldRendering(float partialTicks)");
        int afterCameraSetup = source.indexOf("public void afterCameraSetup(float partialTicks)");
        int customFrameStart = source.indexOf("customUniforms.beginFrame();");
        assertTrue(beginFrame >= 0);
        assertTrue(afterCameraSetup >= 0);
        assertTrue(customFrameStart >= 0);
        assertTrue("Custom uniforms must pre-evaluate after the 1.12 camera transform is available",
            customFrameStart > afterCameraSetup && afterCameraSetup > beginFrame);
    }

    @Test
    public void beginWorldRenderingSeedsSamplerAvailabilityFromCurrentGlStateBeforeFirstProgramSync() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String body = methodBody(source, "public void beginWorldRendering(float partialTicks)");

        int beginLevel = body.indexOf("beginLevelRendering();");
        int markPrepared = body.indexOf("levelPrepared = true;", beginLevel);
        int refresh = body.indexOf("StateTracker.INSTANCE.refreshFromGlState();", markPrepared);
        int captureInputs = body.indexOf("inputs = StateTracker.INSTANCE.getInputs();", refresh);
        int bindGbuffer = body.indexOf("bindGbufferFramebuffer();", captureInputs);
        int syncProgram = body.indexOf("syncProgram();", bindGbuffer);

        assertTrue(beginLevel >= 0);
        assertTrue("Texture/lightmap/overlay availability must be seeded after setup succeeds and before binding the first shader",
            markPrepared > beginLevel && refresh > markPrepared && captureInputs > refresh
                && bindGbuffer > captureInputs && syncProgram > bindGbuffer);
    }

    @Test
    public void frameUpdateNotifierRunsBeforeRenderTargetClearsLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int beginLevel = source.indexOf("public void beginLevelRendering()");
        int inProgressGuard = source.indexOf("if (prepared || isRenderingWorld)", beginLevel);
        int inProgressThrow = source.indexOf(
            "throw new IllegalStateException(\"Called beginLevelRendering but level rendering appears to still be in progress\");",
            inProgressGuard);
        int compile = source.indexOf("ensureShadersCompiled();", beginLevel);
        int stateReset = source.indexOf("shadowRenderTargetsPreparedThisFrame = false;", compile);
        int phaseNone = source.indexOf("phase = WorldRenderingPhase.NONE;", stateReset);
        int notifier = source.indexOf("frameUpdateNotifier.onNewFrame();", phaseNone);
        int clear = source.indexOf("clearRenderTargets();", notifier);
        int phaseSky = source.indexOf("setPhase(WorldRenderingPhase.SKY);", clear);
        String setPhase = methodBody(source, "public void setPhase");

        assertTrue(beginLevel >= 0);
        assertTrue("beginLevelRendering must fail clearly if the previous level-render lifecycle is still active",
            inProgressGuard > beginLevel && inProgressThrow > inProgressGuard && inProgressThrow < compile);
        assertTrue("Frame notifier must run after shader compilation so registered common uniforms are present",
            compile > beginLevel);
        assertTrue("Frame state must reset and renderStage must stay NONE before the notifier fires",
            stateReset > compile && phaseNone > stateReset);
        assertTrue("Frame notifier must run before render-target clears/preparation like DeferredWorldRenderingPipeline",
            notifier > stateReset && clear > notifier);
        assertTrue("The pipeline should switch to SKY through the normal phase path after render-target preparation like DeferredWorldRenderingPipeline",
            phaseSky > clear);
        assertTrue("Initial SKY transition must publish phase-change notifications",
            setPhase.contains("GbufferPrograms.runPhaseChangeNotifier();"));
        assertFalse("Initial SKY transition must not bypass the normal phase-change path",
            source.contains("phase = WorldRenderingPhase.SKY;"));
    }

    @Test
    public void frameBeginFailuresResetLifecycleAndRestoreMainFramebuffer() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String beginWorld = methodBody(source, "public void beginWorldRendering(float partialTicks)");
        String beginLevel = methodBody(source, "public void beginLevelRendering()");
        String restore = methodBody(source, "private Throwable restoreAfterFrameBeginFailure");
        String reset = methodBody(source, "private void resetFrameLifecycleAfterBeginFailure");

        int levelFlag = beginWorld.indexOf("boolean levelPrepared = false;");
        int worldTry = beginWorld.indexOf("try {", levelFlag);
        int beginLevelCall = beginWorld.indexOf("beginLevelRendering();", worldTry);
        int markPrepared = beginWorld.indexOf("levelPrepared = true;", beginLevelCall);
        int bindGbuffer = beginWorld.indexOf("bindGbufferFramebuffer();", markPrepared);
        int syncProgram = beginWorld.indexOf("syncProgram();", bindGbuffer);
        int worldCatch = beginWorld.indexOf("catch (RuntimeException | Error exception)", syncProgram);
        int preparedGuard = beginWorld.indexOf("if (levelPrepared)", worldCatch);
        int worldRestore = beginWorld.indexOf(
            "rethrowCleanupFailure(restoreAfterFrameBeginFailure(exception));", preparedGuard);
        int worldRethrow = beginWorld.indexOf("throw exception;", worldRestore);

        assertTrue("beginWorldRendering must only run failure cleanup after beginLevelRendering succeeds",
            levelFlag >= 0 && worldTry > levelFlag && beginLevelCall > worldTry && markPrepared > beginLevelCall);
        assertTrue("G-buffer bind and initial program sync failures must route through frame-begin cleanup",
            bindGbuffer > markPrepared && syncProgram > bindGbuffer && worldCatch > syncProgram
                && preparedGuard > worldCatch && worldRestore > preparedGuard && worldRethrow > worldRestore);

        int levelTry = beginLevel.indexOf("try {");
        int ensureSetup = beginLevel.indexOf("ensureSetup();", levelTry);
        int ensureFramebuffers = beginLevel.indexOf("ensureFramebufferManagerInitialized();", ensureSetup);
        int customImages = beginLevel.indexOf(
            "customImageManager.initializeOrResize(framebufferManager.getWidth(), framebufferManager.getHeight());",
            ensureFramebuffers);
        int storageBuffers = beginLevel.indexOf("shaderStorageBufferManager.initialize();", customImages);
        int shadowRenderer = beginLevel.indexOf("ensureShadowRendererInitialized();", storageBuffers);
        int compileShaders = beginLevel.indexOf("ensureShadersCompiled();", shadowRenderer);
        int levelMarkPrepared = beginLevel.indexOf("prepared = true;", compileShaders);
        int notifier = beginLevel.indexOf("frameUpdateNotifier.onNewFrame();", levelMarkPrepared);
        int clear = beginLevel.indexOf("clearRenderTargets();", notifier);
        int phaseSky = beginLevel.indexOf("setPhase(WorldRenderingPhase.SKY);", clear);
        int levelCatch = beginLevel.indexOf("catch (RuntimeException | Error exception)", phaseSky);
        int levelRestore = beginLevel.indexOf(
            "rethrowCleanupFailure(restoreAfterFrameBeginFailure(exception));", levelCatch);
        int levelRethrow = beginLevel.indexOf("throw exception;", levelRestore);

        assertTrue("Lazy 1.12 setup must be guarded by frame-begin cleanup before any GL/resource setup can fail",
            levelTry >= 0 && ensureSetup > levelTry && ensureFramebuffers > ensureSetup
                && customImages > ensureFramebuffers && storageBuffers > customImages
                && shadowRenderer > storageBuffers && compileShaders > shadowRenderer);
        assertTrue("Frame state, notifier, render-target clear, and SKY transition must be guarded by cleanup",
            levelMarkPrepared > compileShaders && notifier > levelMarkPrepared && clear > notifier && phaseSky > clear
                && levelCatch > phaseSky && levelRestore > levelCatch && levelRethrow > levelRestore);

        assertTrue(restore.contains("failure = runCleanup(failure, Program::unbind);"));
        assertTrue(restore.contains("activeGbufferProgram = null;"));
        assertTrue(restore.contains("activeGbufferProgramName = null;"));
        assertTrue(restore.contains("failure = runCleanup(failure, this::restoreRenderStateOverrides);"));
        assertTrue(restore.contains(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);"));
        assertTrue(restore.contains("resetFrameLifecycleAfterBeginFailure();"));
        assertTrue(restore.contains("return failure;"));

        assertTrue(reset.contains("prepared = false;"));
        assertTrue(reset.contains("isRenderingWorld = false;"));
        assertTrue(reset.contains("isRenderingFullScreenPass = false;"));
        assertTrue(reset.contains("gbufferBound = false;"));
        assertTrue(reset.contains("isMainBound = true;"));
        assertTrue(reset.contains("bindingShaderFramebuffer = false;"));
        assertTrue(reset.contains("phase = WorldRenderingPhase.NONE;"));
        assertTrue(reset.contains("overridePhase = null;"));
    }

    @Test
    public void pipelineProgramUnbindAlwaysClearsGlProgramAndOverrides() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String unbind = methodBody(source, "private void unbindProgram()");

        int failureLocal = unbind.indexOf("Throwable failure = null;");
        int unbindProgram = unbind.indexOf("failure = runCleanup(failure, Program::unbind);", failureLocal);
        int clearActiveProgram = unbind.indexOf("activeGbufferProgram = null;", unbindProgram);
        int clearActiveProgramName = unbind.indexOf("activeGbufferProgramName = null;", clearActiveProgram);
        int restoreOverrides = unbind.indexOf("failure = runCleanup(failure, this::restoreRenderStateOverrides);",
            clearActiveProgramName);
        int rethrow = unbind.indexOf("rethrowCleanupFailure(failure);", restoreOverrides);

        assertTrue("Program unbind cleanup must aggregate failures from the first cleanup step",
            failureLocal >= 0 && unbindProgram > failureLocal);
        assertTrue("Pipeline active-program bookkeeping must reset even when GL unbind reports cleanup failure",
            clearActiveProgram > unbindProgram && clearActiveProgramName > clearActiveProgram);
        assertTrue("Render-state overrides must still be restored after GL program unbind is attempted",
            restoreOverrides > clearActiveProgramName);
        assertTrue("Program unbind cleanup must rethrow only after all cleanup steps were attempted",
            rethrow > restoreOverrides);
        assertFalse("Pipeline unbind must not skip GL program cleanup just because local bookkeeping is already clear",
            unbind.contains("if (activeGbufferProgram != null)"));
    }

    @Test
    public void cameraSetupFailuresResetPreparedFrameLifecycleLikeBeginFailures() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String afterCameraSetup = methodBody(source, "public void afterCameraSetup(float partialTicks)");
        String reset = methodBody(source, "private void resetFrameLifecycleAfterBeginFailure");

        int tryBlock = afterCameraSetup.indexOf("try {");
        int capture = afterCameraSetup.indexOf(
            "CapturedRenderingState.INSTANCE.capturePostCameraSetup(partialTicks);", tryBlock);
        int frameGuard = afterCameraSetup.indexOf("if (!frameRuntimeUniformsPrepared)", capture);
        int gameplay = afterCameraSetup.indexOf("GameplayUniforms.onFrameStart();", frameGuard);
        int compatibility = afterCameraSetup.indexOf("CompatibilityUniforms.onFrameStart();", gameplay);
        int custom = afterCameraSetup.indexOf("customUniforms.beginFrame();", compatibility);
        int preparedFlag = afterCameraSetup.indexOf("frameRuntimeUniformsPrepared = true;", custom);
        int prepareShadows = afterCameraSetup.indexOf("prepareShadowRenderTargets();", preparedFlag);
        int sync = afterCameraSetup.indexOf("syncProgram();", prepareShadows);
        int catchBlock = afterCameraSetup.indexOf("catch (RuntimeException | Error exception)", sync);
        int frameActiveGuard = afterCameraSetup.indexOf("if (prepared || isRenderingWorld)", catchBlock);
        int restore = afterCameraSetup.indexOf(
            "rethrowCleanupFailure(restoreAfterFrameBeginFailure(exception));", frameActiveGuard);
        int rethrow = afterCameraSetup.indexOf("throw exception;", restore);

        assertTrue("The 1.12 camera hook must guard all frame-runtime uniform and shadow preparation work",
            tryBlock >= 0 && capture > tryBlock);
        assertTrue("Frame-runtime uniform refresh must stay before shadow target preparation",
            frameGuard > capture && gameplay > frameGuard && compatibility > gameplay
                && custom > compatibility && preparedFlag > custom && prepareShadows > preparedFlag);
        assertTrue("Camera setup must sync the active program after shadow preparation",
            sync > prepareShadows);
        assertTrue("Camera setup failures during an active frame must use the same cleanup as begin failures",
            catchBlock > sync && frameActiveGuard > catchBlock && restore > frameActiveGuard && rethrow > restore);
        assertTrue("Begin-failure cleanup must clear the camera-hook frame-uniform flag",
            reset.contains("frameRuntimeUniformsPrepared = false;"));
    }

    @Test
    public void fullscreenPostprocessRunsWithNoneRenderStageLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);
        String finalizeLevel = methodBody(source, "public void finalizeLevelRendering()");
        String finalizeRestore = methodBody(source, "private Throwable restoreAfterFinalizeLevelRendering");
        String finalizeReset = methodBody(source, "private void resetFrameLifecycleAfterFinalize");
        String fixedFunctionRestore = methodBody(source, "private static void restorePostWorldFixedFunctionState");

        int unbind = finalizeLevel.indexOf("unbindProgram();");
        int worldFalse = finalizeLevel.indexOf("isRenderingWorld = false;", unbind);
        int phaseNone = finalizeLevel.indexOf("phase = WorldRenderingPhase.NONE;", worldFalse);
        int overrideNull = finalizeLevel.indexOf("overridePhase = null;", phaseNone);
        int fullscreenTrue = finalizeLevel.indexOf("isRenderingFullScreenPass = true;", overrideNull);
        int composite = finalizeLevel.indexOf("runCompositePass();", fullscreenTrue);
        int finallyBlock = finalizeLevel.indexOf("finally {", composite);
        int cleanup = finalizeLevel.indexOf("failure = restoreAfterFinalizeLevelRendering(failure);", finallyBlock);
        int fullscreenFalse = finalizeReset.indexOf("isRenderingFullScreenPass = false;");
        int preparedFalse = finalizeReset.indexOf("prepared = false;");
        int bindMain = finalizeRestore.indexOf(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);");
        int fixedFunctionRestoreCall = finalizeRestore.indexOf(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::restorePostWorldFixedFunctionState);",
            bindMain);

        assertTrue("World programs must be unbound before the postprocess boundary",
            unbind >= 0);
        assertTrue("Composite/final postprocess should run after world rendering has ended",
            worldFalse > unbind);
        assertTrue("renderStage must be NONE before center-depth/composite/final work",
            phaseNone > worldFalse && overrideNull > phaseNone && fullscreenTrue > overrideNull);
        assertTrue("Fullscreen postprocess should be guarded like DeferredWorldRenderingPipeline",
            composite > fullscreenTrue && cleanup > finallyBlock && fullscreenFalse >= 0);
        assertTrue("Prepared state should not be cleared until after postprocess completes",
            preparedFalse >= 0 && bindMain > finalizeRestore.indexOf("resetFrameLifecycleAfterFinalize();"));
        assertTrue("1.12 GUI rendering expects shader finalization to restore fixed-function texture/color state",
            fixedFunctionRestoreCall > bindMain);
        assertTrue(fixedFunctionRestore.contains("OculusRenderSystem.restoreDefaultActiveTexture();"));
        assertTrue(fixedFunctionRestore.contains("GlStateManager.enableTexture2D();"));
        assertTrue(fixedFunctionRestore.contains("GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);"));
        assertTrue(fixedFunctionRestore.contains("GlStateManager.matrixMode(GL11.GL_MODELVIEW);"));
    }

    @Test
    public void finalizeRunsFullPostprocessSequenceWithoutGbufferBoundShortCircuitLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int runComposite = source.indexOf("private void runCompositePass()");
        int runColorSpace = source.indexOf("private void runColorSpaceConversion()", runComposite);
        String body = source.substring(runComposite, runColorSpace);

        int centerDepth = body.indexOf("centerDepthSampler.sampleCenterDepth();");
        int composite = body.indexOf("compositeRenderer.renderAll();", centerDepth);
        int finalPass = body.indexOf("finalPassRenderer.render();", composite);
        int colorSpace = body.indexOf("runColorSpaceConversion();", finalPass);
        int markUnbound = body.indexOf("gbufferBound = false;", colorSpace);

        assertTrue(runComposite >= 0);
        assertFalse("The 1.16.5 finalize path does not skip center-depth/composite/final work based on gbufferBound",
            body.contains("if (!gbufferBound)"));
        assertTrue("Center depth must be sampled before composite rendering", centerDepth >= 0);
        assertTrue("Composite rendering must run before the final pass", composite > centerDepth);
        assertTrue("Final pass must run before color-space conversion", finalPass > composite);
        assertTrue("Color-space conversion must run before gbufferBound is cleared", colorSpace > finalPass);
        assertTrue(markUnbound > colorSpace);
    }

    @Test
    public void finalPostprocessRefreshesStorageBufferBindingsBeforeCompositeAndFinalPass() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String body = methodBody(source, "private void runCompositePass()");

        int requireCenterDepth = body.indexOf(
            "requirePipelineResource(centerDepthSampler, \"center depth sampler\", \"final postprocess\");");
        int sampleCenterDepth = body.indexOf("centerDepthSampler.sampleCenterDepth();", requireCenterDepth);
        int bindBeforeComposite = body.indexOf("shaderStorageBufferManager.bindAll();", sampleCenterDepth);
        int composite = body.indexOf("compositeRenderer.renderAll();", bindBeforeComposite);
        int bindBeforeFinal = body.indexOf("shaderStorageBufferManager.bindAll();", composite);
        int finalPass = body.indexOf("finalPassRenderer.render();", bindBeforeFinal);
        int colorSpace = body.indexOf("runColorSpaceConversion();", finalPass);

        assertTrue("Final postprocess must validate resources before sampling center depth",
            requireCenterDepth >= 0 && sampleCenterDepth > requireCenterDepth);
        assertTrue("Composite work must see freshly rebound shader storage buffers",
            bindBeforeComposite > sampleCenterDepth && composite > bindBeforeComposite);
        assertTrue("Final pass work must refresh shader storage buffers after composite dispatches",
            bindBeforeFinal > composite && finalPass > bindBeforeFinal);
        assertTrue("Color-space conversion must remain after final pass work",
            colorSpace > finalPass);
    }

    @Test
    public void inFramePostprocessResourcesFailFastInsteadOfSilentSkips() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String beginHand = methodBody(source, "public void beginHand()");
        String beginTranslucents = methodBody(source, "public void beginTranslucents()");
        String runPrepare = methodBody(source, "private void runPreparePass()");
        String runComposite = methodBody(source, "private void runCompositePass()");
        String clearRenderTargets = methodBody(source, "private void clearRenderTargets()");

        assertTrue(source.contains("private static <T> T requirePipelineResource"));
        assertTrue(source.contains("throw new IllegalStateException(operation + \" requires initialized \" + resourceName);"));

        assertTrue(beginHand.contains("requirePipelineResource(renderTargets, \"render targets\", \"beginHand\");"));
        assertTrue(beginTranslucents.contains(
            "requirePipelineResource(renderTargets, \"render targets\", \"beginTranslucents\");"));
        assertTrue(beginTranslucents.contains(
            "requirePipelineResource(deferredRenderer, \"deferred renderer\", \"beginTranslucents\");"));
        assertTrue(runPrepare.contains(
            "requirePipelineResource(prepareRenderer, \"prepare renderer\", \"prepare pass\");"));
        assertTrue(runComposite.contains(
            "requirePipelineResource(centerDepthSampler, \"center depth sampler\", \"final postprocess\");"));
        assertTrue(runComposite.contains(
            "requirePipelineResource(compositeRenderer, \"composite renderer\", \"final postprocess\");"));
        assertTrue(runComposite.contains(
            "requirePipelineResource(finalPassRenderer, \"final pass renderer\", \"final postprocess\");"));
        assertTrue(clearRenderTargets.contains(
            "requirePipelineResource(renderTargets, \"render targets\", \"render-target clear\");"));

        assertFalse("Final postprocess must not skip center-depth sampling when the frame lifecycle is active",
            runComposite.contains("if (centerDepthSampler != null)"));
        assertFalse("Final postprocess must not skip composite rendering when the frame lifecycle is active",
            runComposite.contains("if (compositeRenderer != null)"));
        assertFalse("Final postprocess must not replace a missing final pass with a framebuffer fallback",
            runComposite.contains("if (finalPassRenderer != null)"));
        assertFalse(runComposite.contains("OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);"));
    }

    @Test
    public void validationDumpsBracketDeferredPassBeforeComposite() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String beginTranslucents = methodBody(source, "public void beginTranslucents()");

        int preReadSet = beginTranslucents.indexOf("Set<Integer> preDeferredReadBuffers = getActiveReadBuffers();");
        int preColor0 = beginTranslucents.indexOf(
            "dumpValidationRenderTarget(\"pre-deferred-colortex0\", 0, preDeferredReadBuffers);",
            preReadSet);
        int preColor3 = beginTranslucents.indexOf(
            "dumpValidationRenderTarget(\"pre-deferred-colortex3\", 3, preDeferredReadBuffers);",
            preColor0);
        int preDepth = beginTranslucents.indexOf("dumpValidationDepthTargets(\"pre-deferred\");", preColor3);
        int markAfterOpaque = beginTranslucents.indexOf("isBeforeTranslucent = false;", preDepth);
        int copyDepth = beginTranslucents.indexOf("renderTargets.copyPreTranslucentDepth();", markAfterOpaque);
        int postCopyDepth = beginTranslucents.indexOf(
            "dumpValidationDepthTargets(\"post-pretranslucent-depth-copy\");",
            copyDepth);
        int deferred = beginTranslucents.indexOf("deferredRenderer.renderAll();", postCopyDepth);
        int postReadSet = beginTranslucents.indexOf("Set<Integer> postDeferredReadBuffers = bufferFlipper == null",
            deferred);
        int postColor0 = beginTranslucents.indexOf(
            "dumpValidationRenderTarget(\"post-deferred-colortex0\", 0, postDeferredReadBuffers);",
            postReadSet);
        int postColor3 = beginTranslucents.indexOf(
            "dumpValidationRenderTarget(\"post-deferred-colortex3\", 3, postDeferredReadBuffers);",
            postColor0);

        assertTrue("Validation must capture the opaque gbuffer before the deferred fullscreen pass mutates it",
            preReadSet >= 0 && preColor0 > preReadSet && preColor3 > preColor0
                && preDepth > preColor3 && markAfterOpaque > preDepth);
        assertTrue("Validation must capture copied world depth before the deferred fullscreen pass reads it",
            copyDepth > markAfterOpaque && postCopyDepth > copyDepth && deferred > postCopyDepth);
        assertTrue("Validation must capture the post-deferred read side before translucent rendering resumes",
            deferred > markAfterOpaque && postReadSet > deferred && postColor0 > postReadSet && postColor3 > postColor0);
    }

    @Test
    public void validationDumpsDepthTargetsBeforeComposite() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String runComposite = methodBody(source, "private void runCompositePass()");
        String dumpDepthTargets = methodBody(source, "private void dumpValidationDepthTargets");

        int preColor0 = runComposite.indexOf("dumpValidationRenderTarget(\"pre-composite-colortex0\"");
        int preColor3 = runComposite.indexOf("dumpValidationRenderTarget(\"pre-composite-colortex3\"", preColor0);
        int preDepth = runComposite.indexOf("dumpValidationDepthTargets(\"pre-composite\");", preColor3);
        int centerDepth = runComposite.indexOf("centerDepthSampler.sampleCenterDepth();", preDepth);

        assertTrue("Composite validation must capture depth before center-depth sampling and composite passes",
            preColor0 >= 0 && preColor3 > preColor0 && preDepth > preColor3 && centerDepth > preDepth);
        assertTrue(dumpDepthTargets.contains("OculusRuntimeValidation.dumpDepthTexture("));
        assertTrue(dumpDepthTargets.contains("label + \"-depthtex0\""));
        assertTrue(dumpDepthTargets.contains("renderTargets.getCurrentDepthTexture()"));
        assertTrue(dumpDepthTargets.contains("label + \"-depthtex1-no-translucents\""));
        assertTrue(dumpDepthTargets.contains("renderTargets.getDepthTextureNoTranslucents().getTextureId()"));
        assertTrue(dumpDepthTargets.contains("label + \"-depthtex2-no-hand\""));
        assertTrue(dumpDepthTargets.contains("renderTargets.getDepthTextureNoHand().getTextureId()"));
    }

    @Test
    public void syncProgramSkipsWorldProgramBindingDuringFullscreenPostprocess() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int syncProgram = source.indexOf("public void syncProgram()");
        int availability = source.indexOf("InputAvailability availability =", syncProgram);
        String guard = source.substring(syncProgram, availability);

        assertTrue(syncProgram >= 0);
        assertTrue("World program sync should be scoped to active world rendering",
            guard.contains("!isRenderingWorld"));
        assertTrue("World program sync must not run while fullscreen postprocess is active",
            guard.contains("isRenderingFullScreenPass"));
        assertTrue("World program sync must not run while a post chain owns framebuffer state",
            guard.contains("isPostChain"));
        assertTrue("World program sync should remain eligible while a shader-owned gbuffer target is bound",
            guard.contains("boolean worldTargetReady = isMainBound || gbufferBound || isRenderingShadow;"));
        assertTrue("World program sync must not run when no world render target is available",
            guard.contains("!worldTargetReady"));
    }

    @Test
    public void legacyEntityDrawsHaveDirectProgramResyncPath() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String body = methodBody(source, "public void syncEntityProgramForLegacyDraw()");

        assertTrue(body.contains("!prepared || !isRenderingWorld || isRenderingFullScreenPass"));
        assertTrue(body.contains("isPostChain || !shadersCompiled"));
        assertTrue(body.contains("RenderCondition condition = getCondition(WorldRenderingPhase.ENTITIES);"));
        assertTrue(body.contains("normalizeLegacyImmediateAvailability(condition, rawAvailability);"));
        assertTrue(body.contains("ResolvedProgram resolved = resolveProgram(condition, availability);"));
        assertTrue(body.contains("OculusRuntimeValidation.logWorldProgramSelection("));
        assertTrue(body.contains("bindProgram(resolved);"));
    }

    @Test
    public void renderTargetStateListenerTracksPostChainAndMainFramebufferLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertFalse("Shader pipeline should expose a real render-target state listener, not NOP",
            source.contains("private final RenderTargetStateListener renderTargetStateListener = RenderTargetStateListener.NOP;"));

        int listener = source.indexOf("private final RenderTargetStateListener renderTargetStateListener = new RenderTargetStateListener()");
        int shadowSection = source.indexOf("// Shadow rendering", listener);
        String body = source.substring(listener, shadowSection);

        assertTrue(listener >= 0);
        assertTrue(body.contains("public void beginPostChain()"));
        assertTrue(body.contains("isPostChain = true;"));
        assertTrue(body.contains("unbindProgram();"));
        assertTrue(body.contains("public void endPostChain()"));
        assertTrue(body.contains("isPostChain = false;"));
        assertTrue(body.contains("public void setIsMainBound(boolean bound)"));
        assertTrue("Shader-owned gbuffer binds must not make later world phase syncs ineligible",
            body.contains("if (bindingShaderFramebuffer && !bound)"));
        assertTrue(body.contains("isMainBound = true;"));
        assertTrue(body.contains("return;"));
        assertTrue(body.contains("isMainBound = bound;"));
        assertTrue(body.contains("if (!prepared || !isRenderingWorld || isRenderingFullScreenPass || isPostChain)"));
        assertTrue(body.contains("activeGbufferProgram = null;"));
        assertTrue(body.contains("} else {\n                unbindProgram();"));
        assertFalse("Rebinding the main framebuffer should only invalidate the active pass; the next phase/input change resyncs it",
            body.contains("syncProgram();"));
    }

    @Test
    public void renderTargetPreparationRestoresTextureUnitAndMainFramebufferLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int clearMethod = source.indexOf("private void clearRenderTargets()");
        int failureLocal = source.indexOf("Throwable failure = null;", clearMethod);
        int tryBlock = source.indexOf("try {", failureLocal);
        int restoreTexture = source.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();", tryBlock);
        int clearDisabledShadow = source.indexOf("clearDisabledShadowTargets();", restoreTexture);
        int catchBlock = source.indexOf("catch (RuntimeException | Error exception)", clearDisabledShadow);
        int recordFailure = source.indexOf("failure = exception;", catchBlock);
        int rethrowPrimary = source.indexOf("throw exception;", recordFailure);
        int finallyBlock = source.indexOf("finally {", rethrowPrimary);
        int bindMain = source.indexOf(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);",
            finallyBlock);
        int rethrowCleanup = source.indexOf("rethrowCleanupFailure(failure);", bindMain);

        assertTrue(clearMethod >= 0);
        assertTrue("Render-target preparation must enter a cleanup-guarded block before mutating GL texture state",
            failureLocal > clearMethod && tryBlock > failureLocal && restoreTexture > tryBlock);
        assertTrue("Render-target preparation should start from the default texture unit",
            restoreTexture < clearDisabledShadow);
        assertTrue("Render-target preparation must track primary setup/clear failures before cleanup",
            catchBlock > clearDisabledShadow
                && recordFailure > catchBlock && rethrowPrimary > recordFailure);
        assertTrue("Main framebuffer rebinding must run even when no clear pass executes",
            finallyBlock > rethrowPrimary && bindMain > finallyBlock && rethrowCleanup > bindMain);

        assertTrue(source.contains("private static void bindMainFramebufferAfterRenderTargetPreparation()"));
        assertTrue(source.contains("mainFramebuffer.bindFramebuffer(true);"));
        assertTrue(source.contains("OculusRenderSystem.restoreFramebufferBindings(0, 0, 0);"));
        assertFalse(source.contains("OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);"));
        assertTrue(source.contains("GL11.glViewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);"));
    }

    @Test
    public void framebufferResizeReusesRenderTargetsAndRecalculatesPassSizesLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int method = source.indexOf("private void ensureFramebufferDimensionsUpToDate()");
        int nextMethod = source.indexOf("private void recalculateRenderTargetDependentSizes()", method);
        String resizeCheck = source.substring(method, nextMethod);
        String rebuild = methodBody(source, "private void rebuildRenderTargets");

        int framebufferResize = resizeCheck.indexOf("framebufferManager.resizeIfNeeded(displayWidth, displayHeight);");
        int nullGuard = resizeCheck.indexOf("if (renderTargets == null)", framebufferResize);
        int initialRebuild = resizeCheck.indexOf("rebuildRenderTargets(displayWidth, displayHeight);", nullGuard);
        int returnAfterRebuild = resizeCheck.indexOf("return;", initialRebuild);
        int resizeIfNeeded = resizeCheck.indexOf("boolean changed = renderTargets.resizeIfNeeded(", returnAfterRebuild);
        int depthTexture = resizeCheck.indexOf("framebufferManager.getDepthTexture(),", resizeIfNeeded);
        int depthVersion = resizeCheck.indexOf("framebufferManager.getDepthTextureVersion(),", depthTexture);
        int depthFormat = resizeCheck.indexOf("getDepthBufferFormat(framebufferManager.getDepthTexture())",
            depthVersion);
        int changedGuard = resizeCheck.indexOf("if (changed)", depthFormat);
        int markDirty = resizeCheck.indexOf("renderTargetDependentsDirty = true;", changedGuard);
        int dirtyGuard = resizeCheck.indexOf("if (renderTargetDependentsDirty)", markDirty);
        int customImages = resizeCheck.indexOf("customImageManager.initializeOrResize(displayWidth, displayHeight);",
            dirtyGuard);
        int recalc = resizeCheck.indexOf("recalculateRenderTargetDependentSizes();", customImages);
        int clearDirty = resizeCheck.indexOf("renderTargetDependentsDirty = false;", recalc);
        int rebuildClean = rebuild.indexOf("renderTargetDependentsDirty = false;");

        assertTrue(method >= 0);
        assertTrue(source.contains("private boolean renderTargetDependentsDirty;"));
        assertTrue(framebufferResize >= 0);
        assertTrue(nullGuard > framebufferResize);
        assertTrue(initialRebuild > nullGuard);
        assertTrue(returnAfterRebuild > initialRebuild);
        assertTrue(resizeIfNeeded > returnAfterRebuild);
        assertTrue(depthTexture > resizeIfNeeded);
        assertTrue(depthVersion > depthTexture);
        assertTrue(depthFormat > depthVersion);
        assertTrue(changedGuard > depthFormat);
        assertTrue("Resize must mark dependent renderers dirty before any fallible recalculation work",
            markDirty > changedGuard);
        assertTrue("Dirty dependent renderers must be retried even if the previous recalculation failed after resize",
            dirtyGuard > markDirty);
        assertTrue(customImages > dirtyGuard);
        assertTrue(recalc > customImages);
        assertTrue("Dependent resize work is only clean after custom images and postprocessor framebuffers rebuild",
            clearDirty > recalc);
        assertTrue("Pipeline must derive the depth format from the actual depth texture like 1.16.5",
            source.contains("TextureInfoCache.INSTANCE.getInfo(depthTexture).getInternalFormat()"));
        assertTrue(source.contains("return DepthBufferFormat.fromGlEnumOrDefault(internalFormat);"));
        assertTrue("Initial RenderTargets creation must use the detected depth texture format",
            rebuild.contains("getDepthBufferFormat(framebufferManager.getDepthTexture())"));
        assertTrue("A full render-target rebuild publishes an already-clean dependent graph",
            rebuildClean > rebuild.indexOf("colorSpaceHeight = safeHeight;"));
        assertFalse("Resize should not tear down and recompile postprocessors when RenderTargets can resize in place",
            resizeCheck.substring(returnAfterRebuild).contains("rebuildRenderTargets(displayWidth, displayHeight);"));
    }

    @Test
    public void resizeRecalculationUpdatesPostprocessorFramebuffersAndClearPasses() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int method = source.indexOf("private void recalculateRenderTargetDependentSizes()");
        int nextMethod = source.indexOf("private Framebuffer getOrCreateGbufferFramebuffer", method);
        String recalc = source.substring(method, nextMethod);

        int prepare = recalc.indexOf("prepareRenderer.recalculateSizes();");
        int deferred = recalc.indexOf("deferredRenderer.recalculateSizes();", prepare);
        int composite = recalc.indexOf("compositeRenderer.recalculateSizes();", deferred);
        int finalPass = recalc.indexOf("finalPassRenderer.recalculateSwapPassSize();", composite);
        int replacementFull = recalc.indexOf("List<ClearPass> replacementClearPassesFull = Collections.emptyList();",
            finalPass);
        int replacementPartial = recalc.indexOf("List<ClearPass> replacementClearPasses = Collections.emptyList();",
            replacementFull);
        int completeFalse = recalc.indexOf("boolean complete = false;", replacementPartial);
        int tryBlock = recalc.indexOf("try {", completeFalse);
        int recreateFull = recalc.indexOf(
            "replacementClearPassesFull = ClearPassCreator.createClearPasses(renderTargets, true, renderTargetDirectives);",
            tryBlock);
        int recreatePartial = recalc.indexOf(
            "replacementClearPasses = ClearPassCreator.createClearPasses(renderTargets, false, renderTargetDirectives);",
            recreateFull);
        int completeTrue = recalc.indexOf("complete = true;", recreatePartial);
        int cleanupCatch = recalc.indexOf("catch (RuntimeException | Error exception)", completeTrue);
        int failedFullCleanup = recalc.indexOf(
            "failure = destroyClearPassFramebuffers(failure, replacementClearPassesFull);",
            cleanupCatch);
        int failedPartialCleanup = recalc.indexOf(
            "failure = destroyClearPassFramebuffers(failure, replacementClearPasses);",
            failedFullCleanup);
        int suppressCleanup = recalc.indexOf("addSuppressedCleanupFailure(exception, failure);",
            failedPartialCleanup);
        int rethrowCleanup = recalc.indexOf("throw exception;", suppressCleanup);
        int previousFull = recalc.indexOf("List<ClearPass> previousClearPassesFull = clearPassesFull;",
            rethrowCleanup);
        int previousPartial = recalc.indexOf("List<ClearPass> previousClearPasses = clearPasses;", previousFull);
        int assignFull = recalc.indexOf("clearPassesFull = replacementClearPassesFull;", previousPartial);
        int assignPartial = recalc.indexOf("clearPasses = replacementClearPasses;", assignFull);
        int previousCleanup = recalc.indexOf("Throwable failure = null;", assignPartial);
        int destroyPreviousFull = recalc.indexOf(
            "failure = destroyClearPassFramebuffers(failure, previousClearPassesFull);",
            assignPartial);
        int destroyPreviousPartial = recalc.indexOf(
            "failure = destroyClearPassFramebuffers(failure, previousClearPasses);",
            destroyPreviousFull);
        int rethrowPreviousCleanup = recalc.indexOf("rethrowCleanupFailure(failure);", destroyPreviousPartial);

        assertTrue(method >= 0);
        assertTrue(prepare >= 0);
        assertTrue(deferred > prepare);
        assertTrue(composite > deferred);
        assertTrue(finalPass > composite);
        assertTrue(replacementFull > finalPass);
        assertTrue(replacementPartial > replacementFull);
        assertTrue(completeFalse > replacementPartial);
        assertTrue(tryBlock > completeFalse);
        assertTrue("Replacement clear-pass framebuffers must be created before old clear passes are destroyed",
            recreateFull > tryBlock);
        assertTrue(recreatePartial > recreateFull);
        assertTrue(completeTrue > recreatePartial);
        assertTrue("Partial replacement clear passes must be destroyed if recreation fails",
            cleanupCatch > completeTrue && failedFullCleanup > cleanupCatch && failedPartialCleanup > failedFullCleanup);
        assertTrue("Clear-pass resize cleanup failures must be suppressed onto the resize failure",
            suppressCleanup > failedPartialCleanup && rethrowCleanup > suppressCleanup);
        assertTrue("The old clear-pass fields must only be swapped after replacement creation completes",
            previousFull > rethrowCleanup && previousPartial > previousFull);
        assertTrue(assignFull > previousPartial);
        assertTrue(assignPartial > assignFull);
        assertTrue("Previous clear-pass framebuffers must be deleted after the replacements are installed",
            previousCleanup > assignPartial && destroyPreviousFull > previousCleanup
                && destroyPreviousPartial > destroyPreviousFull && rethrowPreviousCleanup > destroyPreviousPartial);
        assertFalse("Resize recalculation must not delete old clear-pass framebuffers before replacement creation",
            recalc.contains("for (ClearPass clearPass : clearPassesFull)"));
    }

    @Test
    public void postprocessBufferFlipSnapshotsFeedWorldAndFinalPassesLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String rebuild = methodBody(source, "private void rebuildRenderTargets");
        String activeReads = methodBody(source, "private Set<Integer> getActiveReadBuffers");
        String destroyPost = methodBody(source, "private void destroyPostProcessors");

        int newFlipper = rebuild.indexOf(
            "replacementBufferFlipper = new net.oculus.postprocess.BufferFlipper();");
        int prepareRenderer = rebuild.indexOf(
            "replacementPrepareRenderer = new net.oculus.postprocess.CompositeRenderer(",
            newFlipper);
        int preparePreFlips = rebuild.indexOf("directives.getExplicitFlips(\"prepare_pre\")", prepareRenderer);
        int prepareSnapshot = rebuild.indexOf(
            "replacementFlippedAfterPrepare = replacementBufferFlipper.snapshot();", preparePreFlips);
        int deferredRenderer = rebuild.indexOf(
            "replacementDeferredRenderer = new net.oculus.postprocess.CompositeRenderer(",
            prepareSnapshot);
        int deferredPreFlips = rebuild.indexOf("directives.getExplicitFlips(\"deferred_pre\")", deferredRenderer);
        int translucentSnapshot = rebuild.indexOf(
            "replacementFlippedAfterTranslucent = replacementBufferFlipper.snapshot();",
            deferredPreFlips);
        int compositeRenderer = rebuild.indexOf(
            "replacementCompositeRenderer = new net.oculus.postprocess.CompositeRenderer(",
            translucentSnapshot);
        int compositePreFlips = rebuild.indexOf("directives.getExplicitFlips(\"composite_pre\")",
            compositeRenderer);
        int finalRenderer = rebuild.indexOf(
            "replacementFinalPassRenderer = new net.oculus.postprocess.FinalPassRenderer(",
            compositePreFlips);
        int finalSnapshot = rebuild.indexOf("replacementBufferFlipper.snapshot(),", finalRenderer);
        int flippedAtLeastOnce = rebuild.indexOf(
            "replacementCompositeRenderer.getFlippedAtLeastOnceFinal()", finalSnapshot);

        assertTrue("A single BufferFlipper must flow through prepare, deferred, composite, and final construction",
            newFlipper >= 0);
        assertTrue("Prepare pre-flips must be applied inside prepare renderer construction before its snapshot is stored",
            prepareRenderer > newFlipper && preparePreFlips > prepareRenderer && prepareSnapshot > preparePreFlips);
        assertTrue("Deferred construction must start from the post-prepare flip state and publish the translucent snapshot",
            deferredRenderer > prepareSnapshot && deferredPreFlips > deferredRenderer
                && translucentSnapshot > deferredPreFlips);
        assertTrue("Composite construction must start from the post-deferred flip state before final captures its read set",
            compositeRenderer > translucentSnapshot && compositePreFlips > compositeRenderer);
        assertTrue("Final pass must receive the post-composite read snapshot",
            finalRenderer > compositePreFlips && finalSnapshot > finalRenderer);
        assertTrue("Final pass must receive composite flipped-at-least-once state for custom texture gating",
            flippedAtLeastOnce > finalSnapshot);
        assertTrue("World gbuffer reads before translucents must use the post-prepare snapshot",
            activeReads.contains("return isBeforeTranslucent ? flippedAfterPrepare : flippedAfterTranslucent;"));
        assertTrue("Postprocessor teardown must clear stale flip snapshots before any rebuild",
            destroyPost.contains("flippedAfterPrepare = com.google.common.collect.ImmutableSet.of();"));
        assertTrue("Postprocessor teardown must clear stale translucent flip snapshots before any rebuild",
            destroyPost.contains("flippedAfterTranslucent = com.google.common.collect.ImmutableSet.of();"));
        assertTrue("Postprocessor teardown must clear stale resize-retry state before any rebuild",
            destroyPost.contains("renderTargetDependentsDirty = false;"));
        int rethrowCleanup = destroyPost.indexOf("rethrowCleanupFailure(failure);");
        int finallyCleanup = destroyPost.indexOf("} finally {", rethrowCleanup);
        int clearTargets = destroyPost.indexOf("renderTargets = null;", finallyCleanup);
        int clearFlipper = destroyPost.indexOf("bufferFlipper = null;", clearTargets);
        int clearDirty = destroyPost.indexOf("renderTargetDependentsDirty = false;", clearFlipper);
        assertTrue("Postprocessor teardown must clear stale renderer and target handles even when cleanup fails",
            rethrowCleanup >= 0 && finallyCleanup > rethrowCleanup
                && clearTargets > finallyCleanup && clearFlipper > clearTargets && clearDirty > clearFlipper);
    }

    @Test
    public void renderTargetRebuildPublishesOnlyAfterStagedResourcesComplete() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String rebuild = methodBody(source, "private void rebuildRenderTargets");
        String stagedDestroy = methodBody(source, "private static Throwable destroyStagedRenderTargetRebuild");
        String previousDestroy = methodBody(source, "private static Throwable destroyPreviousRenderTargetRebuild");

        int stagedCenter = rebuild.indexOf(
            "net.oculus.postprocess.CenterDepthSampler replacementCenterDepthSampler = null;");
        int stagedTargets = rebuild.indexOf(
            "net.oculus.rendertarget.RenderTargets replacementRenderTargets = null;",
            stagedCenter);
        int stagedPrepare = rebuild.indexOf(
            "net.oculus.postprocess.CompositeRenderer replacementPrepareRenderer = null;",
            stagedTargets);
        int stagedColorSpace = rebuild.indexOf("ColorSpaceConverter replacementColorSpaceConverter = null;",
            stagedPrepare);
        int stagedBindings = rebuild.indexOf(
            "Map<String, TextureBinding> replacementRenderTargetBindings = Collections.emptyMap();",
            stagedColorSpace);
        int completeFalse = rebuild.indexOf("boolean complete = false;", stagedBindings);
        int tryBlock = rebuild.indexOf("try {", completeFalse);
        int createTargets = rebuild.indexOf(
            "replacementRenderTargets = new net.oculus.rendertarget.RenderTargets(",
            tryBlock);
        int stagedTargetsLocal = rebuild.indexOf(
            "final net.oculus.rendertarget.RenderTargets stagedRenderTargets = replacementRenderTargets;",
            createTargets);
        int createBindings = rebuild.indexOf(
            "replacementRenderTargetBindings = createRenderTargetBindings(stagedRenderTargets);",
            stagedTargetsLocal);
        int createCenter = rebuild.indexOf(
            "replacementCenterDepthSampler = new net.oculus.postprocess.CenterDepthSampler(",
            createBindings);
        int stagedDepthSupplier = rebuild.indexOf(
            "stagedRenderTargets::getCurrentDepthTexture",
            createCenter);
        int createClearPasses = rebuild.indexOf(
            "replacementClearPassesFull = ClearPassCreator.createClearPasses(",
            stagedDepthSupplier);
        int createPrepare = rebuild.indexOf(
            "replacementPrepareRenderer = new net.oculus.postprocess.CompositeRenderer(",
            createClearPasses);
        int createFinal = rebuild.indexOf(
            "replacementFinalPassRenderer = new net.oculus.postprocess.FinalPassRenderer(",
            createPrepare);
        int createColorSpace = rebuild.indexOf(
            "replacementColorSpaceConverter = createColorSpaceConverter(",
            createFinal);
        int ensureForcedShadow = rebuild.indexOf("ensureForcedShadowMapInitialized();", createColorSpace);
        int completeTrue = rebuild.indexOf("complete = true;", ensureForcedShadow);
        int cleanupCatch = rebuild.indexOf("catch (RuntimeException | Error exception)", completeTrue);
        int failureGuard = rebuild.indexOf("if (!complete)", cleanupCatch);
        int cleanupCall = rebuild.indexOf("Throwable failure = destroyStagedRenderTargetRebuild(", failureGuard);
        int newShadowCleanup = rebuild.indexOf("if (!hadShadowMapBefore && shadowMap != null)", cleanupCall);
        int shadowDestroy = rebuild.indexOf("failure = runCleanup(failure, shadowMap::destroy);", newShadowCleanup);
        int suppressedCleanup = rebuild.indexOf("addSuppressedCleanupFailure(exception, failure);", shadowDestroy);
        int rethrow = rebuild.indexOf("throw exception;", suppressedCleanup);
        int previousGbuffersBefore = rebuild.indexOf(
            "Map<String, Framebuffer> previousGbufferFramebuffersBeforeTranslucent =",
            rethrow);
        int previousGbuffersAfter = rebuild.indexOf(
            "Map<String, Framebuffer> previousGbufferFramebuffersAfterTranslucent =",
            previousGbuffersBefore);
        int previousCenter = rebuild.indexOf(
            "net.oculus.postprocess.CenterDepthSampler previousCenterDepthSampler = centerDepthSampler;",
            previousGbuffersAfter);
        int previousTargets = rebuild.indexOf(
            "net.oculus.rendertarget.RenderTargets previousRenderTargets = renderTargets;",
            previousCenter);
        int previousPrepare = rebuild.indexOf(
            "net.oculus.postprocess.CompositeRenderer previousPrepareRenderer = prepareRenderer;",
            previousTargets);
        int previousColor = rebuild.indexOf(
            "ColorSpaceConverter previousColorSpaceConverter = colorSpaceConverter;",
            previousPrepare);
        int bindingCleanupLocal = rebuild.indexOf("Throwable bindingCleanupFailure;", previousColor);
        int installTry = rebuild.indexOf("try {", previousColor);
        int installBindings = rebuild.indexOf(
            "bindingCleanupFailure = installRenderTargetBindings(replacementRenderTargetBindings);",
            installTry);
        int installCatch = rebuild.indexOf("catch (RuntimeException | Error exception)", installBindings);
        int installCleanupCall = rebuild.indexOf("Throwable failure = destroyStagedRenderTargetRebuild(",
            installCatch);
        int installShadowCleanup = rebuild.indexOf("if (!hadShadowMapBefore && shadowMap != null)",
            installCleanupCall);
        int installShadowDestroy = rebuild.indexOf("failure = runCleanup(failure, shadowMap::destroy);",
            installShadowCleanup);
        int installSuppressedCleanup = rebuild.indexOf("addSuppressedCleanupFailure(exception, failure);",
            installShadowDestroy);
        int installRethrow = rebuild.indexOf("throw exception;", installSuppressedCleanup);
        int clearGbuffersBefore = rebuild.indexOf("gbufferFramebuffersBeforeTranslucent.clear();",
            installRethrow);
        int clearGbuffersAfter = rebuild.indexOf("gbufferFramebuffersAfterTranslucent.clear();",
            clearGbuffersBefore);
        int commitBuffer = rebuild.indexOf("bufferFlipper = replacementBufferFlipper;", rethrow);
        int commitTargets = rebuild.indexOf("renderTargets = replacementRenderTargets;", commitBuffer);
        int commitFinal = rebuild.indexOf("finalPassRenderer = replacementFinalPassRenderer;", commitTargets);
        int commitColor = rebuild.indexOf("colorSpaceConverter = replacementColorSpaceConverter;", commitFinal);
        int failureAggregation = rebuild.indexOf("Throwable failure = bindingCleanupFailure;", commitColor);
        int shadowRendererInvalidation = rebuild.indexOf(
            "failure = destroyActiveShadowRendererForRenderTargetRebuild(failure);",
            failureAggregation);
        int previousCleanup = rebuild.indexOf(
            "failure = destroyPreviousRenderTargetRebuild(",
            shadowRendererInvalidation);
        int previousCleanupRethrow = rebuild.indexOf("rethrowCleanupFailure(failure);", previousCleanup);

        assertTrue(stagedCenter >= 0);
        assertTrue("Rebuild must stage center-depth, render targets, postprocessors, and color conversion before commit",
            stagedTargets > stagedCenter && stagedPrepare > stagedTargets
                && stagedColorSpace > stagedPrepare && stagedBindings > stagedColorSpace);
        assertTrue("Staged construction must run inside a cleanup guard",
            completeFalse > stagedBindings && tryBlock > completeFalse);
        assertTrue("Staged resources must be created before any field publication",
            createTargets > tryBlock && stagedTargetsLocal > createTargets
                && createBindings > stagedTargetsLocal
                && createCenter > createBindings && stagedDepthSupplier > createCenter
                && createClearPasses > stagedDepthSupplier
                && createPrepare > createClearPasses && createFinal > createPrepare
                && createColorSpace > createFinal && ensureForcedShadow > createColorSpace
                && completeTrue > ensureForcedShadow);
        assertFalse("Staged center-depth sampling must not capture the old pipeline renderTargets field",
            rebuild.substring(createCenter, createClearPasses).contains("renderTargets != null"));
        assertTrue("Forced shadow-map allocation must complete inside staged setup before any rebuild fields publish",
            ensureForcedShadow > createColorSpace && ensureForcedShadow < cleanupCatch);
        assertTrue("Failed rebuilds must destroy staged resources before rethrowing",
            failureGuard > cleanupCatch && cleanupCall > failureGuard && newShadowCleanup > cleanupCall);
        assertTrue("Failed rebuilds must clean a newly-created shadow map and suppress cleanup failures",
            shadowDestroy > newShadowCleanup && suppressedCleanup > shadowDestroy && rethrow > suppressedCleanup);
        assertTrue("Previous live resources must be captured only after replacement staging succeeds",
            previousGbuffersBefore > rethrow && previousGbuffersAfter > previousGbuffersBefore
                && previousCenter > previousGbuffersAfter && previousTargets > previousCenter
                && previousPrepare > previousTargets && previousColor > previousPrepare);
        assertTrue("Replacement render-target aliases must install before any replacement fields publish",
            bindingCleanupLocal > previousColor && installTry > bindingCleanupLocal && installBindings > installTry
                && installCatch > installBindings && installCleanupCall > installCatch
                && installShadowCleanup > installCleanupCall && installShadowDestroy > installShadowCleanup
                && installSuppressedCleanup > installShadowDestroy && installRethrow > installSuppressedCleanup);
        assertTrue("Cached gbuffer framebuffer maps must clear after alias installation and before target publication",
            clearGbuffersBefore > previousColor && clearGbuffersAfter > clearGbuffersBefore);
        assertTrue("Renderer fields must publish only after staged aliases and cached framebuffers are handled",
            commitBuffer > clearGbuffersAfter && commitTargets > commitBuffer
                && commitFinal > commitTargets && commitColor > commitFinal);
        assertTrue("Shadow renderers with old render-target bindings must be invalidated before old targets are destroyed",
            failureAggregation > commitColor && shadowRendererInvalidation > failureAggregation);
        assertTrue("Previous live resources must be destroyed only after replacement fields are published",
            previousCleanup > shadowRendererInvalidation && previousCleanupRethrow > previousCleanup);
        assertFalse("Rebuild must not assign renderTargets before staged construction completes",
            rebuild.substring(0, cleanupCatch).contains("renderTargets = replacementRenderTargets;"));
        assertFalse("Rebuild must not assign renderTargets before staged aliases are installed",
            rebuild.substring(0, installBindings).contains("renderTargets = replacementRenderTargets;"));
        assertFalse("Rebuild must not expose a replacement target and then register its aliases",
            rebuild.contains("renderTargets = replacementRenderTargets;\n        registerRenderTargetBindings();"));
        assertFalse("Rebuild must not tear down the live postprocess stack before replacement staging succeeds",
            rebuild.substring(0, rethrow).contains("destroyPostProcessors();"));

        assertTrue(stagedDestroy.contains("runCleanup(failure, stagedPrepareRenderer::destroy);"));
        assertTrue(stagedDestroy.contains("runCleanup(failure, stagedDeferredRenderer::destroy);"));
        assertTrue(stagedDestroy.contains("runCleanup(failure, stagedCompositeRenderer::destroy);"));
        assertTrue(stagedDestroy.contains("runCleanup(failure, stagedFinalPassRenderer::destroy);"));
        assertTrue(stagedDestroy.contains("runCleanup(failure, stagedColorSpaceConverter::destroy);"));
        assertTrue(stagedDestroy.contains("runCleanup(failure, stagedCenterDepthSampler::destroy);"));
        assertTrue(stagedDestroy.contains("runCleanup(failure, stagedRenderTargets::destroy);"));
        assertTrue(stagedDestroy.contains("return failure;"));

        assertTrue(previousDestroy.contains(
            "for (Framebuffer framebuffer : previousGbufferFramebuffersBeforeTranslucent.values())"));
        assertTrue(previousDestroy.contains(
            "failure = destroyGbufferFramebuffer(failure, previousRenderTargets, framebuffer);"));
        assertTrue(previousDestroy.contains(
            "for (Framebuffer framebuffer : previousGbufferFramebuffersAfterTranslucent.values())"));
        assertTrue(previousDestroy.contains("runCleanup(failure, previousPrepareRenderer::destroy);"));
        assertTrue(previousDestroy.contains("runCleanup(failure, previousDeferredRenderer::destroy);"));
        assertTrue(previousDestroy.contains("runCleanup(failure, previousCompositeRenderer::destroy);"));
        assertTrue(previousDestroy.contains("runCleanup(failure, previousFinalPassRenderer::destroy);"));
        assertTrue(previousDestroy.contains("runCleanup(failure, previousColorSpaceConverter::destroy);"));
        assertTrue(previousDestroy.contains("runCleanup(failure, previousCenterDepthSampler::destroy);"));
        assertTrue(previousDestroy.contains("runCleanup(failure, previousRenderTargets::destroy);"));
        assertTrue(previousDestroy.contains("return failure;"));
    }

    @Test
    public void renderTargetRebuildInvalidatesShadowRendererBindingsBeforeOldTargetsAreDestroyed() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String rebuild = methodBody(source, "private void rebuildRenderTargets");
        String destroyShadowRenderer = methodBody(source,
            "private Throwable destroyActiveShadowRendererForRenderTargetRebuild");

        int commitTargets = rebuild.indexOf("renderTargets = replacementRenderTargets;");
        int commitColorSpace = rebuild.indexOf("colorSpaceHeight = safeHeight;", commitTargets);
        int beginFailureAggregation = rebuild.indexOf("Throwable failure = bindingCleanupFailure;", commitColorSpace);
        int invalidateShadowRenderer = rebuild.indexOf(
            "failure = destroyActiveShadowRendererForRenderTargetRebuild(failure);",
            beginFailureAggregation);
        int destroyPreviousTargets = rebuild.indexOf("failure = destroyPreviousRenderTargetRebuild(",
            invalidateShadowRenderer);
        int rethrow = rebuild.indexOf("rethrowCleanupFailure(failure);", destroyPreviousTargets);

        assertTrue("Replacement render targets must publish before shadow renderer invalidation is needed",
            commitTargets >= 0 && commitColorSpace > commitTargets);
        assertTrue("Rebuild cleanup aggregation must preserve alias-install cleanup failures",
            beginFailureAggregation > commitColorSpace);
        assertTrue("The old ShadowRenderer must be destroyed after replacement publication",
            invalidateShadowRenderer > beginFailureAggregation);
        assertTrue("Old render targets must not be destroyed while a ShadowRenderer still owns bindings to them",
            destroyPreviousTargets > invalidateShadowRenderer && rethrow > destroyPreviousTargets);

        assertTrue(destroyShadowRenderer.contains("if (shadowRenderer != null)"));
        assertTrue(destroyShadowRenderer.contains("failure = runCleanup(failure, shadowRenderer::destroy);"));
        assertTrue("Shadow renderer field must be cleared so the next shadow pass recompiles against live targets",
            destroyShadowRenderer.contains("shadowRenderer = null;"));
        assertTrue("Renderer initialization must be invalidated even if cleanup reports a failure",
            destroyShadowRenderer.contains("shadowRendererInitialized = false;"));
        assertTrue("Render-target rebuild must clear stale per-frame shadow preparation state",
            destroyShadowRenderer.contains("shadowRenderTargetsPreparedThisFrame = false;"));
        assertTrue(destroyShadowRenderer.contains("return failure;"));
    }

    @Test
    public void renderTargetBindingsAreCreatedFromStagedTargetsAndInstalledTransactionally() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String create = methodBody(source, "private Map<String, TextureBinding> createRenderTargetBindings");
        String install = methodBody(source, "private Throwable installRenderTargetBindings");
        String unregister = methodBody(source, "private void unregisterRenderTargetBindings");

        int linkedMap = create.indexOf("Map<String, TextureBinding> bindings = new LinkedHashMap<>();");
        int targetLookup = create.indexOf("targets.get(index)", linkedMap);
        int missingTargetGuard = create.indexOf("if (target == null)", targetLookup);
        int missingTargetThrow = create.indexOf(
            "throw new IllegalStateException(\"Render target colortex\" + index + \" is not configured\");",
            missingTargetGuard);
        int currentBinding = create.indexOf(
            "IrisSamplers.renderTargetBinding(\n                targets, this::getCurrentRenderTargetReadBuffers, index, false);",
            missingTargetThrow);
        int altBinding = create.indexOf(
            "IrisSamplers.renderTargetBinding(\n                targets, this::getCurrentRenderTargetReadBuffers, index, true);",
            currentBinding);
        int colorAlias = create.indexOf("bindings.put(\"colortex\" + index, currentBinding);", altBinding);
        int depthBinding = create.indexOf(
            "TextureBinding depthBinding = TextureBinding.texture2D(() -> targets.getCurrentDepthTexture());",
            colorAlias);
        int depthNoTranslucents = create.indexOf("targets.getDepthTextureNoTranslucents().getTextureId()",
            depthBinding);
        int depthNoHand = create.indexOf("targets.getDepthTextureNoHand().getTextureId()",
            depthNoTranslucents);
        int returnBindings = create.indexOf("return bindings;", depthNoHand);

        assertTrue("Render-target bindings must be assembled in deterministic alias order",
            linkedMap >= 0);
        assertTrue("Binding creation must validate the staged target, not the currently published field",
            targetLookup > linkedMap && missingTargetGuard > targetLookup && missingTargetThrow > missingTargetGuard);
        assertTrue("Render-target sampler suppliers must close over the staged RenderTargets instance",
            currentBinding > missingTargetThrow && altBinding > currentBinding && colorAlias > altBinding);
        assertTrue("Depth sampler suppliers must also close over the staged RenderTargets instance",
            depthBinding > colorAlias && depthNoTranslucents > depthBinding
                && depthNoHand > depthNoTranslucents && returnBindings > depthNoHand);
        assertFalse("Creating staged bindings must not mutate the global sampler registry",
            create.contains("TextureBindingRegistry.register"));
        assertFalse("Creating staged bindings must not read through the live pipeline renderTargets field",
            create.contains("renderTargets.get"));

        int previousBindings = install.indexOf(
            "Map<String, TextureBinding> previousBindings = new HashMap<>(registeredRenderTargetBindings);");
        int installedBindings = install.indexOf("Map<String, TextureBinding> installedBindings = new HashMap<>();",
            previousBindings);
        int registerReplacement = install.indexOf("TextureBindingRegistry.register(entry.getKey(), entry.getValue());",
            installedBindings);
        int rememberReplacement = install.indexOf("installedBindings.put(entry.getKey(), entry.getValue());",
            registerReplacement);
        int catchBlock = install.indexOf("catch (RuntimeException | Error exception)", rememberReplacement);
        int rollbackReplacement = install.indexOf("failure = unregisterTextureBindings(failure, installedBindings);",
            catchBlock);
        int restorePrevious = install.indexOf("failure = registerTextureBindings(failure, previousBindings);",
            rollbackReplacement);
        int suppressCleanup = install.indexOf("addSuppressedCleanupFailure(exception, failure);",
            restorePrevious);
        int rethrow = install.indexOf("throw exception;", suppressCleanup);
        int replaceOwnedBindings = install.indexOf("registeredRenderTargetBindings.clear();", rethrow);
        int rememberOwnedBindings = install.indexOf("registeredRenderTargetBindings.putAll(replacementBindings);",
            replaceOwnedBindings);
        int retirePrevious = install.indexOf("return unregisterTextureBindings(null, previousBindings);",
            rememberOwnedBindings);

        assertTrue("Replacement aliases must install before owned handles are swapped",
            previousBindings >= 0 && installedBindings > previousBindings
                && registerReplacement > installedBindings && rememberReplacement > registerReplacement);
        assertTrue("Partial alias installs must roll back replacement aliases and restore previous aliases",
            catchBlock > rememberReplacement && rollbackReplacement > catchBlock
                && restorePrevious > rollbackReplacement && suppressCleanup > restorePrevious
                && rethrow > suppressCleanup);
        assertTrue("The pipeline must remember replacement aliases before returning previous identity cleanup failures",
            replaceOwnedBindings > rethrow && rememberOwnedBindings > replaceOwnedBindings
                && retirePrevious > rememberOwnedBindings);

        assertTrue("Destroy-time alias cleanup must still unregister by owned identity",
            unregister.contains("unregisterTextureBindings(null, registeredRenderTargetBindings)"));
        int unregisterRethrow = unregister.indexOf("rethrowCleanupFailure(failure);");
        int unregisterFinally = unregister.indexOf("} finally {", unregisterRethrow);
        int unregisterClear = unregister.indexOf("registeredRenderTargetBindings.clear();", unregisterFinally);
        assertTrue("Destroy-time alias cleanup must clear owned handles even when cleanup reports a failure",
            unregisterRethrow >= 0 && unregisterFinally > unregisterRethrow && unregisterClear > unregisterFinally);
    }

    @Test
    public void clearPassFramebufferDestroyHelperIsNullSafeAndReleasesOwnedFramebuffers() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String helper = methodBody(source, "private Throwable destroyClearPassFramebuffers");

        int nullGuard = helper.indexOf("if (clearPasses == null || renderTargets == null)");
        int returnStatement = helper.indexOf("return failure;", nullGuard);
        int loop = helper.indexOf("for (ClearPass clearPass : clearPasses)", returnStatement);
        int clearPassGuard = helper.indexOf("if (clearPass != null)", loop);
        int destroy = helper.indexOf("runCleanup(failure, () -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));",
            clearPassGuard);
        int returnsFailure = helper.indexOf("return failure;", destroy);

        assertTrue(nullGuard >= 0);
        assertTrue(returnStatement > nullGuard);
        assertTrue(loop > returnStatement);
        assertTrue(clearPassGuard > loop);
        assertTrue(destroy > clearPassGuard);
        assertTrue(returnsFailure > destroy);
    }

    @Test
    public void clearPassCreatorDestroysPartialFramebuffersWhenCreationFails() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ClearPassCreator.java");
        String createClearPasses = methodBody(source, "public static List<ClearPass> createClearPasses");
        String addClearPass = methodBody(source, "private static void addRenderTargetClearPass");
        String cleanup = methodBody(source, "private static Throwable destroyClearPassFramebuffers");

        int list = createClearPasses.indexOf("List<ClearPass> clearPasses = new ArrayList<>();");
        int tryBlock = createClearPasses.indexOf("try {", list);
        int createAlt = createClearPasses.indexOf(
            "addRenderTargetClearPass(clearPasses, renderTargets, key, true, clearBuffers);",
            tryBlock);
        int createMain = createClearPasses.indexOf(
            "addRenderTargetClearPass(clearPasses, renderTargets, key, false, clearBuffers);",
            createAlt);
        int returnPasses = createClearPasses.indexOf("return clearPasses;", createMain);
        int catchBlock = createClearPasses.indexOf("catch (RuntimeException | Error exception)", returnPasses);
        int cleanupCall = createClearPasses.indexOf(
            "destroyClearPassFramebuffers(null, renderTargets, clearPasses)", catchBlock);
        int suppressCleanup = createClearPasses.indexOf("addSuppressedCleanupFailure(exception,", catchBlock);
        int rethrow = createClearPasses.indexOf("throw exception;", suppressCleanup);

        int loop = cleanup.indexOf("for (ClearPass clearPass : clearPasses)");
        int nullGuard = cleanup.indexOf("if (clearPass != null)", loop);
        int destroy = cleanup.indexOf(
            "failure = runCleanup(failure, () -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));",
            nullGuard);
        int returnsFailure = cleanup.indexOf("return failure;", destroy);
        int pendingFramebuffer = addClearPass.indexOf(
            "GlFramebuffer pendingFramebuffer = renderTargets.createClearFramebuffer(alt, clearBuffers);");
        int addPass = addClearPass.indexOf("clearPasses.add(new ClearPass(", pendingFramebuffer);
        int publish = addClearPass.indexOf("pendingFramebuffer = null;", addPass);
        int helperCatch = addClearPass.indexOf("catch (RuntimeException | Error exception)", publish);
        int pendingGuard = addClearPass.indexOf("if (pendingFramebuffer != null)", helperCatch);
        int destroyPending = addClearPass.indexOf("renderTargets.destroyFramebuffer(framebuffer)", pendingGuard);

        assertTrue("Clear-pass creation must accumulate created passes in a local cleanup list", list >= 0);
        assertTrue("Clear-pass framebuffer creation must be guarded", tryBlock > list);
        assertTrue(createAlt > tryBlock);
        assertTrue(createMain > createAlt);
        assertTrue("The factory must only return after all clear passes are created",
            returnPasses > createMain);
        assertTrue("Partial clear-pass framebuffers must be destroyed without masking the setup failure",
            catchBlock > returnPasses && cleanupCall > catchBlock && suppressCleanup > catchBlock
                && rethrow > suppressCleanup);
        assertTrue("The cleanup helper must release every created clear-pass framebuffer through RenderTargets ownership",
            loop >= 0 && nullGuard > loop && destroy > nullGuard && returnsFailure > destroy);
        assertTrue("The factory must destroy an unpublished framebuffer if ClearPass publication fails",
            pendingFramebuffer >= 0 && addPass > pendingFramebuffer && publish > addPass
                && helperCatch > publish && pendingGuard > helperCatch && destroyPending > pendingGuard);
        assertTrue(source.contains("private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure)"));
    }

    @Test
    public void constructorInstallsShaderPackRenderLayerOverrides() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);
        String constructor = methodBody(source,
            "public ShaderWorldRenderingPipeline(ShaderPack pack, ProgramSet programSet, ShaderProperties shaderProperties)");

        int blockStateIds = constructor.indexOf("Object2IntMap<IBlockState> blockStateIds = pack.getBlockStateIdMap();");
        int renderLayerOverrides = constructor.indexOf("Map<Block, BlockRenderLayer> renderLayerOverrides =\n"
            + "            BlockMaterialMapping.createRenderLayerMap(pack.getIdMap().getBlockRenderTypeMap());");
        int entityIds = constructor.indexOf("Object2IntFunction<net.oculus.shaderpack.materialmap.NamespacedId> entityIds =\n"
            + "            pack.getIdMap().getEntityIdMap();");
        int framebufferManager = constructor.indexOf("this.framebufferManager = new FramebufferManager(this.directives);");
        int setBlockStateIds = constructor.indexOf("BlockRenderingSettings.INSTANCE.setBlockStateIds(blockStateIds);");
        int setRenderLayerOverrides = constructor.indexOf("BlockRenderingSettings.INSTANCE.setRenderLayerOverrides(renderLayerOverrides);");
        int setEntityIds = constructor.indexOf("BlockRenderingSettings.INSTANCE.setEntityIds(entityIds);");

        assertTrue(blockStateIds >= 0);
        assertTrue(renderLayerOverrides > blockStateIds);
        assertTrue(entityIds > renderLayerOverrides);
        assertTrue("Derived terrain state should be built before resource-owning managers", framebufferManager > entityIds);
        assertTrue(setBlockStateIds > framebufferManager);
        assertTrue(setRenderLayerOverrides > setBlockStateIds);
        assertTrue(setEntityIds > setRenderLayerOverrides);
    }

    @Test
    public void destroyUnregistersOwnedRenderTargetSamplerBindingsForReloads() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");

        int destroy = source.indexOf("public void destroy()");
        int destroyPostProcessors = source.indexOf("private void destroyPostProcessors()", destroy);
        String body = source.substring(destroy, destroyPostProcessors);
        String destroyPostBody = methodBody(source, "private void destroyPostProcessors()");
        String createBindingsBody = methodBody(source, "private Map<String, TextureBinding> createRenderTargetBindings");
        String installBindingsBody = methodBody(source, "private Throwable installRenderTargetBindings");
        String unregisterBody = methodBody(source, "private void unregisterRenderTargetBindings()");
        String unregisterHelperBody = methodBody(source, "private static Throwable unregisterTextureBindings");

        int customImages = body.indexOf("failure = runCleanup(failure, customImageManager::destroy);");
        int customTextures = body.indexOf("failure = runCleanup(failure, customTextureManager::destroy);");
        int framebuffer = body.indexOf("failure = runCleanup(failure, framebufferManager::destroy);");
        int unregisterBindings = destroyPostBody.indexOf("failure = runCleanup(failure, this::unregisterRenderTargetBindings);");
        int destroyTargets = destroyPostBody.indexOf("failure = runCleanup(failure, renderTargets::destroy);", unregisterBindings);
        int missingTargetGuard = createBindingsBody.indexOf("if (target == null)");
        int missingTargetThrow = createBindingsBody.indexOf(
            "throw new IllegalStateException(\"Render target colortex\" + index + \" is not configured\");",
            missingTargetGuard);
        int createCurrentBinding = createBindingsBody.indexOf("TextureBinding currentBinding", missingTargetThrow);
        int rememberColor = createBindingsBody.indexOf(
            "bindings.put(\"colortex\" + index, currentBinding);", createCurrentBinding);
        int previousBindings = installBindingsBody.indexOf(
            "Map<String, TextureBinding> previousBindings = new HashMap<>(registeredRenderTargetBindings);");
        int registerReplacement = installBindingsBody.indexOf(
            "TextureBindingRegistry.register(entry.getKey(), entry.getValue());", previousBindings);
        int rememberReplacement = installBindingsBody.indexOf(
            "installedBindings.put(entry.getKey(), entry.getValue());", registerReplacement);
        int restorePrevious = installBindingsBody.indexOf(
            "failure = registerTextureBindings(failure, previousBindings);", rememberReplacement);
        int replaceOwnedBindings = installBindingsBody.indexOf("registeredRenderTargetBindings.clear();",
            restorePrevious);
        int retirePrevious = installBindingsBody.indexOf(
            "return unregisterTextureBindings(null, previousBindings);", replaceOwnedBindings);
        int identityUnregister = unregisterHelperBody.indexOf(
            "TextureBindingRegistry.unregister(entry.getKey(), entry.getValue())");
        int rethrowUnregister = unregisterBody.indexOf("rethrowCleanupFailure(failure);");
        int finallyUnregister = unregisterBody.indexOf("} finally {", rethrowUnregister);
        int clearOwnedBindings = unregisterBody.indexOf("registeredRenderTargetBindings.clear();", finallyUnregister);

        assertTrue(destroy >= 0);
        assertTrue("The pipeline must keep identity handles for render-target sampler aliases",
            source.contains("private final Map<String, TextureBinding> registeredRenderTargetBindings = new HashMap<>();"));
        assertTrue("Render-target aliases must unregister before deleting render-target textures",
            unregisterBindings >= 0 && destroyTargets > unregisterBindings);
        assertTrue("Reload/rebuild registration must install replacement aliases before reporting old-identity cleanup",
            previousBindings >= 0 && registerReplacement > previousBindings
                && rememberReplacement > registerReplacement && replaceOwnedBindings > restorePrevious
                && retirePrevious > replaceOwnedBindings);
        assertTrue("Configured render-target aliases must fail clearly if allocation left a missing target",
            missingTargetGuard >= 0 && missingTargetThrow > missingTargetGuard);
        assertFalse("Configured render-target aliases must not silently skip missing targets",
            createBindingsBody.substring(missingTargetGuard, createCurrentBinding).contains("continue;"));
        assertTrue("Configured render-target aliases must be staged before global registry mutation",
            rememberColor > createCurrentBinding && registerReplacement >= 0);
        assertTrue("Render-target alias teardown must preserve newer bindings installed for the same sampler name",
            identityUnregister >= 0);
        assertTrue("Render-target alias teardown must clear owned identity handles even when cleanup fails",
            rethrowUnregister >= 0 && finallyUnregister > rethrowUnregister
                && clearOwnedBindings > finallyUnregister);
        assertTrue("Custom images should still release their owned sampler aliases during destroy",
            customImages >= 0);
        assertTrue("Custom textures should still release their owned noise aliases during destroy",
            customTextures >= 0);
        assertTrue("Framebuffer resources should still be destroyed after owned sampler cleanup paths run",
            framebuffer >= 0);
        assertFalse("World pipeline destroy must not clear unrelated global sampler aliases",
            source.contains("TextureBindingRegistry.clear();"));
        assertFalse("Render-target binding creation must not mutate the global registry",
            createBindingsBody.contains("TextureBindingRegistry.register"));
    }

    @Test
    public void destroyRestoresProgramStateAndMainFramebufferBeforeDeletingOwnedFramebuffers() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int destroy = source.indexOf("public void destroy()");
        int destroyPostProcessors = source.indexOf("private void destroyPostProcessors()", destroy);
        String body = source.substring(destroy, destroyPostProcessors);
        String destroyOverrideRestore = methodBody(source, "private void restoreRenderStateOverridesForDestroy()");

        int failureLocal = body.indexOf("Throwable failure = null;");
        int explicitOverrideRestore = body.indexOf(
            "failure = runCleanup(failure, this::restoreRenderStateOverridesForDestroy);", failureLocal);
        int unbindProgram = body.indexOf("failure = runCleanup(failure, this::unbindProgram);",
            explicitOverrideRestore);
        int unbindFramebuffers = body.indexOf(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::unbindCustomFramebuffersForDestroy);",
            unbindProgram);
        int bindMain = body.indexOf(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);",
            unbindFramebuffers);
        int shaderLoaderDestroy = body.indexOf("shaderLoader.destroy();", bindMain);
        int gbufferDestroy = body.indexOf("failure = runCleanup(failure, this::destroyGbufferFramebuffers);",
            shaderLoaderDestroy);
        int postDestroy = body.indexOf("failure = runCleanup(failure, this::destroyPostProcessors);", gbufferDestroy);
        int shadowDestroy = body.indexOf("failure = runCleanup(failure, this::destroyShadowRenderer);", postDestroy);
        int resetLifecycle = body.indexOf("resetFrameLifecycleAfterDestroy();", shadowDestroy);
        int rethrow = body.indexOf("rethrowCleanupFailure(failure);", resetLifecycle);
        int unbindHelper = source.indexOf("private static void unbindCustomFramebuffersForDestroy()");
        int restoreBindings = source.indexOf("OculusRenderSystem.restoreFramebufferBindings(0, 0, 0);", unbindHelper);
        int destroyBlendRestore = destroyOverrideRestore.indexOf("BlendModeOverride.restore();");
        int destroyAlphaRestore = destroyOverrideRestore.indexOf("AlphaTestOverride.restore();", destroyBlendRestore);

        assertTrue(destroy >= 0);
        assertTrue("Destroy must restore blend/alpha overrides in the same leading order as the reference destroy",
            explicitOverrideRestore > failureLocal && destroyBlendRestore >= 0
                && destroyAlphaRestore > destroyBlendRestore);
        assertTrue("Destroy must unbind any active shader program after explicit destroy-time state restoration",
            unbindProgram > explicitOverrideRestore);
        assertTrue("Destroy must explicitly release custom read/draw framebuffer bindings like the reference pipeline",
            unbindFramebuffers > unbindProgram);
        assertTrue("Destroy must bind the Minecraft main framebuffer before deleting owned FBOs",
            bindMain > unbindProgram);
        assertTrue("Destroy framebuffer cleanup must reset read, draw, and combined framebuffer bindings",
            restoreBindings > unbindHelper);
        assertTrue("Compiled programs should only be destroyed after active program state is released",
            shaderLoaderDestroy > bindMain);
        assertTrue("Cached gbuffer framebuffers must be destroyed while their RenderTargets owner is still live",
            gbufferDestroy > shaderLoaderDestroy);
        assertTrue("Postprocess/final resources and main render targets must be destroyed before shadow targets like 1.16.5",
            postDestroy > gbufferDestroy && shadowDestroy > postDestroy);
        assertTrue("Shadow framebuffers must not be deleted while a custom framebuffer is still bound",
            shadowDestroy > bindMain);
        assertTrue("Pipeline destroy must reset lifecycle flags even when cleanup reports a failure",
            resetLifecycle > shadowDestroy);
        assertTrue("Pipeline destroy must rethrow aggregated cleanup failures only after all owned cleanup paths run",
            rethrow > resetLifecycle);
    }

    @Test
    public void destroyResetsFrameLifecycleBeforeRethrowingCleanupFailures() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String destroy = methodBody(source, "public void destroy()");
        String reset = methodBody(source, "private void resetFrameLifecycleAfterDestroy");

        int fallbackDestroy = destroy.indexOf("failure = runCleanup(failure, FallbackTextures::destroy);");
        int resetCall = destroy.indexOf("resetFrameLifecycleAfterDestroy();", fallbackDestroy);
        int rethrow = destroy.indexOf("rethrowCleanupFailure(failure);", resetCall);
        int log = destroy.indexOf("LOGGER.info(\"Destroyed shader pipeline for {}\", pack.getName());", rethrow);
        int finalizeReset = reset.indexOf("resetFrameLifecycleAfterFinalize();");
        int framebufferReset = reset.indexOf("framebuffersInitialized = false;", finalizeReset);
        int shaderReset = reset.indexOf("shadersCompiled = false;", framebufferReset);
        int setupReset = reset.indexOf("setupLogged = false;", shaderReset);
        int shadowInitReset = reset.indexOf("shadowRendererInitialized = false;", setupReset);
        int uniformReset = reset.indexOf("frameRuntimeUniformsPrepared = false;", shadowInitReset);
        int normalReset = reset.indexOf("currentNormalTexture = 0;", uniformReset);
        int specularReset = reset.indexOf("currentSpecularTexture = 0;", normalReset);

        assertTrue("Destroy should reset lifecycle state after every owned cleanup path has been attempted",
            fallbackDestroy >= 0 && resetCall > fallbackDestroy);
        assertTrue("Destroy should reset lifecycle state before rethrowing aggregated cleanup failures",
            rethrow > resetCall && log > rethrow);
        assertTrue("Destroy reset should reuse the final-frame cleanup for world/fullscreen/shadow state",
            finalizeReset >= 0);
        assertTrue("Destroy reset must clear resource/setup flags in addition to final-frame state",
            framebufferReset > finalizeReset && shaderReset > framebufferReset && setupReset > shaderReset);
        assertTrue("Destroy reset must clear shadow, frame-uniform, and PBR texture state",
            shadowInitReset > setupReset && uniformReset > shadowInitReset
                && normalReset > uniformReset && specularReset > normalReset);
    }

    @Test
    public void cachedGbufferFramebufferDestroyUnregistersRenderTargetOwnedFramebuffers() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String destroyCaches = methodBody(source, "private void destroyGbufferFramebuffers()");
        String destroyOne = methodBody(source, "private Throwable destroyGbufferFramebuffer");
        String destroyWithOwner = methodBody(source, "private static Throwable destroyGbufferFramebuffer");

        int beforeLoop = destroyCaches.indexOf("for (Framebuffer framebuffer : gbufferFramebuffersBeforeTranslucent.values())");
        int beforeDestroy = destroyCaches.indexOf("failure = destroyGbufferFramebuffer(failure, framebuffer);", beforeLoop);
        int afterLoop = destroyCaches.indexOf("for (Framebuffer framebuffer : gbufferFramebuffersAfterTranslucent.values())",
            beforeDestroy);
        int afterDestroy = destroyCaches.indexOf("failure = destroyGbufferFramebuffer(failure, framebuffer);", afterLoop);
        int rethrowCleanup = destroyCaches.indexOf("rethrowCleanupFailure(failure);", afterDestroy);
        int finallyCleanup = destroyCaches.indexOf("} finally {", rethrowCleanup);
        int beforeClear = destroyCaches.indexOf("gbufferFramebuffersBeforeTranslucent.clear();", finallyCleanup);
        int afterClear = destroyCaches.indexOf("gbufferFramebuffersAfterTranslucent.clear();", beforeClear);

        assertTrue(beforeLoop >= 0);
        assertTrue(beforeDestroy > beforeLoop);
        assertTrue(afterLoop > beforeDestroy);
        assertTrue(afterDestroy > afterLoop);
        assertTrue(rethrowCleanup > afterDestroy);
        assertTrue("Cached gbuffer maps must clear stale framebuffer handles even when cleanup fails",
            finallyCleanup > rethrowCleanup && beforeClear > finallyCleanup && afterClear > beforeClear);
        assertFalse("Cached gbuffer framebuffers are owned by RenderTargets and must not bypass that owner",
            destroyCaches.contains("framebuffer.destroy();"));

        assertTrue("The current-cache helper must pass the current RenderTargets owner explicitly",
            destroyOne.contains("return destroyGbufferFramebuffer(failure, renderTargets, framebuffer);"));

        int nullGuard = destroyWithOwner.indexOf("if (framebuffer == null)");
        int ownerGuard = destroyWithOwner.indexOf("if (owner != null)", nullGuard);
        int ownerDestroy = destroyWithOwner.indexOf(
            "Throwable ownerFailure = runCleanup(null, () -> owner.destroyFramebuffer(framebuffer.getHandle()));",
            ownerGuard);
        int ownerFailureGuard = destroyWithOwner.indexOf("if (ownerFailure != null)", ownerDestroy);
        int ownerFailureReturn = destroyWithOwner.indexOf("return addCleanupFailure(failure, ownerFailure);",
            ownerFailureGuard);
        int wrapperDestroy = destroyWithOwner.indexOf("runCleanup(failure, framebuffer::destroy);", ownerFailureReturn);
        int returnsFailure = destroyWithOwner.indexOf("return failure;", wrapperDestroy);

        assertTrue(nullGuard >= 0);
        assertTrue(ownerGuard > nullGuard);
        assertTrue("Destroying a cached gbuffer framebuffer must remove it from RenderTargets ownership first",
            ownerDestroy > ownerGuard);
        assertTrue("Owner cleanup failures must return before invalidating the lightweight wrapper",
            ownerFailureGuard > ownerDestroy && ownerFailureReturn > ownerFailureGuard);
        assertTrue("The wrapper should only be marked destroyed after owner teardown succeeds",
            wrapperDestroy > ownerFailureReturn);
        assertTrue(returnsFailure > wrapperDestroy);
    }

    @Test
    public void cachedGbufferFramebufferCreationCleansUnpublishedFramebufferOnCacheFailure() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String getOrCreate = methodBody(source, "private Framebuffer getOrCreateGbufferFramebuffer");

        int pendingHandleLocal = getOrCreate.indexOf("GlFramebuffer pendingHandle = null;");
        int pendingFramebufferLocal = getOrCreate.indexOf("Framebuffer pendingFramebuffer = null;",
            pendingHandleLocal);
        int tryBlock = getOrCreate.indexOf("try {", pendingFramebufferLocal);
        int createHandle = getOrCreate.indexOf(
            "pendingHandle = renderTargets.createGbufferFramebuffer(flippedBuffers, framebufferDrawBuffers);",
            tryBlock);
        int createWrapper = getOrCreate.indexOf(
            "pendingFramebuffer = new Framebuffer(pendingHandle, framebufferDrawBuffers);", createHandle);
        int clearHandle = getOrCreate.indexOf("pendingHandle = null;", createWrapper);
        int cachePut = getOrCreate.indexOf("cache.put(key, pendingFramebuffer);", clearHandle);
        int publish = getOrCreate.indexOf("framebuffer = pendingFramebuffer;", cachePut);
        int clearPending = getOrCreate.indexOf("pendingFramebuffer = null;", publish);
        int returnFramebuffer = getOrCreate.indexOf("return framebuffer;", clearPending);
        int catchBlock = getOrCreate.indexOf("catch (RuntimeException | Error exception)", returnFramebuffer);
        int failureLocal = getOrCreate.indexOf("Throwable failure = null;", catchBlock);
        int wrapperGuard = getOrCreate.indexOf("if (pendingFramebuffer != null)", failureLocal);
        int captureWrapper = getOrCreate.indexOf("final Framebuffer capturedFramebuffer = pendingFramebuffer;",
            wrapperGuard);
        int destroyWrapper = getOrCreate.indexOf(
            "failure = destroyGbufferFramebuffer(failure, capturedFramebuffer);", captureWrapper);
        int removeCache = getOrCreate.indexOf(
            "failure = runCleanup(failure, () -> cache.remove(key, capturedFramebuffer));", destroyWrapper);
        int handleGuard = getOrCreate.indexOf("} else if (pendingHandle != null)", removeCache);
        int captureHandle = getOrCreate.indexOf("final GlFramebuffer capturedHandle = pendingHandle;", handleGuard);
        int destroyHandle = getOrCreate.indexOf(
            "failure = runCleanup(failure, () -> renderTargets.destroyFramebuffer(capturedHandle));",
            captureHandle);
        int suppress = getOrCreate.indexOf("addSuppressedCleanupFailure(exception, failure);", destroyHandle);
        int rethrow = getOrCreate.indexOf("throw exception;", suppress);

        assertTrue("Gbuffer cache creation must track both raw and wrapped unpublished framebuffers",
            pendingHandleLocal >= 0 && pendingFramebufferLocal > pendingHandleLocal
                && tryBlock > pendingFramebufferLocal);
        assertTrue("Gbuffer cache creation must not publish until the render-target-owned handle is wrapped",
            createHandle > tryBlock && createWrapper > createHandle && clearHandle > createWrapper);
        assertTrue("Gbuffer cache ownership must begin only after cache publication succeeds",
            cachePut > clearHandle && publish > cachePut && clearPending > publish
                && returnFramebuffer > clearPending);
        assertTrue("Failed cache publication must destroy a wrapped framebuffer through RenderTargets ownership",
            catchBlock > returnFramebuffer && failureLocal > catchBlock && wrapperGuard > failureLocal
                && captureWrapper > wrapperGuard && destroyWrapper > captureWrapper && removeCache > destroyWrapper);
        assertTrue("Failed wrapper construction must destroy the raw render-target-owned framebuffer handle",
            handleGuard > removeCache && captureHandle > handleGuard && destroyHandle > captureHandle);
        assertTrue("Cache publication cleanup failures must suppress onto the original failure",
            suppress > destroyHandle && rethrow > suppress);
        assertTrue("The pipeline must import the raw framebuffer type for unpublished-handle cleanup",
            source.contains("import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;"));
    }

    @Test
    public void pbrTextureBindingIsScopedToWorldRendering() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("private boolean isRenderingWorld;"));
        assertTrue(source.contains("isRenderingWorld = true;"));
        assertTrue(source.contains("isRenderingWorld = false;"));
        assertTrue(source.contains("if (isRenderingWorld && PBRTextureManager.INSTANCE.hasPbrSampler())"));
        assertFalse("Binding texture 0 must still reset PBR samplers to their default holder like the 1.16.5 path",
            source.contains("id > 0 && isRenderingWorld && PBRTextureManager.INSTANCE.hasPbrSampler()"));
    }

    @Test
    public void pbrTextureBindingResetUsesDefaultHolderBeforePublishingSamplerUpdate() throws Exception {
        String interfaceSource = read("src/main/java/net/oculus/pipeline/WorldRenderingPipeline.java");
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String resetBody = methodBody(source, "public void resetPbrTextureBindings()");
        String updateBody = methodBody(source, "private void updatePbrTextureBindings(PBRTextureHolder pbrHolder)");

        int samplerGuard = resetBody.indexOf("if (!PBRTextureManager.INSTANCE.hasPbrSampler())");
        int normalReset = resetBody.indexOf("currentNormalTexture = 0;", samplerGuard);
        int specularReset = resetBody.indexOf("currentSpecularTexture = 0;", normalReset);
        int noSamplerReturn = resetBody.indexOf("return;", specularReset);
        int defaultHolder = resetBody.indexOf("PBRTextureManager.INSTANCE.getOrLoadHolder(0)", noSamplerReturn);
        int update = resetBody.lastIndexOf("updatePbrTextureBindings(", defaultHolder);
        int notify = resetBody.indexOf("PBRTextureManager.notifyPBRTexturesChanged();", defaultHolder);
        int helperNormal = updateBody.indexOf("currentNormalTexture = pbrHolder.getNormalTexture().getGlTextureId();");
        int helperSpecular = updateBody.indexOf("currentSpecularTexture = pbrHolder.getSpecularTexture().getGlTextureId();",
            helperNormal);
        int textureFormat = updateBody.indexOf("TextureFormat textureFormat = TextureFormatLoader.getFormat();",
            helperSpecular);

        assertTrue(interfaceSource.contains("default void resetPbrTextureBindings()"));
        assertTrue("Reload-time reset without active PBR samplers should clear stale ids without publishing",
            samplerGuard >= 0 && normalReset > samplerGuard && specularReset > normalReset
                && noSamplerReturn > specularReset);
        assertTrue("Reload-time PBR reset must resolve the default holder before rebinding dynamic samplers",
            update > noSamplerReturn && defaultHolder > update && notify > defaultHolder);
        assertTrue("Default-holder reset must use the same current texture source path as base texture binds",
            helperNormal >= 0 && helperSpecular > helperNormal && textureFormat > helperSpecular);
    }

    @Test
    public void gbufferProgramResolutionUsesAvailabilitySpecificShaderVariants() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("shaderLoader.getProgram(source.getName(), availability)"));
        assertTrue(source.contains("throw new ProgramLoadException(\"Failed to create pass for \" + source.getName()"));
        assertTrue(source.contains("\" specialized to input availability \" + availability"));
    }

    @Test
    public void legacyImmediatePhasesForceTexturedLitInputsForDisplayListModelDraws() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String sync = methodBody(source, "public void syncProgram()");
        String helper = methodBody(source,
            "private static InputAvailability normalizeLegacyImmediateAvailability");

        int rawInputs = sync.indexOf("InputAvailability rawAvailability =");
        int normalize = sync.indexOf(
            "InputAvailability availability = normalizeLegacyImmediateAvailability(condition, rawAvailability);",
            rawInputs);
        int resolve = sync.indexOf("ResolvedProgram resolved = resolveProgram(condition, availability);",
            normalize);

        assertTrue("Legacy 1.12 display-list phases must normalize inputs before resolving shader variants",
            rawInputs >= 0 && normalize > rawInputs && resolve > normalize);
        assertTrue(helper.contains("condition == RenderCondition.ENTITIES"));
        assertTrue(helper.contains("condition == RenderCondition.ENTITIES_TRANSLUCENT"));
        assertTrue(helper.contains("condition == RenderCondition.BLOCK_ENTITIES"));
        assertTrue(helper.contains("condition == RenderCondition.HAND_OPAQUE"));
        assertTrue(helper.contains("condition == RenderCondition.HAND_TRANSLUCENT"));
        assertTrue(helper.contains("return new InputAvailability(true, true, safeAvailability.overlay);"));
    }

    @Test
    public void gbufferProgramBindFailuresPropagateInsteadOfBeingSilentlySkipped() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int bindProgram = source.indexOf("private void bindProgram(ResolvedProgram resolved)");
        int resolveProgram = source.indexOf("private ResolvedProgram resolveProgram", bindProgram);
        String bindProgramBody = source.substring(bindProgram, resolveProgram);

        assertTrue(bindProgram >= 0);
        assertTrue(bindProgramBody.contains("program.use();"));
        assertFalse(bindProgramBody.contains("catch (RuntimeException ex)"));
        assertFalse(bindProgramBody.contains("Failed to bind program"));
    }

    @Test
    public void beginHandDoesNotCopyMainDepthDuringShadowRendering() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int beginHand = source.indexOf("public void beginHand()");
        int beginTranslucents = source.indexOf("public void beginTranslucents()", beginHand);
        String body = source.substring(beginHand, beginTranslucents);

        int shadowGuard = body.indexOf("if (isRenderingShadow) {");
        int shadowReturn = body.indexOf("return;", shadowGuard);
        int copyPreHandDepth = body.indexOf("renderTargets.copyPreHandDepth();");

        assertTrue(beginHand >= 0);
        assertTrue("beginHand must skip during shadow rendering because 1.12 translucent layer hooks also fire there",
            shadowGuard >= 0 && shadowReturn > shadowGuard);
        assertTrue("Shadow guard must run before the main pre-hand depth snapshot copy",
            copyPreHandDepth > shadowReturn);
    }

    @Test
    public void gbufferFramebufferBindReusesFramebufferDrawBufferStateLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int bindMethod = source.indexOf("private void bindGbufferFramebuffer(ResolvedProgram resolved, boolean clear)");
        int resolveMethod = source.indexOf("private int[] resolveGbufferDrawBuffers", bindMethod);
        String bindBody = source.substring(bindMethod, resolveMethod);

        assertTrue(bindMethod >= 0);
        assertTrue(bindBody.contains("Framebuffer framebuffer = getOrCreateGbufferFramebuffer(isBeforeTranslucent, drawBuffers);"));
        assertTrue(bindBody.contains("bindShaderFramebuffer(framebuffer);"));
        assertTrue(source.contains("private void bindShaderFramebuffer(Framebuffer framebuffer)"));
        assertTrue(source.contains("framebuffer.bind();"));
        assertTrue(source.contains("isMainBound = true;"));
        assertFalse(bindBody.contains("GL20.glDrawBuffers"));
        assertFalse(bindBody.contains("framebuffer.getHandle().noDrawBuffers();"));
        assertFalse(source.contains("private int[] convertToGLDrawBuffers"));
    }

    @Test
    public void gbufferDepthClearForcesAndRestoresDepthWriteMask() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int bindMethod = source.indexOf("private void bindGbufferFramebuffer(ResolvedProgram resolved, boolean clear)");
        int clearHelper = source.indexOf("private static void clearGbufferDepth()", bindMethod);
        int resolveMethod = source.indexOf("private int[] resolveGbufferDrawBuffers", clearHelper);
        String bindBody = source.substring(bindMethod, clearHelper);
        String clearBody = source.substring(clearHelper, resolveMethod);

        int clearGuard = bindBody.indexOf("if (clear)");
        int helperCall = bindBody.indexOf("clearGbufferDepth();", clearGuard);
        int markBound = bindBody.indexOf("gbufferBound = true;", helperCall);

        int saveDepthMask = clearBody.indexOf("boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);");
        int forceDepthMask = clearBody.indexOf("GlStateManager.depthMask(true);", saveDepthMask);
        int failureLocal = clearBody.indexOf("Throwable failure = null;", forceDepthMask);
        int tryBlock = clearBody.indexOf("try {", failureLocal);
        int clearDepth = clearBody.indexOf("GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);", tryBlock);
        int catchBlock = clearBody.indexOf("catch (RuntimeException | Error exception)", clearDepth);
        int recordFailure = clearBody.indexOf("failure = exception;", catchBlock);
        int rethrowPrimary = clearBody.indexOf("throw exception;", recordFailure);
        int finallyBlock = clearBody.indexOf("finally {", rethrowPrimary);
        int restoreDepthMask = clearBody.indexOf(
            "Throwable cleanupFailure = runCleanup(null, () -> GlStateManager.depthMask(previousDepthMask));",
            finallyBlock);
        int suppressFailure = clearBody.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);",
            restoreDepthMask);
        int rethrowCleanup = clearBody.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressFailure);

        assertTrue(bindMethod >= 0);
        assertTrue("Only the first frame g-buffer bind should clear depth",
            clearGuard >= 0 && helperCall > clearGuard && markBound > helperCall);
        assertTrue("The g-buffer depth clear must save the incoming depth write mask",
            saveDepthMask >= 0);
        assertTrue("The g-buffer depth clear must force writes before clearing",
            forceDepthMask > saveDepthMask && failureLocal > forceDepthMask && clearDepth > failureLocal);
        assertTrue("The g-buffer depth clear must preserve primary clear failures before cleanup",
            catchBlock > clearDepth && recordFailure > catchBlock && rethrowPrimary > recordFailure);
        assertTrue("The g-buffer depth clear must restore the caller's depth write mask",
            finallyBlock > rethrowPrimary && restoreDepthMask > finallyBlock);
        assertTrue("Depth-mask cleanup failures must suppress onto primary failures or rethrow alone",
            suppressFailure > restoreDepthMask && rethrowCleanup > suppressFailure);
        assertFalse("The g-buffer bind path must not leave raw depth clears outside the guarded helper",
            bindBody.contains("GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);"));
    }

    @Test
    public void gbufferNoProgramFallbackBindsDefaultGbufferFramebufferLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int bindMethod = source.indexOf("private void bindGbufferFramebuffer(ResolvedProgram resolved, boolean clear)");
        int initializeMethod = source.indexOf("private void ensureFramebufferManagerInitialized()", bindMethod);
        String bindBody = source.substring(bindMethod, initializeMethod);

        int unavailable = bindBody.indexOf("!shadersCompiled || shaderLoader == null");
        int bindMain = bindBody.indexOf("bindMainFramebufferAfterRenderTargetPreparation();", unavailable);
        int returnFallback = bindBody.indexOf("return;", bindMain);
        int drawBuffers = bindBody.indexOf("int[] drawBuffers = resolveGbufferDrawBuffers(resolved);", returnFallback);
        int framebuffer = bindBody.indexOf("Framebuffer framebuffer = getOrCreateGbufferFramebuffer(isBeforeTranslucent, drawBuffers);",
            drawBuffers);
        int bindFramebuffer = bindBody.indexOf("bindShaderFramebuffer(framebuffer);", framebuffer);
        int markBound = bindBody.indexOf("gbufferBound = true;", bindFramebuffer);

        assertTrue(bindMethod >= 0);
        assertTrue("The only main-framebuffer fallback should be unavailable shader infrastructure",
            bindMain > unavailable);
        assertTrue("A successfully compiled pack with no gbuffer source must still bind the default gbuffer framebuffer",
            drawBuffers > returnFallback);
        assertTrue(framebuffer > drawBuffers);
        assertTrue(bindFramebuffer > framebuffer);
        assertTrue(markBound > bindFramebuffer);
        assertFalse("A pack with no gbuffer programs must not skip the source-faithful default gbuffer pass",
            bindBody.contains("!shaderLoader.hasCompiledPrograms()"));
        assertFalse("No-program fallback must not bind raw framebuffer 0 while a Minecraft framebuffer may exist",
            bindBody.contains("OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);"));
    }

    @Test
    public void missingGbufferProgramSourceCreatesDefaultFramebufferPassLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int resolve = source.indexOf("private ResolvedProgram resolveProgram");
        int apply = source.indexOf("private void applyRenderStateOverrides", resolve);
        String resolveBody = source.substring(resolve, apply);

        int sourceLookup = resolveBody.indexOf("ProgramSource source = programFallbackResolver.resolveNullable(id);");
        int nullGuard = resolveBody.indexOf("if (source == null)", sourceLookup);
        int defaultResolved = resolveBody.indexOf("return new ResolvedProgram(id, null, null);", nullGuard);

        int bindProgram = source.indexOf("private void bindProgram(ResolvedProgram resolved)");
        int resolveProgram = source.indexOf("private ResolvedProgram resolveProgram", bindProgram);
        String bindBody = source.substring(bindProgram, resolveProgram);

        int nullProgram = bindBody.indexOf("if (program == null)");
        int bindDefault = bindBody.indexOf("bindWorldFramebufferForProgram(resolved, false);", nullProgram);
        int unbind = bindBody.indexOf("Program.unbind();", bindDefault);
        int restore = bindBody.indexOf("restoreRenderStateOverrides();", unbind);

        int sync = source.indexOf("public void syncProgram()");
        int availability = source.indexOf("InputAvailability availability =", sync);
        String syncGuard = source.substring(sync, availability);

        assertTrue(resolve >= 0);
        assertTrue(sourceLookup >= 0);
        assertTrue(nullGuard > sourceLookup);
        assertTrue(defaultResolved > nullGuard);
        assertTrue(bindProgram >= 0);
        assertTrue(nullProgram >= 0);
        assertTrue(bindDefault > nullProgram);
        assertTrue(unbind > bindDefault);
        assertTrue(restore > unbind);
        assertFalse("World program sync must still produce default framebuffer passes when no gbuffer programs compile",
            syncGuard.contains("!shaderLoader.hasCompiledPrograms()"));
    }

    @Test
    public void shadowProgramSyncBindsShadowFramebufferInsteadOfWorldGbufferFramebuffer() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");

        int bindProgram = source.indexOf("private void bindProgram(ResolvedProgram resolved)");
        int helperMethod = source.indexOf("private void bindWorldFramebufferForProgram", bindProgram);
        int resolveProgram = source.indexOf("private ResolvedProgram resolveProgram", helperMethod);
        String bindBody = source.substring(bindProgram, helperMethod);
        String helperBody = source.substring(helperMethod, resolveProgram);

        int nullProgram = bindBody.indexOf("if (program == null)");
        int nullHelper = bindBody.indexOf("bindWorldFramebufferForProgram(resolved, false);", nullProgram);
        int unbind = bindBody.indexOf("Program.unbind();", nullHelper);
        int sameProgram = bindBody.indexOf("if (activeGbufferProgram == program)", unbind);
        int sameHelper = bindBody.indexOf("bindWorldFramebufferForProgram(resolved, false);", sameProgram);
        int newProgramHelper = bindBody.indexOf("bindWorldFramebufferForProgram(resolved, false);",
            sameHelper + 1);
        int useProgram = bindBody.indexOf("program.use();", newProgramHelper);

        int shadowGuard = helperBody.indexOf("if (isRenderingShadow) {");
        int bindShadowFramebuffer = helperBody.indexOf(
            ".bindFramebufferForShadowPass();", shadowGuard);
        int shadowReturn = helperBody.indexOf("return;", bindShadowFramebuffer);
        int bindGbuffer = helperBody.indexOf("bindGbufferFramebuffer(resolved, clear);", shadowReturn);

        assertTrue(bindProgram >= 0);
        assertTrue("No-program shadow fallback must not rebind the regular gbuffer framebuffer",
            nullHelper > nullProgram && unbind > nullHelper);
        assertTrue("Cached shadow program sync must route through the shared framebuffer helper",
            sameHelper > sameProgram);
        assertTrue("New shadow program sync must bind the shadow framebuffer before program use",
            newProgramHelper > sameHelper && useProgram > newProgramHelper);
        assertTrue("The world-framebuffer bind helper must select the ShadowRenderer framebuffer during shadow rendering",
            shadowGuard >= 0 && bindShadowFramebuffer > shadowGuard && shadowReturn > bindShadowFramebuffer);
        assertTrue("The regular gbuffer framebuffer must stay on the non-shadow path",
            bindGbuffer > shadowReturn);
        assertFalse("Shadow-aware program sync must route framebuffer ownership through the helper",
            bindBody.contains("bindGbufferFramebuffer(resolved, false);"));
    }

    @Test
    public void deferredAndPrepareFullscreenPassesSetFullscreenGuardLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int beginTranslucents = source.indexOf("public void beginTranslucents()");
        int finalize = source.indexOf("public void finalizeLevelRendering()", beginTranslucents);
        String translucents = source.substring(beginTranslucents, finalize);

        int deferredTry = translucents.indexOf("try {");
        int unbindDeferred = translucents.indexOf("unbindProgram();", deferredTry);
        int deferredFullscreen = translucents.indexOf("isRenderingFullScreenPass = true;", unbindDeferred);
        int deferredRender = translucents.indexOf("deferredRenderer.renderAll();", deferredTry);
        int deferredFinally = translucents.indexOf("finally {", deferredRender);
        int deferredRestore = translucents.indexOf("failure = restoreAfterDeferredPass(failure);",
            deferredFinally);
        int deferredRethrow = translucents.indexOf("rethrowCleanupFailure(failure);", deferredRestore);
        String deferredRestoreBody = methodBody(source, "private Throwable restoreAfterDeferredPass");
        int callSharedRestore = deferredRestoreBody.indexOf("failure = restoreAfterPreparePass(failure);");
        int enableBlend = deferredRestoreBody.indexOf("runCleanup(failure, GlStateManager::enableBlend);",
            callSharedRestore);
        int enableAlpha = deferredRestoreBody.indexOf("runCleanup(failure, GlStateManager::enableAlpha);",
            enableBlend);

        assertTrue(beginTranslucents >= 0);
        assertTrue("Deferred unbind must be inside the guarded fullscreen cleanup boundary",
            deferredTry >= 0 && unbindDeferred > deferredTry);
        assertTrue(deferredFullscreen > unbindDeferred);
        assertTrue(deferredRender > deferredTry);
        assertTrue(deferredFinally > deferredRender);
        assertTrue(deferredRestore > deferredFinally);
        assertTrue(deferredRethrow > deferredRestore);
        assertTrue(callSharedRestore >= 0);
        assertTrue(enableBlend > callSharedRestore);
        assertTrue(enableAlpha > enableBlend);

        int runPrepare = source.indexOf("private void runPreparePass()");
        int runComposite = source.indexOf("private void runCompositePass()", runPrepare);
        String prepare = source.substring(runPrepare, runComposite);

        int prepareTry = prepare.indexOf("try {");
        int unbindPrepare = prepare.indexOf("unbindProgram();", prepareTry);
        int prepareFullscreen = prepare.indexOf("isRenderingFullScreenPass = true;", unbindPrepare);
        int prepareRender = prepare.indexOf("prepareRenderer.renderAll();", prepareTry);
        int prepareFinally = prepare.indexOf("finally {", prepareRender);
        int prepareRestore = prepare.indexOf("failure = restoreAfterPreparePass(failure);", prepareFinally);
        int prepareRethrow = prepare.indexOf("rethrowCleanupFailure(failure);", prepareRestore);
        int prepareSync = prepare.indexOf("syncProgram();", prepareRethrow);
        String prepareRestoreBody = methodBody(source, "private Throwable restoreAfterPreparePass");
        int prepareFullscreenFalse = prepareRestoreBody.indexOf("isRenderingFullScreenPass = false;");
        int prepareBindGbuffer = prepareRestoreBody.indexOf(
            "runCleanup(failure, () -> bindGbufferFramebuffer(false));", prepareFullscreenFalse);

        assertTrue(runPrepare >= 0);
        assertTrue("Prepare unbind must be inside the guarded fullscreen cleanup boundary",
            prepareTry >= 0 && unbindPrepare > prepareTry);
        assertTrue(prepareFullscreen > unbindPrepare);
        assertTrue(prepareRender > prepareTry);
        assertTrue(prepareFinally > prepareRender);
        assertTrue(prepareRestore > prepareFinally);
        assertTrue(prepareRethrow > prepareRestore);
        assertTrue(prepareFullscreenFalse >= 0);
        assertTrue(prepareBindGbuffer > prepareFullscreenFalse);
        assertTrue(prepareSync > prepareRethrow);
    }

    @Test
    public void beginTranslucentsDepthCopyIsInsideDeferredCleanupBoundary() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String beginTranslucents = methodBody(source, "public void beginTranslucents()");

        int resourceGuard = beginTranslucents.indexOf(
            "requirePipelineResource(deferredRenderer, \"deferred renderer\", \"beginTranslucents\");");
        int failureLocal = beginTranslucents.indexOf("Throwable failure = null;", resourceGuard);
        int tryBlock = beginTranslucents.indexOf("try {", failureLocal);
        int markAfterTry = beginTranslucents.indexOf("isBeforeTranslucent = false;", tryBlock);
        int copyDepth = beginTranslucents.indexOf("renderTargets.copyPreTranslucentDepth();", markAfterTry);
        int unbind = beginTranslucents.indexOf("unbindProgram();", copyDepth);
        int renderDeferred = beginTranslucents.indexOf("deferredRenderer.renderAll();", unbind);
        int catchBlock = beginTranslucents.indexOf("catch (RuntimeException | Error exception)", renderDeferred);
        int finallyBlock = beginTranslucents.indexOf("finally {", catchBlock);
        int cleanup = beginTranslucents.indexOf("failure = restoreAfterDeferredPass(failure);", finallyBlock);

        assertTrue("Deferred pass must establish its failure tracker before depth-copy work", failureLocal > resourceGuard);
        assertTrue("The pipeline must leave the pre-translucent read state before the depth copy like 1.16.5",
            tryBlock > failureLocal && markAfterTry > tryBlock && copyDepth > markAfterTry);
        assertTrue("Deferred raster work must still run after the read-state transition and depth copy",
            unbind > copyDepth && renderDeferred > unbind);
        assertTrue("Depth-copy, unbind, and render failures must all route through deferred cleanup",
            catchBlock > renderDeferred && finallyBlock > catchBlock && cleanup > finallyBlock);
    }

    @Test
    public void fullscreenPipelineBoundariesRestoreFrameStateFromFinally() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String beginTranslucents = methodBody(source, "public void beginTranslucents()");
        String finalizeLevel = methodBody(source, "public void finalizeLevelRendering()");
        String runPrepare = methodBody(source, "private void runPreparePass()");
        String deferredRestore = methodBody(source, "private Throwable restoreAfterDeferredPass");
        String prepareRestore = methodBody(source, "private Throwable restoreAfterPreparePass");
        String finalizeRestore = methodBody(source, "private Throwable restoreAfterFinalizeLevelRendering");
        String finalizeReset = methodBody(source, "private void resetFrameLifecycleAfterFinalize");

        int deferredTry = beginTranslucents.indexOf("try {");
        int deferredUnbind = beginTranslucents.indexOf("unbindProgram();", deferredTry);
        int deferredRender = beginTranslucents.indexOf("deferredRenderer.renderAll();", deferredUnbind);
        int deferredFinally = beginTranslucents.indexOf("finally {", deferredRender);
        int deferredCleanup = beginTranslucents.indexOf("failure = restoreAfterDeferredPass(failure);",
            deferredFinally);
        int deferredRethrow = beginTranslucents.indexOf("rethrowCleanupFailure(failure);", deferredCleanup);
        int deferredSync = beginTranslucents.indexOf("syncProgram();", deferredRethrow);
        int deferredSharedRestore = deferredRestore.indexOf("failure = restoreAfterPreparePass(failure);");
        int deferredEnableBlend = deferredRestore.indexOf(
            "failure = runCleanup(failure, GlStateManager::enableBlend);", deferredSharedRestore);
        int deferredEnableAlpha = deferredRestore.indexOf(
            "failure = runCleanup(failure, GlStateManager::enableAlpha);", deferredEnableBlend);

        assertTrue(deferredTry >= 0);
        assertTrue("Deferred program unbind failures must route through the same cleanup boundary",
            deferredUnbind > deferredTry);
        assertTrue(deferredRender > deferredUnbind);
        assertTrue("Deferred pass cleanup must run from finally so failures restore the gbuffer boundary",
            deferredFinally > deferredRender);
        assertTrue(deferredCleanup > deferredFinally);
        assertTrue(deferredRethrow > deferredCleanup);
        assertTrue(deferredSharedRestore >= 0);
        assertTrue(deferredEnableBlend > deferredSharedRestore);
        assertTrue(deferredEnableAlpha > deferredEnableBlend);
        assertTrue("Program resync should only run after the cleanup boundary completes",
            deferredSync > deferredRethrow);

        int prepareTry = runPrepare.indexOf("try {");
        int prepareUnbind = runPrepare.indexOf("unbindProgram();", prepareTry);
        int prepareRender = runPrepare.indexOf("prepareRenderer.renderAll();", prepareUnbind);
        int prepareFinally = runPrepare.indexOf("finally {", prepareRender);
        int prepareCleanup = runPrepare.indexOf("failure = restoreAfterPreparePass(failure);", prepareFinally);
        int prepareRethrow = runPrepare.indexOf("rethrowCleanupFailure(failure);", prepareCleanup);
        int prepareSync = runPrepare.indexOf("syncProgram();", prepareRethrow);
        int prepareFullscreenFalse = prepareRestore.indexOf("isRenderingFullScreenPass = false;");
        int prepareBindGbuffer = prepareRestore.indexOf(
            "failure = runCleanup(failure, () -> bindGbufferFramebuffer(false));", prepareFullscreenFalse);

        assertTrue(prepareTry >= 0);
        assertTrue("Prepare program unbind failures must route through the same cleanup boundary",
            prepareUnbind > prepareTry);
        assertTrue(prepareRender > prepareUnbind);
        assertTrue("Prepare pass cleanup must run from finally so failures restore the gbuffer boundary",
            prepareFinally > prepareRender);
        assertTrue(prepareCleanup > prepareFinally);
        assertTrue(prepareRethrow > prepareCleanup);
        assertTrue(prepareFullscreenFalse >= 0);
        assertTrue(prepareBindGbuffer > prepareFullscreenFalse);
        assertTrue(prepareSync > prepareRethrow);

        int finalTry = finalizeLevel.indexOf("try {");
        int finalUnbind = finalizeLevel.indexOf("unbindProgram();", finalTry);
        int runComposite = finalizeLevel.indexOf("runCompositePass();", finalUnbind);
        int finalFinally = finalizeLevel.indexOf("finally {", runComposite);
        int finalCleanup = finalizeLevel.indexOf("failure = restoreAfterFinalizeLevelRendering(failure);",
            finalFinally);
        int finalRethrow = finalizeLevel.indexOf("rethrowCleanupFailure(failure);", finalCleanup);
        int cleanupUnbind = finalizeRestore.indexOf("failure = runCleanup(failure, this::unbindProgram);");
        int resetCall = finalizeRestore.indexOf("resetFrameLifecycleAfterFinalize();", cleanupUnbind);
        int bindMain = finalizeRestore.indexOf(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::bindMainFramebufferAfterRenderTargetPreparation);",
            resetCall);
        int fixedFunctionRestore = finalizeRestore.indexOf(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::restorePostWorldFixedFunctionState);",
            bindMain);

        assertTrue(finalTry >= 0);
        assertTrue("Final program unbind failures must route through final frame cleanup",
            finalUnbind > finalTry);
        assertTrue(runComposite > finalUnbind);
        assertTrue("Finalize cleanup must run from finally so postprocess failures close the frame lifecycle",
            finalFinally > runComposite);
        assertTrue(finalCleanup > finalFinally && finalRethrow > finalCleanup);
        assertTrue(cleanupUnbind >= 0 && resetCall > cleanupUnbind);
        assertTrue("Finalize cleanup must restore the main framebuffer if final postprocess fails",
            bindMain > resetCall);
        assertTrue("Finalize cleanup must leave legacy GUI state usable after shader postprocess",
            fixedFunctionRestore > bindMain);
        assertTrue(finalizeReset.contains("prepared = false;"));
        assertTrue(finalizeReset.contains("isRenderingWorld = false;"));
        assertTrue(finalizeReset.contains("isRenderingFullScreenPass = false;"));
        assertTrue(finalizeReset.contains("gbufferBound = false;"));
        assertTrue(finalizeReset.contains("isMainBound = true;"));
        assertTrue(finalizeReset.contains("bindingShaderFramebuffer = false;"));
        assertTrue(finalizeReset.contains("isPostChain = false;"));
        assertTrue(finalizeReset.contains("isBeforeTranslucent = true;"));
        assertTrue(finalizeReset.contains("handDepthCapturedThisFrame = false;"));
        assertTrue(finalizeReset.contains("shadowRenderTargetsPreparedThisFrame = false;"));
        assertTrue(finalizeReset.contains("isRenderingShadow = false;"));
        assertTrue(finalizeReset.contains("sodiumTerrainRendering = false;"));
        assertTrue(finalizeReset.contains("frameRuntimeUniformsPrepared = false;"));
        assertTrue(finalizeReset.contains("phase = WorldRenderingPhase.NONE;"));
        assertTrue(finalizeReset.contains("overridePhase = null;"));
        assertTrue(finalizeReset.contains("activeGbufferProgram = null;"));
        assertTrue(finalizeReset.contains("activeGbufferProgramName = null;"));
    }

    @Test
    public void prepareAndShadowBoundariesRestoreGbufferWithoutExtraDepthClear() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int renderShadows = source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        int prepareMethod = source.indexOf("private void prepareShadowRenderTargets()", renderShadows);
        String renderShadowsBody = source.substring(renderShadows, prepareMethod);

        int shadowFinally = renderShadowsBody.indexOf("finally {");
        int restoreAfterShadow = renderShadowsBody.indexOf("failure = restoreAfterShadowRender(failure);",
            shadowFinally);
        int unsetShadow = renderShadowsBody.indexOf("isRenderingShadow = false;", restoreAfterShadow);
        String restoreAfterShadowBody = methodBody(source, "private Throwable restoreAfterShadowRender");
        int unbind = restoreAfterShadowBody.indexOf("runCleanup(failure, this::unbindProgram);");
        int bindWithoutClear = restoreAfterShadowBody.indexOf(
            "runCleanup(failure, () -> bindGbufferFramebuffer(false));", unbind);

        assertTrue(renderShadows >= 0);
        assertTrue("Post-shadow cleanup must unbind the shadow pass before leaving shadow mode like 1.16.5 beginPass(null)",
            restoreAfterShadow > shadowFinally && unsetShadow > restoreAfterShadow);
        assertTrue("Post-shadow cleanup must restore the gbuffer target without clearing world depth",
            bindWithoutClear > unbind);
        assertFalse("Post-shadow cleanup must not use the depth-clearing gbuffer bind",
            restoreAfterShadowBody.contains("bindGbufferFramebuffer();"));

        int runPrepare = source.indexOf("private void runPreparePass()");
        int runComposite = source.indexOf("private void runCompositePass()", runPrepare);
        String prepare = source.substring(runPrepare, runComposite);
        String prepareRestore = methodBody(source, "private Throwable restoreAfterPreparePass");

        int prepareFinally = prepare.indexOf("finally {");
        int prepareCleanup = prepare.indexOf("failure = restoreAfterPreparePass(failure);", prepareFinally);
        int guardFalse = prepareRestore.indexOf("isRenderingFullScreenPass = false;");
        int prepareBindWithoutClear = prepareRestore.indexOf(
            "bindGbufferFramebuffer(false)", guardFalse);

        assertTrue(runPrepare >= 0);
        assertTrue("Prepare pass cleanup must be routed through the shared cleanup aggregator",
            prepareCleanup > prepareFinally);
        assertTrue("Prepare pass cleanup must restore the gbuffer target without clearing world depth",
            prepareBindWithoutClear > guardFalse);
        assertFalse("Prepare pass cleanup must not use the depth-clearing gbuffer bind",
            prepare.contains("bindGbufferFramebuffer();"));
    }

    @Test
    public void shadowBoundaryAggregatesFramebufferAndViewportCleanupFailures() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String renderShadowsBody = methodBody(source,
            "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        String restoreAfterShadow = methodBody(source, "private Throwable restoreAfterShadowRender");

        int renderFinally = renderShadowsBody.indexOf("finally {");
        int restoreCall = renderShadowsBody.indexOf("failure = restoreAfterShadowRender(failure);", renderFinally);
        int unsetShadow = renderShadowsBody.indexOf("isRenderingShadow = false;", restoreCall);
        int rethrow = renderShadowsBody.indexOf("rethrowCleanupFailure(failure);", unsetShadow);

        int unbind = restoreAfterShadow.indexOf("failure = runCleanup(failure, this::unbindProgram);");
        int bindGbuffer = restoreAfterShadow.indexOf(
            "failure = runCleanup(failure, () -> bindGbufferFramebuffer(false));", unbind);
        int viewport = restoreAfterShadow.indexOf(
            "failure = runCleanup(failure, ShaderWorldRenderingPipeline::restoreMinecraftViewport);",
            bindGbuffer);
        int returnsFailure = restoreAfterShadow.indexOf("return failure;", viewport);
        String viewportRestore = methodBody(source, "private static void restoreMinecraftViewport()");

        assertTrue("Shadow render cleanup must call the shared restore helper from its finally path",
            renderFinally >= 0 && restoreCall > renderFinally);
        assertTrue("Shadow mode must reset after cleanup and before cleanup failures are rethrown",
            unsetShadow > restoreCall && rethrow > unsetShadow);
        assertTrue("Post-shadow cleanup must unbind the shadow program through the cleanup aggregator",
            unbind >= 0);
        assertTrue("Post-shadow cleanup must still rebind the gbuffer if program cleanup fails",
            bindGbuffer > unbind);
        assertTrue("Post-shadow cleanup must still restore the Minecraft viewport if gbuffer binding fails",
            viewport > bindGbuffer && returnsFailure > viewport);
        assertTrue(viewportRestore.contains(
            "GL11.glViewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);"));
    }

    @Test
    public void shadowBoundaryFailuresResetPreparedFrameLifecycleLikeCameraSetupFailures() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String renderShadowsBody = methodBody(source,
            "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        String reset = methodBody(source, "private void resetFrameLifecycleAfterBeginFailure");

        int outerTry = renderShadowsBody.indexOf("try {");
        int ensureRenderer = renderShadowsBody.indexOf("ensureShadowRendererInitialized();", outerTry);
        int prepareTargets = renderShadowsBody.indexOf("prepareShadowRenderTargets();", ensureRenderer);
        int prepareBefore = renderShadowsBody.indexOf("if (prepareBeforeShadow)", prepareTargets);
        int shadowRender = renderShadowsBody.indexOf("shadowRenderer.renderShadows(renderGlobal, cameraEntity, partialTicks);",
            prepareBefore);
        int prepareAfter = renderShadowsBody.indexOf("if (!prepareBeforeShadow)", shadowRender);
        int outerCatch = renderShadowsBody.lastIndexOf("catch (RuntimeException | Error exception)");
        int activeFrameGuard = renderShadowsBody.indexOf("if (prepared || isRenderingWorld)", outerCatch);
        int restoreFrame = renderShadowsBody.indexOf(
            "rethrowCleanupFailure(restoreAfterFrameBeginFailure(exception));", activeFrameGuard);
        int rethrow = renderShadowsBody.indexOf("throw exception;", restoreFrame);

        assertTrue("Shadow rendering must guard target preparation, prepare passes, and shadow terrain rendering together",
            outerTry >= 0 && ensureRenderer > outerTry && prepareTargets > ensureRenderer
                && prepareBefore > prepareTargets && shadowRender > prepareBefore && prepareAfter > shadowRender);
        assertTrue("Shadow boundary failures during an active frame must use the same cleanup as camera setup failures",
            outerCatch > prepareAfter && activeFrameGuard > outerCatch && restoreFrame > activeFrameGuard
                && rethrow > restoreFrame);
        assertTrue("Begin-failure cleanup must clear shadow and prepared-frame state after a shadow-boundary abort",
            reset.contains("shadowRenderTargetsPreparedThisFrame = false;")
                && reset.contains("isRenderingShadow = false;")
                && reset.contains("frameRuntimeUniformsPrepared = false;"));
    }

    @Test
    public void shadowAndLightingFallbackBooleansMatchReferencePipeline() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("public boolean shouldDisableVanillaEntityShadows() {\n"
            + "        return shadowRenderer != null;\n"
            + "    }"));
        assertTrue(source.contains("public boolean shouldDisableDirectionalShading() {\n"
            + "        return !oldLighting;\n"
            + "    }"));
    }

    @Test
    public void forcedShadowDistanceDisplayUsesReferenceChunkRounding() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int method = source.indexOf("private OptionalInt resolveForcedShadowDistance");
        int nextMethod = source.indexOf("private void ensureSetup()", method);
        String body = source.substring(method, nextMethod);

        assertTrue(method >= 0);
        assertTrue(body.contains("int chunks = ((int) (distance * mul) + 15) / 16;"));
        assertFalse(body.contains("Math.ceil"));
    }

    @Test
    public void shaderCompilationFailuresPropagateToPipelineFallbackBoundary() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String body = methodBody(source, "private void ensureShadersCompiled()");

        assertTrue(source.contains("private void ensureShadersCompiled()"));
        assertTrue("Shader compilation cleanup must catch hard errors from lazy shadow renderer initialization",
            body.contains("} catch (RuntimeException | Error exception) {"));

        int catchBlock = body.indexOf("} catch (RuntimeException | Error exception) {");
        int logFailure = body.indexOf(
            "LOGGER.error(\"Failed to compile shaders for pack {}\", pack.getName(), exception);", catchBlock);
        int resetCompiled = body.indexOf("shadersCompiled = false;", logFailure);
        int rethrow = body.indexOf("throw exception;", resetCompiled);
        assertTrue("Shader compilation failures must log, clear the compiled flag, and rethrow the original failure",
            catchBlock >= 0 && logFailure > catchBlock && resetCompiled > logFailure && rethrow > resetCompiled);
    }

    @Test
    public void shadowProgramsUsePrepareFlipsOnlyWhenPrepareRunsBeforeShadow() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("this.prepareBeforeShadow = directives.isPrepareBeforeShadow();"));
        assertTrue(source.contains("this::getCurrentRenderTargetReadBuffers"));
        assertTrue(source.contains("private Set<Integer> getCurrentRenderTargetReadBuffers()"));
        assertTrue(source.contains("return isRenderingShadow ? getShadowReadBuffers() : getActiveReadBuffers();"));
        assertTrue(source.contains("private Set<Integer> getShadowReadBuffers()"));
        assertTrue(source.contains("return prepareBeforeShadow ? flippedAfterPrepare : Collections.emptySet();"));
        assertFalse(source.contains("isRenderingShadow ? flippedAfterPrepare : getActiveReadBuffers()"));
        assertFalse(source.contains("this::getActiveReadBuffers, index"));
        assertFalse(source.contains("() -> flippedAfterPrepare,"));
    }

    @Test
    public void shadowComputeBindingsUseStageSpecificFlipSetsLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int constructor = source.indexOf("createdShadowRenderer = new net.oculus.pipeline.shadow.ShadowRenderer(");
        assertTrue(constructor >= 0);

        String shadowRendererArgs = source.substring(constructor,
            source.indexOf("LOGGER.info(\"Shadow rendering enabled", constructor));
        assertTrue(shadowRendererArgs.contains("programSet.getShadow().orElse(null),"));
        assertTrue(shadowRendererArgs.contains("programSet.getShadowCompute(),"));
        assertTrue(shadowRendererArgs.contains("programSet.getShadowCompCompute(),"));
        assertTrue(shadowRendererArgs.contains("() -> Collections.emptySet(),"));
        assertTrue(shadowRendererArgs.contains("this::getShadowReadBuffers,"));
        assertTrue(shadowRendererArgs.contains("this,"));
        assertTrue(shadowRendererArgs.contains("customTextureManager::getNoiseTextureId,"));
        assertTrue(shadowRendererArgs.indexOf("programSet.getShadowCompCompute(),") >
            shadowRendererArgs.indexOf("programSet.getShadowCompute(),"));
        assertTrue("Root shadow computes run during target preparation before prepareBeforeShadow executes",
            shadowRendererArgs.indexOf("() -> Collections.emptySet(),") >
                shadowRendererArgs.indexOf("() -> renderTargets,"));
        assertTrue("Compute-only shadowcomp passes should see the same shadow-stage read buffers as shadow raster",
            shadowRendererArgs.indexOf("this::getShadowReadBuffers,") >
                shadowRendererArgs.indexOf("() -> Collections.emptySet(),"));
        assertTrue(shadowRendererArgs.indexOf("this,") >
            shadowRendererArgs.indexOf("this::getShadowReadBuffers,"));
        assertTrue(shadowRendererArgs.indexOf("customTextureManager::getNoiseTextureId,") >
            shadowRendererArgs.indexOf("this,"));
        assertFalse(shadowRendererArgs.contains("() -> flippedAfterPrepare,"));
    }

    @Test
    public void shaderCompileRechecksShadowRendererAfterLazyShadowTargetAllocation() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String getOrCreateShadowMap = methodBody(source,
            "private net.oculus.pipeline.shadow.ShadowMap getOrCreateShadowMap()");
        String ensureShadersCompiled = methodBody(source, "private void ensureShadersCompiled()");
        String beginLevel = methodBody(source, "public void beginLevelRendering()");

        int assignShadowMap = getOrCreateShadowMap.indexOf("shadowMap = createdShadowMap;");
        int assignDisabledClears = getOrCreateShadowMap.indexOf(
            "disabledShadowClearPassesFull = createdDisabledShadowClearPassesFull;", assignShadowMap);
        int invalidateRenderer = getOrCreateShadowMap.indexOf(
            "shadowRendererInitialized = false;", assignDisabledClears);

        int initializeLoader = ensureShadersCompiled.indexOf("shaderLoader.initialize(context);");
        int markCompiled = ensureShadersCompiled.indexOf("shadersCompiled = true;", initializeLoader);
        int lazyShadowGuard = ensureShadersCompiled.indexOf(
            "if (shadowMap != null && !shadowRendererInitialized)", markCompiled);
        int reinitializeRenderer = ensureShadersCompiled.indexOf(
            "ensureShadowRendererInitialized();", lazyShadowGuard);
        int updateImageHint = ensureShadersCompiled.indexOf(
            "updateShadowRendererImageCullingHint();", reinitializeRenderer);

        int initialShadowInit = beginLevel.indexOf("ensureShadowRendererInitialized();");
        int compileShaders = beginLevel.indexOf("ensureShadersCompiled();", initialShadowInit);
        int clearTargets = beginLevel.indexOf("clearRenderTargets();", compileShaders);

        assertTrue("Lazy shadow target creation must invalidate the renderer initialized flag",
            assignShadowMap >= 0 && assignDisabledClears > assignShadowMap
                && invalidateRenderer > assignDisabledClears);
        assertTrue("Shader compilation must re-run shadow renderer initialization when compilation allocated shadow targets",
            initializeLoader >= 0 && markCompiled > initializeLoader && lazyShadowGuard > markCompiled
                && reinitializeRenderer > lazyShadowGuard);
        assertTrue("Shadow image usage must be propagated after any lazy renderer initialization",
            updateImageHint > reinitializeRenderer);
        assertTrue("The frame setup path must still compile and initialize lazy shadow targets before clears run",
            initialShadowInit >= 0 && compileShaders > initialShadowInit && clearTargets > compileShaders);
    }

    @Test
    public void shadowRendererInitializationPublishesOnlyAfterSuccessfulConstruction() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String body = methodBody(source, "private void ensureShadowRendererInitialized()");

        int forcedShadowMap = body.indexOf("ensureForcedShadowMapInitialized();");
        int disabledBranch = body.indexOf(
            "if (shadowMap == null || !shadowMap.isEnabled()", forcedShadowMap);
        int disabledNullRenderer = body.indexOf("this.shadowRenderer = null;", disabledBranch);
        int disabledPublish = body.indexOf("shadowRendererInitialized = true;", disabledNullRenderer);
        int disabledReturn = body.indexOf("return;", disabledPublish);
        int stagedLocal = body.indexOf(
            "net.oculus.pipeline.shadow.ShadowRenderer createdShadowRenderer = null;", disabledReturn);
        int tryBlock = body.indexOf("try {", stagedLocal);
        int construct = body.indexOf(
            "createdShadowRenderer = new net.oculus.pipeline.shadow.ShadowRenderer(", tryBlock);
        int publishRenderer = body.indexOf("this.shadowRenderer = createdShadowRenderer;", construct);
        int updateImageHint = body.indexOf("updateShadowRendererImageCullingHint();", publishRenderer);
        int publishInitialized = body.indexOf("shadowRendererInitialized = true;", updateImageHint);
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", publishInitialized);
        int clearRenderer = body.indexOf("this.shadowRenderer = null;", catchBlock);
        int resetInitialized = body.indexOf("shadowRendererInitialized = false;", clearRenderer);
        int cleanupGuard = body.indexOf("if (createdShadowRenderer != null)", resetInitialized);
        int destroyCreated = body.indexOf(
            "failure = runCleanup(failure, createdShadowRenderer::destroy);", cleanupGuard);
        int suppressCleanup = body.indexOf("addSuppressedCleanupFailure(exception, failure);", destroyCreated);
        int rethrow = body.indexOf("throw exception;", suppressCleanup);

        assertTrue("Shadow renderer init must not publish initialized before checking disabled/fallback paths",
            forcedShadowMap >= 0 && disabledBranch > forcedShadowMap
                && !body.substring(forcedShadowMap, disabledBranch).contains("shadowRendererInitialized = true;"));
        assertTrue("Disabled or unavailable shadows can publish initialized only after clearing the renderer",
            disabledNullRenderer > disabledBranch && disabledPublish > disabledNullRenderer
                && disabledReturn > disabledPublish);
        assertTrue("Enabled shadows must stage the renderer before publishing it",
            stagedLocal > disabledReturn && tryBlock > stagedLocal && construct > tryBlock
                && publishRenderer > construct);
        assertTrue("The initialized flag must be set only after image usage has been propagated",
            updateImageHint > publishRenderer && publishInitialized > updateImageHint);
        assertTrue("Construction failures must clear published state before cleanup/rethrow",
            catchBlock > publishInitialized && clearRenderer > catchBlock
                && resetInitialized > clearRenderer);
        assertTrue("Partially created shadow renderers must be destroyed and suppressed onto the cause",
            cleanupGuard > resetInitialized && destroyCreated > cleanupGuard
                && suppressCleanup > destroyCreated && rethrow > suppressCleanup);
    }

    @Test
    public void shadowRendererReceivesCompiledImageUsageForVoxelizationCulling() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int method = source.indexOf("private void updateShadowRendererImageCullingHint()");
        int nextMethod = source.indexOf("private OptionalInt resolveForcedShadowDistance", method);
        String body = source.substring(method, nextMethod);

        assertTrue(method >= 0);
        assertTrue(body.contains("if (!shadersCompiled || shadowRenderer == null)"));
        assertTrue(body.contains("ProgramSource shadowSource = programSet.getShadow().orElse(null);"));
        assertTrue(body.contains("Program shadowProgram = shaderLoader.getProgram(shadowSource.getName(), new InputAvailability(true, true, true));"));
        assertTrue(body.contains("shadowRenderer.setUsesImages(shadowProgram != null && shadowProgram.getActiveImages() > 0);"));

        int compile = source.indexOf("private void ensureShadersCompiled()");
        int initialized = source.indexOf("shadersCompiled = true;", compile);
        int update = source.indexOf("updateShadowRendererImageCullingHint();", initialized);
        assertTrue("Shadow image usage must be propagated after the availability-specific shadow program is compiled",
            update > initialized);
    }

    @Test
    public void shadowTargetPreparationRunsBeforePreparePassAndShadowProgramSync() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("private boolean shadowRenderTargetsPreparedThisFrame;"));
        assertTrue(source.contains("shadowRenderTargetsPreparedThisFrame = false;"));
        assertTrue(source.contains("private void prepareShadowRenderTargets()"));
        String prepareShadowTargets = methodBody(source, "private void prepareShadowRenderTargets()");
        int preparedGuard = prepareShadowTargets.indexOf("if (shadowRenderTargetsPreparedThisFrame)");
        int ensureInitialized = prepareShadowTargets.indexOf("ensureShadowRendererInitialized();", preparedGuard);
        int rendererGuard = prepareShadowTargets.indexOf("if (shadowRenderer == null)", ensureInitialized);
        int prepareTargets = prepareShadowTargets.indexOf("shadowRenderer.prepareRenderTargets();", rendererGuard);
        int markPrepared = prepareShadowTargets.indexOf("shadowRenderTargetsPreparedThisFrame = true;", prepareTargets);
        assertTrue("Shadow target preparation must re-check renderer initialization after shader compilation can lazily allocate targets",
            preparedGuard >= 0 && ensureInitialized > preparedGuard && rendererGuard > ensureInitialized);
        assertTrue("Shadow target preparation must only mark the frame prepared after renderer preparation runs",
            prepareTargets > rendererGuard && markPrepared > prepareTargets);
        assertTrue(source.contains("shadowRenderer.prepareRenderTargets();"));
        assertTrue(source.contains("shadowRenderTargetsPreparedThisFrame = true;"));

        int afterCameraSetup = source.indexOf("public void afterCameraSetup(float partialTicks)");
        int customUniformFrame = source.indexOf("customUniforms.beginFrame();", afterCameraSetup);
        int afterCameraPrepare = source.indexOf("prepareShadowRenderTargets();", customUniformFrame);
        int afterCameraSync = source.indexOf("syncProgram();", afterCameraPrepare);
        assertTrue(afterCameraSetup >= 0);
        assertTrue("Shadow target preparation must wait until 1.12 camera-dependent custom uniforms pre-evaluate",
            customUniformFrame > afterCameraSetup);
        assertTrue("Shadow target preparation must occur before the post-camera world program sync",
            afterCameraPrepare > customUniformFrame && afterCameraSync > afterCameraPrepare);

        int renderShadows = source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        int renderPrepareTargets = source.indexOf("prepareShadowRenderTargets();", renderShadows);
        int prepareBeforeShadow = source.indexOf("if (prepareBeforeShadow)", renderShadows);
        int shadowFlag = source.indexOf("isRenderingShadow = true;", renderShadows);
        int bindShadowFramebuffer = source.indexOf("shadowRenderer.bindFramebufferForShadowPass();", shadowFlag);
        int shadowSync = source.indexOf("syncProgram();", bindShadowFramebuffer);
        String renderShadowsBody = source.substring(renderShadows, source.indexOf("private void prepareShadowRenderTargets()", renderShadows));
        assertTrue(renderShadows >= 0);
        assertTrue("Shadow targets must be prepared before a prepare pass that runs before shadows",
            renderPrepareTargets > renderShadows && renderPrepareTargets < prepareBeforeShadow);
        assertTrue("Shadow compute must run before the root shadow raster program is synced",
            renderPrepareTargets < shadowFlag && shadowSync > shadowFlag);
        assertTrue("The root shadow framebuffer must be selected before the root shadow program is used",
            bindShadowFramebuffer > shadowFlag && shadowSync > bindShadowFramebuffer);
        assertFalse("1.16.5 does not issue a renderer-level post-shadow memory barrier",
            renderShadowsBody.contains("OculusRenderSystem.memoryBarrier(SHADOW_IMAGE_BARRIER);"));
        assertFalse(source.contains("private static final int SHADOW_IMAGE_BARRIER"));
    }

    @Test
    public void postShadowCleanupUsesSingleFailureAggregatingPath() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String renderShadowsBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private Throwable restoreAfterShadowRender", source.indexOf(
                "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)")));

        int finallyBlock = renderShadowsBody.indexOf("} finally {");
        int restoreCall = renderShadowsBody.indexOf("failure = restoreAfterShadowRender(failure);", finallyBlock);
        int clearShadowFlag = renderShadowsBody.indexOf("isRenderingShadow = false;", restoreCall);
        int rethrow = renderShadowsBody.indexOf("rethrowCleanupFailure(failure);", clearShadowFlag);

        assertTrue("Shadow rendering must aggregate cleanup onto the active failure from the finally path",
            finallyBlock >= 0 && restoreCall > finallyBlock);
        assertTrue("The shadow-rendering flag must clear only after the cleanup helper records failures",
            clearShadowFlag > restoreCall && rethrow > clearShadowFlag);
        assertFalse("A no-arg shadow cleanup wrapper risks running post-shadow cleanup outside the active failure path",
            source.contains("private void restoreAfterShadowRender()"));
    }

    @Test
    public void rasterShadowCompositeSourcesFailClearlyWhileComputePassesReachShadowRenderer() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);
        String constructor = methodBody(source,
            "public ShaderWorldRenderingPipeline(ShaderPack pack, ProgramSet programSet, ShaderProperties shaderProperties)");
        String rejectHelper = methodBody(source,
            "static void rejectUnsupportedShadowCompositeRasterPrograms");
        String firstUnsupportedHelper = methodBody(source,
            "private static String firstUnsupportedShadowCompositeRasterProgram");

        int requireProgramSet = constructor.indexOf(
            "this.programSet = Objects.requireNonNull(programSet, \"programSet\");");
        int rejectUnsupported = constructor.indexOf(
            "rejectUnsupportedShadowCompositeRasterPrograms(this.programSet);", requireProgramSet);
        int runtimeManagers = constructor.indexOf(
            "this.customTextureManager = CustomTextureManager.fromShaderPack", rejectUnsupported);
        int shadowRenderer = source.indexOf("createdShadowRenderer = new net.oculus.pipeline.shadow.ShadowRenderer(");
        int rootShadowComputes = source.indexOf("programSet.getShadowCompute(),", shadowRenderer);
        int shadowCompositeComputes = source.indexOf("programSet.getShadowCompCompute(),", rootShadowComputes);

        assertFalse(source.contains("shadowCompositeRenderer"));
        assertTrue("Constructor must reject unsupported raster shadow composites before runtime GL/resource managers initialize",
            requireProgramSet >= 0 && rejectUnsupported > requireProgramSet && runtimeManagers > rejectUnsupported);
        assertTrue(rejectHelper.contains("throw new ProgramLoadException(\"Shader pack requires unsupported raster shadow composite pass \""));
        assertTrue(rejectHelper.contains("compute-only shadowcomp"));
        assertTrue(firstUnsupportedHelper.contains("programSet.getShadowComposite()"));
        assertTrue(firstUnsupportedHelper.contains("source != null && source.isValid()"));
        assertFalse(firstUnsupportedHelper.contains("programSet.getShadowCompCompute()"));
        assertTrue("Compute-only shadowcomp groups must be handed to ShadowRenderer instead of rejected",
            shadowCompositeComputes > rootShadowComputes);
    }

    @Test
    public void disabledRequestedShadowTargetsAreFullClearedWithoutCreatingShadowRenderer() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);
        String supplier = source.substring(
            source.indexOf("private net.oculus.pipeline.shadow.ShadowMap getOrCreateShadowMap()"),
            source.indexOf("private void ensureForcedShadowMapInitialized()"));
        String clearDisabled = methodBody(source, "private void clearDisabledShadowTargets");
        String destroyShadow = methodBody(source, "private void destroyShadowRenderer");

        assertTrue(source.contains("private void clearDisabledShadowTargets()"));
        assertTrue(source.contains("private List<ClearPass> disabledShadowClearPassesFull = Collections.emptyList();"));
        assertTrue(source.contains("shadowDirectives.isShadowEnabled() != OptionalBoolean.FALSE"));
        assertTrue("Disabled shadow target allocation must create persistent full-clear passes like 1.16.5",
            supplier.contains("ClearPassCreator.createShadowClearPasses(\n"
                + "                        createdShadowMap, true, shadowDirectives);"));
        assertFalse("Disabled shadow-target allocation must not consume the full-clear flag before frame preparation",
            supplier.contains("consumeFullClearRequired()"));

        int fullClearGuard = clearDisabled.indexOf("if (!shadowMap.isFullClearRequired())");
        int ensurePasses = clearDisabled.indexOf("ensureDisabledShadowClearPassesInitialized();", fullClearGuard);
        int consumeFullClear = clearDisabled.indexOf("shadowMap.consumeFullClearRequired();", ensurePasses);
        int executeLoop = clearDisabled.indexOf("for (ClearPass clearPass : disabledShadowClearPassesFull)",
            consumeFullClear);
        int execute = clearDisabled.indexOf("clearPass.execute(SHADOW_CLEAR_DEFAULT);", executeLoop);
        assertTrue("Disabled shadow-target clearing must guard on the full-clear bit",
            fullClearGuard >= 0);
        assertTrue("Disabled shadow-target clearing must create persistent clear passes before consuming the flag",
            ensurePasses > fullClearGuard && consumeFullClear > ensurePasses);
        assertTrue("Disabled shadow-target clearing must execute persistent full clear passes",
            executeLoop > consumeFullClear && execute > executeLoop);
        assertFalse("Disabled shadow-target clearing must not use the temporary ShadowMap clear path",
            clearDisabled.contains("clearFullColorBuffersIfRequired()"));

        int destroyDisabledPasses = destroyShadow.indexOf("failure = destroyDisabledShadowClearPassFramebuffers(failure);");
        int destroyShadowMap = destroyShadow.indexOf("failure = runCleanup(failure, shadowMap::destroy);",
            destroyDisabledPasses);
        int rethrowCleanup = destroyShadow.indexOf("rethrowCleanupFailure(failure);", destroyShadowMap);
        int finallyCleanup = destroyShadow.indexOf("} finally {", rethrowCleanup);
        int clearShadowRenderer = destroyShadow.indexOf("shadowRenderer = null;", finallyCleanup);
        int clearShadowMap = destroyShadow.indexOf("shadowMap = null;", clearShadowRenderer);
        int clearPreparedFlag = destroyShadow.indexOf("shadowRenderTargetsPreparedThisFrame = false;", clearShadowMap);
        assertTrue("Disabled shadow clear-pass framebuffers must be destroyed before the shadow map textures",
            destroyDisabledPasses >= 0 && destroyShadowMap > destroyDisabledPasses);
        assertTrue("Shadow teardown must clear stale renderer/map handles even when cleanup fails",
            rethrowCleanup > destroyShadowMap && finallyCleanup > rethrowCleanup
                && clearShadowRenderer > finallyCleanup && clearShadowMap > clearShadowRenderer
                && clearPreparedFlag > clearShadowMap);

        int clearCall = source.indexOf("clearDisabledShadowTargets();");
        int renderTargetReady = source.indexOf(
            "requirePipelineResource(renderTargets, \"render targets\", \"render-target clear\");", clearCall);
        assertTrue(clearCall >= 0);
        assertTrue(renderTargetReady >= 0);
        assertTrue("Disabled requested shadow targets must clear before main render-target clears require live targets",
            clearCall < renderTargetReady);
    }

    @Test
    public void colorSpaceConversionRequiresMainFramebufferAndColorTextureLikeReference() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);
        String body = methodBody(source, "private void runColorSpaceConversion()");

        int minecraft = body.indexOf("Minecraft minecraft = Minecraft.getMinecraft();");
        int minecraftGuard = body.indexOf("if (minecraft == null)", minecraft);
        int minecraftThrow = body.indexOf(
            "throw new IllegalStateException(\"Color-space conversion requires a Minecraft instance\");",
            minecraftGuard);
        int framebuffer = body.indexOf("net.minecraft.client.shader.Framebuffer mainFramebuffer = minecraft.getFramebuffer();",
            minecraftThrow);
        int framebufferGuard = body.indexOf("if (mainFramebuffer == null)", framebuffer);
        int framebufferThrow = body.indexOf(
            "throw new IllegalStateException(\"Color-space conversion requires a Minecraft main framebuffer\");",
            framebufferGuard);
        int colorTexture = body.indexOf("int colorTexture = mainFramebuffer.framebufferTexture;",
            framebufferThrow);
        int colorGuard = body.indexOf("if (colorTexture <= 0)", colorTexture);
        int colorThrow = body.indexOf(
            "throw new IllegalStateException(\"Color-space conversion requires a valid Minecraft main color texture\");",
            colorGuard);
        int width = body.indexOf("int width = Math.max(1, mainFramebuffer.framebufferWidth);", colorThrow);
        int rebuild = body.indexOf("ensureColorSpaceConverterUpToDate(width, height);", width);
        int failureLocal = body.indexOf("Throwable failure = null;", rebuild);
        int tryBlock = body.indexOf("try {", failureLocal);
        int requireConverter = body.indexOf(
            "requirePipelineResource(colorSpaceConverter, \"color-space converter\", \"color-space conversion\")",
            tryBlock);
        int process = body.indexOf(".process(colorTexture);", requireConverter);
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", process);
        int rememberFailure = body.indexOf("failure = exception;", catchBlock);
        int finallyBlock = body.indexOf("finally {", rememberFailure);
        int cleanupFailure = body.indexOf(
            "Throwable cleanupFailure = runCleanup(null, () -> mainFramebuffer.bindFramebuffer(true));",
            finallyBlock);
        int suppressCleanup = body.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);",
            cleanupFailure);
        int rethrowCleanup = body.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressCleanup);

        assertTrue(minecraft >= 0);
        assertTrue("Color-space conversion must fail clearly when the Minecraft instance is unavailable",
            minecraftGuard > minecraft && minecraftThrow > minecraftGuard);
        assertTrue("Color-space conversion must fail clearly when the main framebuffer is unavailable",
            framebuffer > minecraftThrow && framebufferGuard > framebuffer
                && framebufferThrow > framebufferGuard);
        assertTrue("Color-space conversion must fail clearly when the main color texture is unavailable",
            colorTexture > framebufferThrow && colorGuard > colorTexture && colorThrow > colorGuard);
        assertTrue("Color-space conversion must update converter size/config before processing the final color texture",
            width > colorThrow && rebuild > width && requireConverter > rebuild && process > requireConverter);
        assertTrue("Color-space conversion must process inside a cleanup guard",
            failureLocal > rebuild && tryBlock > failureLocal && process > tryBlock);
        assertTrue("The main framebuffer must be rebound even when color-space conversion fails",
            catchBlock > process && rememberFailure > catchBlock && finallyBlock > rememberFailure
                && cleanupFailure > finallyBlock);
        assertTrue("Color-space conversion cleanup failures must suppress onto the primary conversion failure",
            suppressCleanup > cleanupFailure && rethrowCleanup > suppressCleanup);
        assertFalse(body.contains("return;"));
        assertFalse(body.contains("mainFramebuffer.framebufferTexture > 0"));
    }

    @Test
    public void colorSpaceConverterIsRebuiltForMainFramebufferSizeChanges() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("private int colorSpaceWidth = -1;"));
        assertTrue(source.contains("private int colorSpaceHeight = -1;"));
        assertTrue(source.contains("this.colorSpaceWidth = safeWidth;"));
        assertTrue(source.contains("this.colorSpaceHeight = safeHeight;"));
        assertTrue(source.contains("safeWidth != colorSpaceWidth || safeHeight != colorSpaceHeight"));
        assertTrue(source.contains("rebuildColorSpaceConverter(safeWidth, safeHeight);"));

        int widthTracking = source.indexOf("this.colorSpaceWidth = safeWidth;");
        int noOpBranch = source.indexOf("directives.supportsColorCorrection()");
        assertTrue(widthTracking >= 0);
        assertTrue(noOpBranch >= 0);
        assertTrue("Color-space dimensions must be tracked even for the no-op converter",
            widthTracking < noOpBranch);
    }

    @Test
    public void translucentEntityProgramRequiresBlendToBeEnabled() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String body = methodBody(source, "private boolean isStandardTranslucentBlend()");

        int blendEnabled = body.indexOf("GL11.glIsEnabled(GL11.GL_BLEND)");
        int srcRgb = body.indexOf("GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)", blendEnabled);
        int dstRgb = body.indexOf("GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)", srcRgb);

        assertTrue("Stale blend factors must not select gbuffers_entities_translucent while blending is disabled",
            blendEnabled >= 0 && srcRgb > blendEnabled && dstRgb > srcRgb);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
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
