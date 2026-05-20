package net.oculus.postprocess;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import net.oculus.gl.program.ComputeProgram;
import org.junit.Test;
import sun.misc.Unsafe;

public class PostprocessRendererSourceTest {
    @Test
    public void compositeAndFinalCompilationFailuresAreNotSilentlyDropped() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertFalse(composite.contains("Failed to create composite pass"));
        assertFalse(composite.contains("Failed to compile compute shader"));
        assertFalse(finalPass.contains("Final pass shader compilation failed"));
        assertFalse(finalPass.contains("Failed to compile final compute shader"));
        assertFalse(finalPass.contains("Final pass missing vertex or fragment source, using passthrough"));

        assertTrue(composite.contains(
            "Pass pass = createPass(source, i, computeSources, stageReadsFromAlt, flippedAtLeastOnceSnapshot);"));
        assertTrue(composite.contains("ComputeProgram program = builder.buildCompute();"));
        assertTrue(finalPass.contains("throw new IllegalStateException(\"Final pass \" + source.getName()"));
        assertTrue(finalPass.contains("+ \" is missing vertex or fragment source\");"));
        assertTrue(finalPass.contains("createdProgram = builder.build();"));
        assertTrue(finalPass.contains("ComputeProgram program = builder.buildCompute();"));
    }

    @Test
    public void postprocessConstructorsUnbindReadFramebufferAfterFramebufferCreation() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String compositeUnbind = methodBody(composite, "private static void unbindReadFramebufferAfterConstruction()");
        String finalUnbind = methodBody(finalPass, "private static void unbindReadFramebufferAfterConstruction()");

        assertTrue(composite.contains("private static void unbindReadFramebufferAfterConstruction()"));
        assertTrue(compositeUnbind.contains("OculusRenderSystem.bindReadFramebuffer(0);"));
        assertFalse(compositeUnbind.contains("OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);"));
        assertTrue(composite.indexOf("this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();")
            < composite.indexOf("unbindReadFramebufferAfterConstruction();"));
        String compositeConstructor = between(composite,
            "public CompositeRenderer(PackDirectives packDirectives,",
            "public ImmutableSet<Integer> getFlippedAtLeastOnceFinal()");
        int compositeFinalSnapshot = compositeConstructor.indexOf(
            "this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();");
        int compositeUnbindCall = compositeConstructor.indexOf("unbindReadFramebufferAfterConstruction();",
            compositeFinalSnapshot);
        int compositeUnbindCatch = compositeConstructor.indexOf("catch (RuntimeException | Error exception)",
            compositeUnbindCall);
        int compositeDestroyCreated = compositeConstructor.indexOf("destroyCreatedPasses();", compositeUnbindCatch);
        int compositeCleanupCatch = compositeConstructor.indexOf(
            "catch (RuntimeException | Error cleanupException)", compositeDestroyCreated);
        int compositeSuppress = compositeConstructor.indexOf("exception.addSuppressed(cleanupException);",
            compositeCleanupCatch);
        int compositeRethrow = compositeConstructor.indexOf("throw exception;", compositeSuppress);
        assertTrue("Composite constructor must clean created passes if the final read-framebuffer unbind fails",
            compositeUnbindCall > compositeFinalSnapshot && compositeUnbindCatch > compositeUnbindCall
                && compositeDestroyCreated > compositeUnbindCatch && compositeCleanupCatch > compositeDestroyCreated
                && compositeSuppress > compositeCleanupCatch && compositeRethrow > compositeSuppress);

        assertTrue(finalPass.contains("private static void unbindReadFramebufferAfterConstruction()"));
        assertTrue(finalUnbind.contains("OculusRenderSystem.bindReadFramebuffer(0);"));
        assertFalse(finalUnbind.contains("OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);"));
        assertTrue(countOccurrences(finalPass, "unbindReadFramebufferAfterConstruction();") >= 1);
        assertTrue(finalPass.indexOf("this.mipmappedBuffers = createdMipmappedBuffers;")
            < finalPass.indexOf("unbindReadFramebufferAfterConstruction();"));
        String finalConstructor = between(finalPass,
            "public FinalPassRenderer(PackDirectives packDirectives,",
            "private void bindCenterDepthSmooth");
        int finalAssignment = finalConstructor.indexOf("this.mipmappedBuffers = createdMipmappedBuffers;");
        int finalUnbindCall = finalConstructor.indexOf("unbindReadFramebufferAfterConstruction();", finalAssignment);
        int finalUnbindCatch = finalConstructor.indexOf("catch (RuntimeException | Error exception)",
            finalUnbindCall);
        int finalFailure = finalConstructor.indexOf("Throwable failure = null;", finalUnbindCatch);
        int finalDestroyProgram = finalConstructor.indexOf("failure = destroyProgram(failure, createdProgram);",
            finalFailure);
        int finalDestroyComputes = finalConstructor.indexOf(
            "failure = destroyComputePrograms(failure, createdComputes);", finalDestroyProgram);
        int finalDestroyBaseline = finalConstructor.indexOf("failure = destroyFramebuffer(failure, createdBaseline);",
            finalDestroyComputes);
        int finalDestroyColorHolder = finalConstructor.indexOf(
            "failure = destroyGlFramebuffer(failure, createdColorHolder);", finalDestroyBaseline);
        int finalDestroySwapPasses = finalConstructor.indexOf(
            "failure = destroySwapPasses(failure, createdSwapPasses);", finalDestroyColorHolder);
        int finalSuppress = finalConstructor.indexOf("addSuppressedCleanupFailure(exception, failure);",
            finalDestroySwapPasses);
        int finalRethrow = finalConstructor.indexOf("throw exception;", finalSuppress);
        assertTrue("Final constructor must clean created resources if the final read-framebuffer unbind fails",
            finalUnbindCall > finalAssignment && finalUnbindCatch > finalUnbindCall
                && finalFailure > finalUnbindCatch && finalDestroyProgram > finalFailure
                && finalDestroyComputes > finalDestroyProgram && finalDestroyBaseline > finalDestroyComputes
                && finalDestroyColorHolder > finalDestroyBaseline
                && finalDestroySwapPasses > finalDestroyColorHolder
                && finalSuppress > finalDestroySwapPasses && finalRethrow > finalSuppress);
    }

    @Test
    public void compositePassFramebufferOwnsDrawBufferRouting() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String renderTargets = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");

        assertTrue(composite.contains(
            "framebuffer = renderTargets.createColorFramebuffer(stageReadsFromAlt, drawBuffers);"));
        assertTrue(renderTargets.contains("framebuffer.drawBuffers(logicalDrawBuffers(drawBuffers.length));"));
        assertFalse(composite.contains("glDrawBuffers"));

        int viewport = composite.indexOf("GL11.glViewport(0, 0, scaledWidth, scaledHeight);");
        int bind = composite.indexOf("pass.framebuffer.bind();", viewport);
        int use = composite.indexOf("pass.program.use();", bind);

        assertTrue(viewport >= 0);
        assertTrue(bind > viewport);
        assertTrue(use > bind);
    }

    @Test
    public void postprocessBindsCompositeDepthSamplersFromLiveRenderTargets() throws Exception {
        String samplers = read("src/main/java/net/oculus/samplers/IrisSamplers.java");
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertTrue(samplers.contains(
            "public static final ImmutableSet<Integer> COMPOSITE_RESERVED_TEXTURE_UNITS = ImmutableSet.of(1, 2);"));
        assertFalse(composite.contains("private static final ImmutableSet<Integer> COMPOSITE_RESERVED_TEXTURE_UNITS"));
        assertFalse(finalPass.contains("private static final ImmutableSet<Integer> COMPOSITE_RESERVED_TEXTURE_UNITS"));
        assertTrue("Composite raster and compute programs must use the shared 1.16.5 composite reserved units",
            countOccurrences(composite, "IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS") >= 2);
        assertTrue("Final raster and compute programs must use the shared 1.16.5 composite reserved units",
            countOccurrences(finalPass, "IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS") >= 2);

        assertTrue(samplers.contains("public static void addCompositeSamplerBindings"));
        assertTrue(samplers.contains("builder.overrideSamplerBinding(\"gdepthtex\", depth);"));
        assertTrue(samplers.contains("builder.overrideSamplerBinding(\"depthtex0\", depth);"));
        assertTrue(samplers.contains("builder.overrideSamplerBinding(\"depthtex1\", depthNoTranslucents);"));
        assertTrue(samplers.contains("builder.overrideSamplerBinding(\"depthtex2\", depthNoHand);"));

        assertTrue("Composite raster and compute programs must override depth samplers with the staged RenderTargets",
            countOccurrences(composite, "IrisSamplers.addCompositeSamplerBindings(builder, renderTargets);") >= 2);
        assertTrue("Final raster and compute programs must override depth samplers with the staged RenderTargets",
            countOccurrences(finalPass, "IrisSamplers.addCompositeSamplerBindings(builder, renderTargets);") >= 2);

        assertPostprocessSamplerOrder(composite, "private Pass createPass(");
        assertPostprocessSamplerOrder(composite, "private ComputeProgram[] createComputes(");
        assertPostprocessSamplerOrder(finalPass, "public FinalPassRenderer(PackDirectives packDirectives,");
        assertPostprocessSamplerOrder(finalPass, "private ComputeProgram[] createComputes(");
    }

    @Test
    public void postprocessFullscreenPassesDisableAndRestoreFixedFunctionFog() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertFogStateGuard(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll(");
        assertFogStateGuard(finalPass, "public void render()", "private Throwable restoreAfterRender(");
    }

    @Test
    public void compositeConstructorIteratesSourceArrayLikeReference() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");

        assertTrue(composite.contains("int passCount = sources == null ? 0 : sources.length;"));
        assertTrue(composite.contains("for (int i = 0; i < passCount; i++)"));
        assertTrue(composite.contains("ProgramSource source = sources[i];"));
        assertTrue(composite.contains("ComputeSource[] computeSources = computesAt(computes, i);"));
        assertTrue(composite.contains("if (computeSources != null)"));
        assertFalse(composite.contains("Math.max(sources"));
        assertFalse(composite.contains("sourceAt("));
        assertFalse(composite.contains("hasValidComputes"));
    }

    @Test
    public void compositeConstructorSnapshotsAndAppliesBufferFlipsLikeReference() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String constructorBody = between(composite,
            "public CompositeRenderer(PackDirectives packDirectives,",
            "public ImmutableSet<Integer> getFlippedAtLeastOnceFinal()");
        String destroyCreatedPasses = methodBody(composite, "private void destroyCreatedPasses()");

        int flippedAtLeastOnceBuilder = constructorBody.indexOf("ImmutableSet.Builder<Integer> flippedAtLeastOnce = ImmutableSet.builder();");
        int preFlips = constructorBody.indexOf("preFlips.forEach((buffer, shouldFlip) ->", flippedAtLeastOnceBuilder);
        int preFlipCall = constructorBody.indexOf("bufferFlipper.flip(buffer);", preFlips);
        int passLoop = constructorBody.indexOf("for (int i = 0; i < passCount; i++)", preFlipCall);
        int sourceLookup = constructorBody.indexOf("ProgramSource source = sources[i];", passLoop);
        int computeLookup = constructorBody.indexOf("ComputeSource[] computeSources = computesAt(computes, i);",
            sourceLookup);
        int stageSnapshot = constructorBody.indexOf("ImmutableSet<Integer> stageReadsFromAlt = bufferFlipper.snapshot();",
            computeLookup);
        int flippedSnapshot = constructorBody.indexOf(
            "ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();", stageSnapshot);
        int computeOnlyCreation = constructorBody.indexOf(
            "pass.computes = createComputes(computeSources, stageReadsFromAlt, flippedAtLeastOnceSnapshot);",
            flippedSnapshot);
        int createPass = constructorBody.indexOf(
            "Pass pass = createPass(source, i, computeSources, stageReadsFromAlt, flippedAtLeastOnceSnapshot);",
            flippedSnapshot);
        int explicitFlips = constructorBody.indexOf(
            "Map<Integer, Boolean> explicitFlips = source.getDirectives().getExplicitFlips();", createPass);
        int drawBufferLoop = constructorBody.indexOf("for (int buffer : pass.drawBuffers)", explicitFlips);
        int explicitFalse = constructorBody.indexOf("if (explicitFlips.get(buffer) == Boolean.FALSE)", drawBufferLoop);
        int drawBufferFlip = constructorBody.indexOf("bufferFlipper.flip(buffer);", explicitFalse);
        int drawBufferFlippedOnce = constructorBody.indexOf("flippedAtLeastOnce.add(buffer);", drawBufferFlip);
        int explicitTrueLoop = constructorBody.indexOf("explicitFlips.forEach((buffer, shouldFlip) ->",
            drawBufferFlippedOnce);
        int explicitTrueGuard = constructorBody.indexOf("if (Boolean.TRUE.equals(shouldFlip))", explicitTrueLoop);
        int explicitTrueFlip = constructorBody.indexOf("bufferFlipper.flip(buffer);", explicitTrueGuard);
        int explicitTrueFlippedOnce = constructorBody.indexOf("flippedAtLeastOnce.add(buffer);", explicitTrueFlip);
        int finalSnapshot = constructorBody.indexOf("this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();",
            explicitTrueFlippedOnce);

        assertTrue("CompositeRenderer must track normal per-pass writes separately from pre-flips",
            flippedAtLeastOnceBuilder >= 0);
        assertTrue("Explicit stage pre-flips must run before any pass snapshot is captured",
            preFlips > flippedAtLeastOnceBuilder && preFlipCall > preFlips && passLoop > preFlipCall);
        assertFalse("Explicit pre-flips must not count as flippedAtLeastOnce, matching the 1.16.5 constructor note",
            constructorBody.substring(preFlips, passLoop).contains("flippedAtLeastOnce.add"));
        assertTrue("Each pass must read source and matching compute array entry before taking flip snapshots",
            sourceLookup > passLoop && computeLookup > sourceLookup);
        assertTrue("Each pass must snapshot current read buffers before creating compute or raster programs",
            stageSnapshot > computeLookup && flippedSnapshot > stageSnapshot);
        assertTrue("Compute-only passes must use the pre-pass read snapshot",
            computeOnlyCreation > flippedSnapshot);
        assertTrue("Raster passes must be created before their draw buffers mutate flip state",
            createPass > flippedSnapshot && explicitFlips > createPass);
        assertTrue("Draw-buffer flips must honor explicit false overrides",
            drawBufferLoop > explicitFlips && explicitFalse > drawBufferLoop && drawBufferFlip > explicitFalse);
        assertTrue("Normal pass writes must mark buffers flipped at least once for later custom texture gating",
            drawBufferFlippedOnce > drawBufferFlip);
        assertTrue("Explicit true flips must run after normal draw-buffer flips like the reference",
            explicitTrueLoop > drawBufferFlippedOnce && explicitTrueGuard > explicitTrueLoop
                && explicitTrueFlip > explicitTrueGuard && explicitTrueFlippedOnce > explicitTrueFlip);
        assertTrue("Final flipped-at-least-once state must be captured after all pass and explicit flips",
            finalSnapshot > explicitTrueFlippedOnce);
    }

    @Test
    public void compositeRenderAllStillRunsCleanupWhenPassListIsEmpty() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");

        assertFalse(composite.contains("destroyed || passes.isEmpty()"));
        assertFalse(composite.contains("passes.isEmpty())"));

        int guard = composite.indexOf("if (destroyed) {");
        int begin = composite.indexOf("FullScreenQuadRenderer.INSTANCE.begin();", guard);
        int loop = composite.indexOf("for (Pass pass : passes)", begin);
        int end = composite.indexOf("FullScreenQuadRenderer.INSTANCE.end();", loop);
        int bindMain = composite.indexOf("mainFramebuffer.bindFramebuffer(true)", end);
        int clearUniforms = composite.indexOf("ProgramUniforms::clearActiveUniforms", bindMain);
        int clearSamplers = composite.indexOf("ProgramSamplers::clearActiveSamplers", clearUniforms);
        int clearImages = composite.indexOf("ProgramImages::clearActiveImages", clearSamplers);
        int useZero = composite.indexOf("GL20.glUseProgram(0)", clearImages);

        assertTrue(guard >= 0);
        assertTrue(begin > guard);
        assertTrue(loop > begin);
        assertTrue(end > loop);
        assertTrue(bindMain > end);
        assertTrue(clearUniforms > bindMain);
        assertTrue(clearSamplers > clearUniforms);
        assertTrue(clearImages > clearSamplers);
        assertTrue(useZero > clearImages);
    }

    @Test
    public void compositeValidationDumpsBracketEachPostprocessPassOutput() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String constructorBody = between(composite,
            "public CompositeRenderer(PackDirectives packDirectives,",
            "public ImmutableSet<Integer> getFlippedAtLeastOnceFinal()");
        String createPass = between(composite, "private Pass createPass(", "private void bindCenterDepthSmooth");
        String renderAll = between(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll");
        String dumpMethod = methodBody(composite, "private void dumpValidationRenderTargetsAfterPass(Pass pass)");

        assertTrue(composite.contains("import net.oculus.client.OculusRuntimeValidation;"));
        assertTrue(composite.contains(
            "private static final int[] VALIDATION_DUMP_BUFFERS = new int[] {0, 2, 3, 4, 5, 6, 7};"));
        assertTrue(createPass.contains("pass.index = index;"));
        assertTrue(createPass.contains("pass.name = source.getName();"));
        assertTrue(createPass.contains("pass.stageReadsAfter = stageReadsFromAlt;"));

        int createRasterPass = constructorBody.indexOf(
            "Pass pass = createPass(source, i, computeSources, stageReadsFromAlt, flippedAtLeastOnceSnapshot);");
        int explicitTrueLoop = constructorBody.indexOf("explicitFlips.forEach((buffer, shouldFlip) ->",
            createRasterPass);
        int stageReadsAfter = constructorBody.indexOf("pass.stageReadsAfter = bufferFlipper.snapshot();",
            explicitTrueLoop);
        assertTrue("Composite validation must snapshot the read side after draw-buffer and explicit flips",
            createRasterPass >= 0 && explicitTrueLoop > createRasterPass && stageReadsAfter > explicitTrueLoop);

        int computeOnlyPass = constructorBody.indexOf("ComputeOnlyPass pass = new ComputeOnlyPass();");
        int computeOnlyName = constructorBody.indexOf("pass.name = source == null ? \"compute-only\" : source.getName();",
            computeOnlyPass);
        int computeOnlyStageAfter = constructorBody.indexOf("pass.stageReadsAfter = stageReadsFromAlt;",
            computeOnlyName);
        assertTrue("Compute-only validation dumps must retain the current read side",
            computeOnlyPass >= 0 && computeOnlyName > computeOnlyPass && computeOnlyStageAfter > computeOnlyName);

        int passLoop = renderAll.indexOf("for (Pass pass : passes)");
        int computeOnlyGuard = renderAll.indexOf("if (pass instanceof ComputeOnlyPass)", passLoop);
        int ranComputeGuard = renderAll.indexOf("if (ranCompute)", computeOnlyGuard);
        int computeOnlyDump = renderAll.indexOf("dumpValidationRenderTargetsAfterPass(pass);", ranComputeGuard);
        int computeOnlyContinue = renderAll.indexOf("continue;", computeOnlyDump);
        int renderPass = renderAll.indexOf("renderPass(pass);", computeOnlyContinue);
        int rasterDump = renderAll.indexOf("dumpValidationRenderTargetsAfterPass(pass);", renderPass);
        assertTrue("Composite validation must dump real compute-only and raster pass outputs immediately after work",
            passLoop >= 0 && computeOnlyGuard > passLoop && ranComputeGuard > computeOnlyGuard
                && computeOnlyDump > ranComputeGuard && computeOnlyContinue > computeOnlyDump
                && renderPass > computeOnlyContinue && rasterDump > renderPass);

        assertTrue(dumpMethod.contains("OculusRuntimeValidation.isRenderTargetDumpEnabled()"));
        assertTrue(dumpMethod.contains("pass.stageReadsAfter == null"));
        assertTrue(dumpMethod.contains("for (int bufferIndex : VALIDATION_DUMP_BUFFERS)"));
        assertTrue(dumpMethod.contains("boolean readsAlt = readBuffers != null && readBuffers.contains(bufferIndex);"));
        assertTrue(dumpMethod.contains("OculusRuntimeValidation.dumpRenderTargetTexture("));
        assertTrue(dumpMethod.contains("\"postprocess-pass-\" + pass.index + \"-\" + passName"));
    }

    @Test
    public void compositeRenderAllFailsClearlyAfterDestroyInsteadOfSilentlySkipping() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String renderAll = between(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll");

        int guard = renderAll.indexOf("if (destroyed)");
        int failClearly = renderAll.indexOf(
            "throw new IllegalStateException(\"Cannot render a destroyed composite renderer\");", guard);
        int saveBlend = renderAll.indexOf("boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);",
            failClearly);

        assertTrue("Destroyed composite rendering must fail clearly instead of becoming a silent no-op",
            guard >= 0 && failClearly > guard && saveBlend > failClearly);
        assertFalse(renderAll.contains("if (destroyed) {\n            return;\n        }"));
    }

    @Test
    public void compositeResizeFailsClearlyAfterDestroyInsteadOfSilentlySkipping() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String resize = between(composite, "public void recalculateSizes()", "private void destroyResizeReplacements");

        int guard = resize.indexOf("if (destroyed)");
        int failClearly = resize.indexOf(
            "throw new IllegalStateException(\"Cannot resize a destroyed composite renderer\");", guard);
        int replacements = resize.indexOf("List<ResizeReplacement> replacements = new ArrayList<>();",
            failClearly);

        assertTrue("Destroyed composite resize must fail clearly before staging replacement framebuffers",
            guard >= 0 && failClearly > guard && replacements > failClearly);
        assertFalse("Destroyed composite resize must not silently no-op",
            resize.contains("if (destroyed) {\n            return;\n        }"));
    }

    @Test
    public void compositeRenderAllRestoresFullscreenAndSamplerStateFromFinally() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String renderAll = between(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll");
        String restore = between(composite, "private Throwable restoreAfterRenderAll", "private boolean dispatchComputes");

        int guard = renderAll.indexOf("if (destroyed) {");
        int flag = renderAll.indexOf("boolean fullscreenQuadBegun = false;", guard);
        int tryBlock = renderAll.indexOf("try {", flag);
        int begin = renderAll.indexOf("FullScreenQuadRenderer.INSTANCE.begin();", tryBlock);
        int markBegun = renderAll.indexOf("fullscreenQuadBegun = true;", begin);
        int loop = renderAll.indexOf("for (Pass pass : passes)", markBegun);
        int normalEnd = renderAll.indexOf("FullScreenQuadRenderer.INSTANCE.end();", loop);
        int markEnded = renderAll.indexOf("fullscreenQuadBegun = false;", normalEnd);
        int finallyBlock = renderAll.indexOf("finally {", markEnded);
        int guardedEnd = renderAll.indexOf("FullScreenQuadRenderer.INSTANCE.end();", finallyBlock);
        int restoreCall = renderAll.indexOf(
            "cleanupFailure = restoreAfterRenderAll(cleanupFailure, previousActiveTexture, blendWasEnabled,",
            guardedEnd);

        assertTrue(guard >= 0);
        assertTrue(flag > guard);
        assertTrue(tryBlock > flag);
        assertTrue(begin > tryBlock);
        assertTrue(markBegun > begin);
        assertTrue(loop > markBegun);
        assertTrue(normalEnd > loop);
        assertTrue(markEnded > normalEnd);
        assertTrue(finallyBlock > markEnded);
        assertTrue(guardedEnd > finallyBlock);
        assertTrue(restoreCall > guardedEnd);
        assertTrue(restore.contains("mainFramebuffer.bindFramebuffer(true)"));
        assertTrue("Composite cleanup fallback must use the read/draw-aware framebuffer restoration shim",
            restore.contains("OculusRenderSystem.restoreFramebufferBindings(0, 0, 0)"));
        assertFalse("Composite cleanup must not bypass OculusRenderSystem framebuffer backend dispatch",
            restore.contains("OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0)"));
        assertTrue(restore.contains("ProgramUniforms::clearActiveUniforms"));
        assertTrue(restore.contains("ProgramSamplers::clearActiveSamplers"));
        assertTrue(restore.contains("ProgramImages::clearActiveImages"));
        assertTrue(restore.contains("GL20.glUseProgram(0)"));
        assertTrue(restore.contains("OculusRenderSystem.unbindTexture2DFromUnits(SamplerLimits.get().getMaxTextureUnits())"));
        assertTrue(restore.contains("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)"));
    }

    @Test
    public void compositeRenderAllPreservesPrimaryFailureWhenCleanupFails() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String renderAll = between(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll");

        int failureLocal = renderAll.indexOf("Throwable failure = null;");
        int primaryCatch = renderAll.indexOf("} catch (RuntimeException | Error exception) {", failureLocal);
        int recordPrimary = renderAll.indexOf("failure = exception;", primaryCatch);
        int rethrowPrimary = renderAll.indexOf("throw exception;", recordPrimary);
        int finallyBlock = renderAll.indexOf("finally {", rethrowPrimary);
        int cleanupFailure = renderAll.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int fullscreenCleanup = renderAll.indexOf("FullScreenQuadRenderer.INSTANCE.end();", cleanupFailure);
        int fullscreenCatch = renderAll.indexOf("cleanupFailure = addCleanupFailure(cleanupFailure, exception);",
            fullscreenCleanup);
        int restoreCall = renderAll.indexOf(
            "cleanupFailure = restoreAfterRenderAll(cleanupFailure, previousActiveTexture, blendWasEnabled,",
            fullscreenCatch);
        int suppress = renderAll.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", restoreCall);
        int rethrowCleanupOnly = renderAll.indexOf("rethrowCleanupFailure(cleanupFailure);", suppress);

        assertTrue("Composite render must record the primary render failure before cleanup",
            failureLocal >= 0 && primaryCatch > failureLocal && recordPrimary > primaryCatch
                && rethrowPrimary > recordPrimary);
        assertTrue("Composite render cleanup must aggregate fullscreen and restore failures",
            finallyBlock > rethrowPrimary && cleanupFailure > finallyBlock && fullscreenCleanup > cleanupFailure
                && fullscreenCatch > fullscreenCleanup && restoreCall > fullscreenCatch);
        assertTrue("Composite render cleanup failures must suppress onto primary failures or rethrow alone",
            suppress > restoreCall && rethrowCleanupOnly > suppress);
    }

    @Test
    public void compositeRenderAllRestoresCallerOwnedBlendAlphaAndActiveTextureStateFromFinally() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String renderAll = between(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll");
        String restore = between(composite, "private Throwable restoreAfterRenderAll", "private boolean dispatchComputes");

        int saveBlend = renderAll.indexOf("boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);");
        int saveAlpha = renderAll.indexOf("boolean alphaWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);",
            saveBlend);
        int saveActiveTexture = renderAll.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);",
            saveAlpha);
        int failureLocal = renderAll.indexOf("Throwable failure = null;", saveActiveTexture);
        int tryBlock = renderAll.indexOf("try {", failureLocal);
        int disableBlend = renderAll.indexOf("GlStateManager.disableBlend();", tryBlock);
        int disableAlpha = renderAll.indexOf("GlStateManager.disableAlpha();", disableBlend);
        int finallyBlock = renderAll.indexOf("finally {", disableAlpha);
        int restoreCall = renderAll.indexOf(
            "cleanupFailure = restoreAfterRenderAll(cleanupFailure, previousActiveTexture, blendWasEnabled,",
            finallyBlock);

        assertTrue(saveBlend >= 0);
        assertTrue(saveAlpha > saveBlend);
        assertTrue("Composite render must save caller-owned active texture state before mutating samplers",
            saveActiveTexture > saveAlpha);
        assertTrue("Composite render must enter the cleanup-guarded block before mutating blend/alpha state",
            failureLocal > saveActiveTexture && tryBlock > failureLocal
                && disableBlend > tryBlock && disableAlpha > disableBlend);
        assertTrue("Composite render draw-state restoration must run from the cleanup path",
            restoreCall > finallyBlock);

        int clearImages = restore.indexOf("failure = runCleanup(failure, ProgramImages::clearActiveImages);");
        int clearProgram = restore.indexOf("failure = runCleanup(failure, () -> GL20.glUseProgram(0));",
            clearImages);
        int unbindTextures = restore.indexOf(
            "OculusRenderSystem.unbindTexture2DFromUnits(SamplerLimits.get().getMaxTextureUnits())",
            clearProgram);
        int restoreActiveTexture = restore.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)",
            unbindTextures);
        int restoreBlend = restore.indexOf("restoreBlendState(blendWasEnabled)",
            restoreActiveTexture);
        int restoreAlpha = restore.indexOf("restoreAlphaState(alphaWasEnabled)", restoreBlend);
        int enableBlend = restore.indexOf("GlStateManager.enableBlend();", restoreAlpha);
        int disableBlendRestore = restore.indexOf("GlStateManager.disableBlend();", enableBlend);
        int enableAlpha = restore.indexOf("GlStateManager.enableAlpha();", disableBlendRestore);
        int disableAlphaRestore = restore.indexOf("GlStateManager.disableAlpha();", enableAlpha);

        assertTrue("Blend/alpha restore should run even if image cleanup fails", clearImages >= 0);
        assertTrue(clearProgram > clearImages);
        assertTrue(unbindTextures > clearProgram);
        assertTrue("Composite cleanup must restore the caller active texture unit before draw-state restore",
            restoreActiveTexture > unbindTextures && restoreBlend > restoreActiveTexture
                && restoreAlpha > restoreBlend);
        assertTrue(enableBlend > restoreAlpha);
        assertTrue(disableBlendRestore > enableBlend);
        assertTrue(enableAlpha > disableBlendRestore);
        assertTrue(disableAlphaRestore > enableAlpha);
    }

    @Test
    public void compositeRenderAllForcesAndRestoresColorMaskFromFinally() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String renderAll = between(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll");
        String restore = between(composite, "private Throwable restoreAfterRenderAll", "private boolean dispatchComputes");
        String restoreColorMask = composite.substring(
            composite.indexOf("private static void restoreColorMask"),
            composite.indexOf("private boolean dispatchComputes"));

        int saveActiveTexture = renderAll.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int allocateColorMask = renderAll.indexOf("ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);",
            saveActiveTexture);
        int readColorMask = renderAll.indexOf("GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);",
            allocateColorMask);
        int tryBlock = renderAll.indexOf("try {", readColorMask);
        int disableBlend = renderAll.indexOf("GlStateManager.disableBlend();", tryBlock);
        int disableAlpha = renderAll.indexOf("GlStateManager.disableAlpha();", disableBlend);
        int forceColorMask = renderAll.indexOf("GlStateManager.colorMask(true, true, true, true);", disableAlpha);
        int finallyBlock = renderAll.indexOf("finally {", forceColorMask);
        int restoreCall = renderAll.indexOf(
            "cleanupFailure = restoreAfterRenderAll(cleanupFailure, previousActiveTexture, blendWasEnabled,",
            finallyBlock);
        int restoreColorMaskArg = renderAll.indexOf("previousColorMask", restoreCall);

        assertTrue("Composite render must allocate color-mask storage after active texture capture",
            allocateColorMask > saveActiveTexture);
        assertTrue("Composite render must read the caller color mask before mutating draw state",
            readColorMask > allocateColorMask);
        assertTrue("Composite fullscreen output must force all color channels writable inside cleanup guard",
            tryBlock > readColorMask && forceColorMask > disableAlpha);
        assertTrue("Composite cleanup must receive the saved color mask from the finally path",
            restoreCall > finallyBlock && restoreColorMaskArg > restoreCall);

        int restoreActiveTexture = restore.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)");
        int restoreBlend = restore.indexOf("restoreBlendState(blendWasEnabled)", restoreActiveTexture);
        int restoreAlpha = restore.indexOf("restoreAlphaState(alphaWasEnabled)", restoreBlend);
        int restoreColorMaskCall = restore.indexOf("restoreColorMask(previousColorMask)", restoreAlpha);

        assertTrue("Composite cleanup must restore the color mask even if draw-state restore fails",
            restoreColorMaskCall > restoreAlpha);
        assertTrue(restoreColorMask.contains("previousColorMask.get(0) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(1) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(2) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(3) != 0"));
    }

    @Test
    public void compositePassTargetsFailFastInsteadOfSkippingMissingTargets() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");

        assertTrue(composite.contains(
            "RenderTarget target = requireRenderTarget(buffer, \"Postprocess pass \" + source.getName());"));
        String createPassBody = between(composite, "private Pass createPass(", "private void bindCenterDepthSmooth");
        int drawBuffers = createPassBody.indexOf("int[] drawBuffers = directives.getDrawBuffers();");
        int viewWidth = createPassBody.indexOf("int viewWidth = 0;", drawBuffers);
        assertTrue(drawBuffers >= 0);
        assertTrue(viewWidth > drawBuffers);
        assertTrue(composite.contains("RenderTarget target = requireRenderTarget(buffer, \"Postprocess resize\");"));
        assertTrue(composite.contains(
            "setupMipmapping(renderTargets.get(index), index, pass.stageReadsFromAlt.contains(index));"));
        assertTrue(composite.contains(
            "throw new IllegalStateException(\"Mipmapped render target colortex\" + index + \" is not configured\");"));
        String resizeBody = between(composite, "public void recalculateSizes()", "public void destroy()");
        int replacements = resizeBody.indexOf("List<ResizeReplacement> replacements = new ArrayList<>();");
        int pendingReplacement = resizeBody.indexOf("GlFramebuffer pendingReplacementFramebuffer = null;", replacements);
        int tryBlock = resizeBody.indexOf("try {", replacements);
        int createReplacement = resizeBody.indexOf("GlFramebuffer replacementFramebuffer =", tryBlock);
        int trackPending = resizeBody.indexOf("pendingReplacementFramebuffer = replacementFramebuffer;",
            createReplacement);
        int addReplacement = resizeBody.indexOf(
            "replacements.add(new ResizeReplacement(pass, replacementFramebuffer, viewWidth, viewHeight));",
            createReplacement);
        int clearPending = resizeBody.indexOf("pendingReplacementFramebuffer = null;", addReplacement);
        int cleanupCatch = resizeBody.indexOf("catch (RuntimeException | Error exception)", clearPending);
        int destroyPending = resizeBody.indexOf(
            "Throwable failure = destroyFramebuffer(null, pendingReplacementFramebuffer);", cleanupCatch);
        int destroyReplacements = resizeBody.indexOf(
            "failure = destroyResizeReplacements(failure, replacements);", destroyPending);
        int suppressCleanup = resizeBody.indexOf("addSuppressedCleanupFailure(exception, failure);", destroyReplacements);
        int rethrow = resizeBody.indexOf("throw exception;", suppressCleanup);
        int previousList = resizeBody.indexOf("List<GlFramebuffer> previousFramebuffers = new ArrayList<>();",
            rethrow);
        int collectLoop = resizeBody.indexOf("for (ResizeReplacement replacement : replacements)", previousList);
        int recordPrevious = resizeBody.indexOf("previousFramebuffers.add(replacement.pass.framebuffer);", collectLoop);
        int destroyPrevious = resizeBody.indexOf(
            "rethrowCleanupFailure(destroyFramebuffers(null, previousFramebuffers));", recordPrevious);
        int commitLoop = resizeBody.indexOf("for (ResizeReplacement replacement : replacements)", recordPrevious);
        int assignReplacement = resizeBody.indexOf("pass.framebuffer = replacement.framebuffer;", commitLoop);
        int clearReplacement = resizeBody.indexOf("replacement.framebuffer = null;", assignReplacement);
        int assignViewWidth = resizeBody.indexOf("pass.viewWidth = replacement.viewWidth;", clearReplacement);
        int assignViewHeight = resizeBody.indexOf("pass.viewHeight = replacement.viewHeight;", assignViewWidth);

        assertTrue("Resize must stage replacement framebuffers before mutating pass state", replacements >= 0);
        assertTrue(pendingReplacement > replacements && tryBlock > pendingReplacement);
        assertTrue(createReplacement > tryBlock && trackPending > createReplacement && addReplacement > trackPending
            && clearPending > addReplacement);
        assertTrue("All replacement framebuffers must be created before the resize commit phase",
            cleanupCatch > clearPending && previousList > rethrow && collectLoop > previousList);
        assertTrue("Unpublished replacement framebuffers must be destroyed before staged replacements",
            destroyPending > cleanupCatch && destroyReplacements > destroyPending);
        assertTrue("Failed resize staging must destroy any replacement framebuffers it created",
            cleanupCatch > clearPending && destroyReplacements > cleanupCatch);
        assertTrue("Resize replacement cleanup failures must be suppressed onto the resize failure",
            suppressCleanup > destroyReplacements && rethrow > suppressCleanup);
        assertTrue("Previous framebuffers must be collected before replacement framebuffers publish",
            recordPrevious > collectLoop);
        assertTrue("Pass framebuffer and viewport state must update before old framebuffer cleanup can fail",
            commitLoop > recordPrevious && assignReplacement > commitLoop && clearReplacement > assignReplacement
                && assignViewWidth > clearReplacement && assignViewHeight > assignViewWidth);
        assertTrue("Old framebuffer cleanup must run after replacement state is live",
            destroyPrevious > assignViewHeight);
        assertTrue(resizeBody.contains("private void destroyResizeReplacements(List<ResizeReplacement> replacements)"));
        assertTrue(resizeBody.contains("private Throwable destroyResizeReplacements(Throwable failure, List<ResizeReplacement> replacements)"));
        assertTrue(resizeBody.contains("failure = destroyFramebuffer(failure, replacement.framebuffer);"));
        assertTrue(resizeBody.contains("private Throwable destroyFramebuffers(Throwable failure, List<GlFramebuffer> framebuffers)"));
        assertTrue(resizeBody.contains("failure = destroyFramebuffer(failure, framebuffer);"));
        assertTrue(resizeBody.contains("private static final class ResizeReplacement"));
        assertFalse(composite.contains(
            "if (target == null) {\n                continue;\n            }\n            if ((viewWidth > 0"));
        assertFalse(composite.contains("public void recalculateSizesOld()"));
        assertFalse(composite.contains("drawBuffers == null || drawBuffers.length == 0"));
        assertFalse(composite.contains("viewWidth = mc == null ? renderTargets.getWidth()"));
        assertFalse(composite.contains(
            "if (target == null) {\n            return;\n        }\n\n        int texture = readFromAlt"));
    }

    @Test
    public void compositeConstructorDestroysCreatedPassesWhenLaterPassCreationFails() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String constructorBody = between(composite,
            "public CompositeRenderer(PackDirectives packDirectives,",
            "public ImmutableSet<Integer> getFlippedAtLeastOnceFinal()");
        String destroyCreatedPasses = methodBody(composite, "private void destroyCreatedPasses()");
        String destroyPendingAndCreatedPasses = methodBody(composite,
            "private void destroyPendingAndCreatedPasses(Pass pendingPass)");

        int pendingLocal = constructorBody.indexOf("Pass pendingPass = null;");
        int tryBlock = constructorBody.indexOf("try {", pendingLocal);
        int computeOnlyPass = constructorBody.indexOf("ComputeOnlyPass pass = new ComputeOnlyPass();", tryBlock);
        int trackComputeOnly = constructorBody.indexOf("pendingPass = pass;", computeOnlyPass);
        int addComputeOnly = constructorBody.indexOf("passes.add(pass);", trackComputeOnly);
        int clearComputeOnly = constructorBody.indexOf("pendingPass = null;", addComputeOnly);
        int createPass = constructorBody.indexOf(
            "Pass pass = createPass(source, i, computeSources, stageReadsFromAlt, flippedAtLeastOnceSnapshot);",
            tryBlock);
        int trackPass = constructorBody.indexOf("pendingPass = pass;", createPass);
        int addPass = constructorBody.indexOf("passes.add(pass);", trackPass);
        int clearPass = constructorBody.indexOf("pendingPass = null;", addPass);
        int cleanup = constructorBody.indexOf("} catch (RuntimeException | Error exception) {", clearPass);
        int destroyCreated = constructorBody.indexOf("destroyPendingAndCreatedPasses(pendingPass);", cleanup);
        int cleanupCatch = constructorBody.indexOf("catch (RuntimeException | Error cleanupException)", destroyCreated);
        int suppressed = constructorBody.indexOf("exception.addSuppressed(cleanupException);", cleanupCatch);
        int rethrow = constructorBody.indexOf("throw exception;", suppressed);

        assertTrue(pendingLocal >= 0);
        assertTrue(tryBlock > pendingLocal);
        assertTrue("Compute-only passes must be tracked before resources are attached and list-owned",
            computeOnlyPass > tryBlock && trackComputeOnly > computeOnlyPass && addComputeOnly > trackComputeOnly
                && clearComputeOnly > addComputeOnly);
        assertTrue(createPass > tryBlock);
        assertTrue("Raster passes must be tracked until list ownership begins",
            trackPass > createPass && addPass > trackPass && clearPass > addPass);
        assertTrue(cleanup > clearPass);
        assertTrue(destroyCreated > cleanup);
        assertTrue(cleanupCatch > destroyCreated);
        assertTrue(suppressed > cleanupCatch);
        assertTrue(rethrow > suppressed);
        assertTrue(composite.contains("private void destroyCreatedPasses()"));
        assertTrue(destroyCreatedPasses.contains("destroyPendingAndCreatedPasses(null);"));

        int destroyFailure = destroyPendingAndCreatedPasses.indexOf("Throwable failure = null;");
        int destroyPending = destroyPendingAndCreatedPasses.indexOf(
            "failure = destroyPass(failure, pendingPass, renderTargets);", destroyFailure);
        int destroyLoop = destroyPendingAndCreatedPasses.indexOf("for (Pass pass : passes)", destroyPending);
        int destroyPass = destroyPendingAndCreatedPasses.indexOf(
            "failure = destroyPass(failure, pass, renderTargets);", destroyLoop);
        int rethrowCleanup = destroyPendingAndCreatedPasses.indexOf("rethrowCleanupFailure(failure);", destroyPass);
        int finallyBlock = destroyPendingAndCreatedPasses.indexOf("} finally {", rethrowCleanup);
        int clearPasses = destroyPendingAndCreatedPasses.indexOf("passes.clear();", finallyBlock);

        assertTrue("Constructor-failure cleanup must attempt all passes before rethrowing cleanup failures",
            destroyFailure >= 0 && destroyPending > destroyFailure && destroyLoop > destroyPending
                && destroyPass > destroyLoop && rethrowCleanup > destroyPass);
        assertTrue("Constructor-failure cleanup must drop stale pass handles even when cleanup reports a failure",
            finallyBlock > rethrowCleanup && clearPasses > finallyBlock);
    }

    @Test
    public void compositeCreatePassCleansProgramComputesAndFramebufferOnFailure() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String createPass = between(composite, "private Pass createPass(", "private void bindCenterDepthSmooth");

        int programLocal = createPass.indexOf("Program program = null;");
        int computesLocal = createPass.indexOf("ComputeProgram[] compiledComputes = new ComputeProgram[0];", programLocal);
        int framebufferLocal = createPass.indexOf("GlFramebuffer framebuffer = null;", computesLocal);
        int tryBlock = createPass.indexOf("try {", framebufferLocal);
        int buildProgram = createPass.indexOf("program = builder.build();", tryBlock);
        int createComputes = createPass.indexOf("compiledComputes = createComputes", buildProgram);
        int createFramebuffer = createPass.indexOf("framebuffer = renderTargets.createColorFramebuffer", createComputes);
        int markComplete = createPass.indexOf("complete = true;", createFramebuffer);
        int cleanupCatch = createPass.indexOf("catch (RuntimeException | Error exception)", markComplete);
        int failureLocal = createPass.indexOf("Throwable failure = null;", cleanupCatch);
        int destroyProgram = createPass.indexOf("failure = destroyProgram(failure, program);", failureLocal);
        int destroyComputes = createPass.indexOf("failure = destroyComputePrograms(failure, compiledComputes);",
            destroyProgram);
        int destroyFramebuffer = createPass.indexOf("failure = destroyFramebuffer(failure, framebuffer);",
            destroyComputes);
        int suppressed = createPass.indexOf("addSuppressedCleanupFailure(exception, failure);", destroyFramebuffer);
        int rethrow = createPass.indexOf("throw exception;", suppressed);

        assertTrue(programLocal >= 0);
        assertTrue(computesLocal > programLocal);
        assertTrue(framebufferLocal > computesLocal);
        assertTrue(tryBlock > framebufferLocal);
        assertTrue(buildProgram > tryBlock);
        assertTrue("Composite pass creation must match 1.16.5 order: raster program, computes, then framebuffer",
            createComputes > buildProgram && createFramebuffer > createComputes);
        assertTrue(markComplete > createFramebuffer);
        assertTrue(cleanupCatch > markComplete);
        assertTrue(failureLocal > cleanupCatch);
        assertTrue(destroyProgram > failureLocal);
        assertTrue(destroyComputes > destroyProgram);
        assertTrue(destroyFramebuffer > destroyComputes);
        assertTrue(suppressed > destroyFramebuffer);
        assertTrue(rethrow > suppressed);
    }

    @Test
    public void compositeDestroyAggregatesCleanupFailuresWithoutSkippingLaterResources() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String destroy = methodBody(composite, "public void destroy()");
        String passDestroy = between(composite, "void destroy(RenderTargets renderTargets)", "void destroyComputes()");
        String helpers = composite.substring(composite.indexOf("private static Throwable destroyComputePrograms"));

        int tryBlock = destroy.indexOf("try {");
        int topFailure = destroy.indexOf("Throwable failure = null;", tryBlock);
        int loop = destroy.indexOf("for (Pass pass : passes)", topFailure);
        int destroyPass = destroy.indexOf("failure = destroyPass(failure, pass, renderTargets);", loop);
        int rethrowTop = destroy.indexOf("rethrowCleanupFailure(failure);", destroyPass);
        int finallyBlock = destroy.indexOf("} finally {", rethrowTop);
        int clearPasses = destroy.indexOf("passes.clear();", finallyBlock);
        int markDestroyed = destroy.indexOf("destroyed = true;", clearPasses);

        int passTry = passDestroy.indexOf("try {");
        int passFailure = passDestroy.indexOf("Throwable failure = null;", passTry);
        int program = passDestroy.indexOf("failure = destroyProgram(failure, program);", passFailure);
        int computes = passDestroy.indexOf("failure = destroyComputes(failure);", program);
        int framebuffer = passDestroy.indexOf("failure = destroyFramebuffer(failure, framebuffer, renderTargets);",
            computes);
        int rethrowPass = passDestroy.indexOf("rethrowCleanupFailure(failure);", framebuffer);
        int passFinally = passDestroy.indexOf("} finally {", rethrowPass);
        int programNull = passDestroy.indexOf("program = null;", passFinally);
        int computesNull = passDestroy.indexOf("computes = new ComputeProgram[0];", programNull);
        int framebufferNull = passDestroy.indexOf("framebuffer = null;", computesNull);

        assertTrue(tryBlock >= 0 && topFailure > tryBlock);
        assertTrue(loop > topFailure && destroyPass > loop);
        assertTrue("Composite destroy must rethrow aggregated cleanup failures after later resources are attempted",
            rethrowTop > destroyPass);
        assertTrue("Composite destroy must clear pass ownership and mark destroyed even when cleanup fails",
            finallyBlock > rethrowTop && clearPasses > finallyBlock && markDestroyed > clearPasses);
        assertTrue(passTry >= 0 && passFailure > passTry);
        assertTrue("Pass destroy must attempt program, compute, and framebuffer cleanup in order",
            program > passFailure && computes > program && framebuffer > computes);
        assertTrue("Pass destroy must drop stale handles even when cleanup reports a failure",
            rethrowPass > framebuffer && passFinally > rethrowPass && programNull > passFinally
                && computesNull > programNull && framebufferNull > computesNull);
        assertTrue(helpers.contains("failure.addSuppressed(exception);"));
        assertTrue(helpers.contains("primary.addSuppressed(cleanupFailure);"));
        assertTrue(helpers.contains("throw (RuntimeException) failure;"));
        assertTrue(helpers.contains("throw (Error) failure;"));
    }

    @Test
    public void compositeComputeCompilationDestroysPartialProgramsOnFailure() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String createComputes = between(composite,
            "private ComputeProgram[] createComputes(",
            "private void overrideRenderTargetSamplerBindings");

        int programsArray = createComputes.indexOf("ComputeProgram[] programs = new ComputeProgram[computes.length];");
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

    @Test
    public void renderTargetMipmapsRouteThroughReferenceStyleRenderSystemWrappers() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String renderSystem = read("src/main/java/net/oculus/gl/OculusRenderSystem.java");
        String finalResetTarget = between(finalPass, "private static void resetRenderTarget", "private static final class SwapPass");

        assertTrue(renderSystem.contains("public static void generateMipmaps(int texture, int target)"));
        assertTrue(renderSystem.contains("public static void texParameteri(int texture, int target, int pname, int param)"));
        assertTrue(renderSystem.contains("public static void copyTexSubImage2D(int destinationTexture"));
        assertTrue(renderSystem.contains("GlStateManager.bindTexture(texture);"));
        assertTrue(renderSystem.contains("public static void withDefaultTextureBindingRestored(Runnable operation)"));

        assertTrue(composite.contains("OculusRenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);"));
        assertTrue(composite.contains(
            "OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);"));
        assertTrue(composite.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        assertTrue(finalPass.contains("OculusRenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);"));
        assertTrue(finalPass.contains(
            "OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);"));
        assertTrue(finalPass.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        assertTrue(finalResetTarget.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        int finalResetWrapper = finalResetTarget.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {");
        int finalResetMainFilter = finalResetTarget.indexOf("OculusRenderSystem.texParameteri(target.getMainTexture(),",
            finalResetWrapper);
        int finalResetAltFilter = finalResetTarget.indexOf("OculusRenderSystem.texParameteri(target.getAltTexture(),",
            finalResetMainFilter);
        int finalResetUnbind = finalResetTarget.indexOf("OculusRenderSystem.bindTexture2DToUnit(0, 0);",
            finalResetAltFilter);
        assertTrue("Final render-target mipmap reset must leave the default texture unit unbound before swap copyback",
            finalResetWrapper >= 0 && finalResetMainFilter > finalResetWrapper
                && finalResetAltFilter > finalResetMainFilter && finalResetUnbind > finalResetAltFilter);
        assertTrue(finalPass.contains("OculusRenderSystem.copyTexSubImage2D("));

        assertFalse(composite.contains("GL30.glGenerateMipmap"));
        assertFalse(finalPass.contains("GL30.glGenerateMipmap"));
        assertFalse(composite.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));
        assertFalse(finalPass.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));
        assertFalse(finalPass.contains("GlStateManager.bindTexture(0);"));
    }

    @Test
    public void postprocessRenderersUseSharedSourcePreparerWithoutSecondTextPatcher() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String preparer = read("src/main/java/net/oculus/shader/ShaderSourcePreparer.java");
        String patcher = read("src/main/java/net/oculus/shader/ShaderCompatibilityPatcher.java");

        assertTrue(composite.contains("ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram("));
        assertTrue(finalPass.contains("ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram("));
        assertTrue(composite.contains("vertexSource = prepared.getVertexSource();"));
        assertTrue(finalPass.contains("geometrySource = prepared.getGeometrySource();"));
        assertTrue(preparer.contains("ShaderCompatibilityPatcher.patchGrouped("));
        assertTrue(patcher.contains("patchCenterDepthSmooth(packName, programName, patched);"));
        assertTrue(patcher.contains("patchCompositeTextureLodExtension(packName, programName, patched);"));

        assertFalse(composite.contains("patchCompositeShader"));
        assertFalse(finalPass.contains("patchFinalShader"));
        assertFalse(composite.contains("source.replace(\"in vec\""));
        assertFalse(finalPass.contains("source.replace(\"in vec\""));
        assertFalse(composite.contains("\"#version 120\\n\" + source"));
        assertFalse(finalPass.contains("\"#version 120\\n\" + source"));
    }

    @Test
    public void postprocessSamplerCleanupUsesTwelveSafeTextureUnitWrapper() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertTrue(composite.contains(
            "OculusRenderSystem.unbindTexture2DFromUnits(SamplerLimits.get().getMaxTextureUnits())"));
        assertTrue(finalPass.contains(
            "OculusRenderSystem.unbindTexture2DFromUnits(SamplerLimits.get().getMaxTextureUnits())"));
        assertTrue(composite.contains("OculusRenderSystem.restoreDefaultActiveTexture();"));
        assertTrue(finalPass.contains("OculusRenderSystem.restoreDefaultActiveTexture();"));

        assertFalse(composite.contains("GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);"));
        assertFalse(finalPass.contains("GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + i);"));
    }

    @Test
    public void postprocessComputeBarrierMatchesReferenceImageTextureBarrier() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");

        assertTrue(composite.contains("private static final int COMPUTE_BARRIER = GL42.GL_TEXTURE_FETCH_BARRIER_BIT\n"
            + "        | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;"));
        assertTrue(finalPass.contains("private static final int COMPUTE_BARRIER = GL42.GL_TEXTURE_FETCH_BARRIER_BIT\n"
            + "        | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;"));
        assertTrue(composite.contains("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);"));
        assertTrue(finalPass.contains("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);"));

        String compositeRenderAll = between(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll");
        int dispatch = compositeRenderAll.indexOf("boolean ranCompute = dispatchComputes(pass.computes);");
        int barrierGuard = compositeRenderAll.indexOf("if (ranCompute) {", dispatch);
        int barrier = compositeRenderAll.indexOf("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);", barrierGuard);
        int unbindProgram = compositeRenderAll.indexOf("Program.unbind();", barrier);

        assertTrue("Composite passes must issue the reference compute memory barrier only after a compute dispatch",
            dispatch >= 0 && barrierGuard > dispatch && barrier > barrierGuard && unbindProgram > barrier);
        assertTrue("Composite memory barrier must depend on the dispatch helper's ran-compute result like 1.16.5",
            compositeRenderAll.contains("if (ranCompute)"));
        assertTrue("Composite render must keep the dispatch result local to each pass",
            compositeRenderAll.contains("boolean ranCompute"));

        assertFalse(composite.contains("ARBShaderStorageBufferObject"));
        assertFalse(finalPass.contains("ARBShaderStorageBufferObject"));
        assertFalse(composite.contains("GL_SHADER_STORAGE_BARRIER_BIT"));
        assertFalse(finalPass.contains("GL_SHADER_STORAGE_BARRIER_BIT"));
    }

    @Test
    public void compositeComputeOnlyPassesStillBarrierUnbindAndSkipRasterWorkLikeReference() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String renderAll = between(composite, "public void renderAll()", "private Throwable restoreAfterRenderAll");

        int passLoop = renderAll.indexOf("for (Pass pass : passes)");
        int dispatch = renderAll.indexOf("boolean ranCompute = dispatchComputes(pass.computes);", passLoop);
        int barrierGuard = renderAll.indexOf("if (ranCompute)", dispatch);
        int barrier = renderAll.indexOf("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);", barrierGuard);
        int unbind = renderAll.indexOf("Program.unbind();", barrier);
        int computeOnlyGuard = renderAll.indexOf("if (pass instanceof ComputeOnlyPass)", unbind);
        int skipRaster = renderAll.indexOf("continue;", computeOnlyGuard);
        int renderPass = renderAll.indexOf("renderPass(pass);", skipRaster);

        assertTrue("Composite pass loop must dispatch compute programs before raster decisions",
            passLoop >= 0 && dispatch > passLoop);
        assertTrue("Composite compute passes must issue the image/texture barrier before unbinding active programs",
            barrierGuard > dispatch && barrier > barrierGuard && unbind > barrier);
        assertTrue("Compute-only composite passes must still clean program state before skipping raster work",
            computeOnlyGuard > unbind && skipRaster > computeOnlyGuard);
        assertTrue("Raster work must only run after compute-only passes have been skipped",
            renderPass > skipRaster);
    }

    @Test
    public void compositeComputeDispatchRequiresMinecraftMainFramebufferLikeReference() throws Exception {
        String composite = read("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String dispatch = between(composite, "private boolean dispatchComputes", "private void renderPass");
        String requireMainFramebuffer = methodBody(composite,
            "private static Framebuffer requireMainFramebuffer(Minecraft minecraft)");

        int emptyGuard = dispatch.indexOf("if (computes == null || computes.length == 0)");
        int ranCompute = dispatch.indexOf("boolean ranCompute = false;", emptyGuard);
        int widthDefault = dispatch.indexOf("int width = 0;", ranCompute);
        int heightDefault = dispatch.indexOf("int height = 0;", widthDefault);
        int loop = dispatch.indexOf("for (ComputeProgram compute : computes)", heightDefault);
        int nullSkip = dispatch.indexOf("if (compute == null)", loop);
        int continueNull = dispatch.indexOf("continue;", nullSkip);
        int firstRealCompute = dispatch.indexOf("if (!ranCompute)", continueNull);
        int require = dispatch.indexOf("Framebuffer mainFramebuffer = requireMainFramebuffer(Minecraft.getMinecraft());",
            firstRealCompute);
        int width = dispatch.indexOf("width = Math.max(1, mainFramebuffer.framebufferWidth);", require);
        int height = dispatch.indexOf("height = Math.max(1, mainFramebuffer.framebufferHeight);", width);
        int dispatchCall = dispatch.indexOf("compute.dispatch(width, height);", loop);
        int markRan = dispatch.indexOf("ranCompute = true;", dispatchCall);

        assertTrue("Composite compute dispatch must keep empty compute arrays as a no-op",
            emptyGuard >= 0 && ranCompute > emptyGuard);
        assertTrue("Composite compute dispatch must skip null compute slots before requiring Minecraft state",
            widthDefault > ranCompute && heightDefault > widthDefault && loop > heightDefault
                && nullSkip > loop && continueNull > nullSkip);
        assertTrue("Composite compute dispatch must source dimensions from the Minecraft main framebuffer only for a real compute",
            firstRealCompute > continueNull && require > firstRealCompute
                && width > require && height > width && dispatchCall > height && markRan > dispatchCall);
        assertTrue(requireMainFramebuffer.contains(
            "throw new IllegalStateException(\"Composite compute dispatch requires a Minecraft instance\");"));
        assertTrue(requireMainFramebuffer.contains(
            "throw new IllegalStateException(\"Composite compute dispatch requires a Minecraft main framebuffer\");"));
        assertFalse(composite.contains("getMainFramebufferWidth"));
        assertFalse(composite.contains("getMainFramebufferHeight"));
        assertFalse(dispatch.contains("renderTargets.getWidth()"));
        assertFalse(dispatch.contains("renderTargets.getHeight()"));
    }

    @Test
    public void compositeComputeDispatchIgnoresNullComputeSlotsWithoutMinecraftFramebuffer() throws Exception {
        CompositeRenderer renderer = allocate(CompositeRenderer.class);
        Method dispatchComputes = CompositeRenderer.class.getDeclaredMethod("dispatchComputes", ComputeProgram[].class);
        dispatchComputes.setAccessible(true);

        boolean ranCompute = (Boolean) dispatchComputes.invoke(
            renderer,
            new Object[] { new ComputeProgram[] { null, null } });

        assertFalse("Null composite compute slots must remain a no-op without requiring Minecraft framebuffer state",
            ranCompute);
    }

    @Test
    public void finalPassComputeDispatchRequiresMinecraftMainFramebufferLikeReference() throws Exception {
        String finalPass = read("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String render = between(finalPass, "public void render()", "private Throwable restoreAfterRender");
        String dispatch = methodBody(finalPass, "private void dispatchComputes");
        String requireMainFramebuffer = methodBody(finalPass,
            "private static Framebuffer requireMainFramebuffer(Minecraft minecraft)");

        int minecraft = render.indexOf("Minecraft minecraft = Minecraft.getMinecraft();");
        int require = render.indexOf("Framebuffer mainFramebuffer = requireMainFramebuffer(minecraft);", minecraft);
        int width = render.indexOf("int viewportWidth = Math.max(1, mainFramebuffer.framebufferWidth);", require);
        int height = render.indexOf("int viewportHeight = Math.max(1, mainFramebuffer.framebufferHeight);", width);
        int dispatchCall = render.indexOf("dispatchComputes(viewportWidth, viewportHeight);", height);
        int barrier = render.indexOf("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);", dispatchCall);

        assertTrue("Final pass compute dispatch must source dimensions from the Minecraft main framebuffer",
            minecraft >= 0 && require > minecraft && width > require && height > width);
        assertTrue("Final pass compute dispatch must run before the reference image/texture barrier",
            dispatchCall > height && barrier > dispatchCall);
        assertTrue(dispatch.contains("if (computes == null || computes.length == 0)"));
        assertTrue(dispatch.contains("compute.dispatch(width, height);"));
        assertTrue(requireMainFramebuffer.contains(
            "throw new IllegalStateException(\"Final pass requires a Minecraft instance\");"));
        assertTrue(requireMainFramebuffer.contains(
            "throw new IllegalStateException(\"Final pass requires a Minecraft main framebuffer\");"));
        assertFalse(finalPass.contains("getMainFramebufferWidth"));
        assertFalse(finalPass.contains("getMainFramebufferHeight"));
        assertFalse(finalPass.contains("renderTargets.getWidth()"));
        assertFalse(finalPass.contains("renderTargets.getHeight()"));
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        int end = source.indexOf("\n    }\n", start);
        assertTrue("Method was not found: " + signature, start >= 0);
        assertTrue("Method end was not found: " + signature, end > start);
        return source.substring(start, end);
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0);
        assertTrue(endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void assertPostprocessSamplerOrder(String source, String methodStart) {
        int start = source.indexOf(methodStart);
        int renderTargets = source.indexOf("overrideRenderTargetSamplerBindings(builder", start);
        int noise = source.indexOf("bindNoiseSampler(builder);", renderTargets);
        int compositeDepth = source.indexOf("IrisSamplers.addCompositeSamplerBindings(builder, renderTargets);", noise);
        int shadow = source.indexOf("bindShadowTargetSamplerBindings(builder", compositeDepth);
        int centerDepth = source.indexOf("bindCenterDepthSmooth(builder);", shadow);

        assertTrue("Method was not found: " + methodStart, start >= 0);
        assertTrue("Postprocess sampler setup must follow render targets, noise, composite depth, shadow, center-depth order",
            renderTargets > start && noise > renderTargets && compositeDepth > noise && shadow > compositeDepth
                && centerDepth > shadow);
    }

    private static void assertFogStateGuard(String source, String renderStart, String restoreStart) {
        String render = between(source, renderStart, restoreStart);
        String restore = methodBody(source, restoreStart);
        String restoreFog = methodBody(source, "private static void restoreFogState(boolean fogWasEnabled)");

        int capture = render.indexOf("boolean fogWasEnabled = GL11.glIsEnabled(GL11.GL_FOG);");
        int disable = render.indexOf("GlStateManager.disableFog();", capture);
        int restoreCall = restore.indexOf("restoreFogState(fogWasEnabled)");

        assertTrue("Postprocess renderer must capture fixed-function fog before fullscreen passes", capture >= 0);
        assertTrue("Postprocess renderer must disable fixed-function fog for fullscreen shader passes",
            disable > capture);
        assertTrue("Postprocess cleanup must restore the previous fixed-function fog state", restoreCall >= 0);
        assertTrue(restoreFog.contains("GlStateManager.enableFog();"));
        assertTrue(restoreFog.contains("GlStateManager.disableFog();"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
