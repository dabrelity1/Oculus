package net.oculus.rendertarget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RenderTargetsSourceTest {
    @Test
    public void depthCopyMatchesReferenceBindingBoundaries() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String strategy = read("src/main/java/net/oculus/rendertarget/DepthCopyStrategy.java");

        int method = source.indexOf("private void copyDepth(DepthTexture destination, GlFramebuffer destinationFramebuffer, boolean allocate)");
        int methodEnd = source.indexOf("public boolean isFullClearRequired()", method);
        String copyDepth = source.substring(method, methodEnd);

        int saveActiveTexture = copyDepth.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int saveFramebuffer = copyDepth.indexOf("int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();",
            saveActiveTexture);
        int saveReadFramebuffer = copyDepth.indexOf("int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();",
            saveFramebuffer);
        int saveDrawFramebuffer = copyDepth.indexOf("int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();",
            saveReadFramebuffer);
        int previousTextureLocal = copyDepth.indexOf("int previousTexture = 0;", saveDrawFramebuffer);
        int restoreTextureLocal = copyDepth.indexOf("boolean restoreTexture = false;", previousTextureLocal);
        int failureLocal = copyDepth.indexOf("Throwable failure = null;", restoreTextureLocal);
        int outerTry = copyDepth.indexOf("try {", failureLocal);
        int restoreDefaultActive = copyDepth.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();", outerTry);
        int saveTexture = copyDepth.indexOf("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            restoreDefaultActive);
        int markRestoreTexture = copyDepth.indexOf("restoreTexture = true;", saveTexture);
        int allocateBranch = copyDepth.indexOf("if (allocate)", markRestoreTexture);
        int readBind = copyDepth.indexOf("depthSourceFramebuffer.bindAsReadBuffer();", allocateBranch);
        int allocateBind = copyDepth.indexOf("GlStateManager.bindTexture(destination.getTextureId());", allocateBranch);
        int copyImage = copyDepth.indexOf("OculusRenderSystem.copyTexImage2D", allocateBind);
        int updateBranch = copyDepth.indexOf("} else {", copyImage);
        int copyStrategy = copyDepth.indexOf("copyStrategy.copy(depthSourceFramebuffer, currentDepthTexture, destinationFramebuffer,",
            updateBranch);
        int catchBlock = copyDepth.indexOf("catch (RuntimeException | Error exception)", copyStrategy);
        int recordFailure = copyDepth.indexOf("failure = exception;", catchBlock);
        int rethrowPrimary = copyDepth.indexOf("throw exception;", recordFailure);
        int finallyBlock = copyDepth.indexOf("finally {", rethrowPrimary);
        int cleanupFailureLocal = copyDepth.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int restoreTextureGuard = copyDepth.indexOf("if (restoreTexture)", cleanupFailureLocal);
        int restoreTexture = copyDepth.indexOf("cleanupFailure = restoreDefaultTextureBinding(cleanupFailure, previousTexture);",
            restoreTextureGuard);
        int restoreFramebuffer = copyDepth.indexOf(
            "cleanupFailure = restoreFramebufferBindings(cleanupFailure, previousFramebuffer, previousReadFramebuffer,",
            restoreTexture);
        int restoreActiveTexture = copyDepth.indexOf(
            "cleanupFailure = restoreActiveTexture(cleanupFailure, previousActiveTexture);",
            restoreFramebuffer);
        int suppress = copyDepth.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", restoreActiveTexture);
        int rethrowCleanupOnly = copyDepth.indexOf("rethrowCleanupFailure(cleanupFailure);", suppress);

        assertTrue(method >= 0);
        assertTrue("Depth copy must save the incoming active texture unit", saveActiveTexture >= 0);
        assertTrue("Depth copy must save the previous framebuffer binding", saveFramebuffer > saveActiveTexture);
        assertTrue("Depth copy must save the previous read framebuffer binding", saveReadFramebuffer > saveFramebuffer);
        assertTrue("Depth copy must save the previous draw framebuffer binding", saveDrawFramebuffer > saveReadFramebuffer);
        assertTrue("Depth copy must declare cleanup state before beginning GL work",
            previousTextureLocal > saveDrawFramebuffer && restoreTextureLocal > previousTextureLocal
                && failureLocal > restoreTextureLocal);
        assertTrue("Depth copy must restore framebuffer state even if either copy path fails", outerTry > saveFramebuffer);
        assertTrue("Depth copy must use the default texture unit for copy operations",
            restoreDefaultActive > outerTry);
        assertTrue("Depth copy must save the default unit's previous 2D texture binding",
            saveTexture > restoreDefaultActive);
        assertTrue("Depth copy must restore the texture binding only after it has been captured",
            markRestoreTexture > saveTexture);
        assertTrue(allocateBranch > outerTry);
        assertTrue("Initial depth copy must bind the source framebuffer inside the allocation branch", readBind > allocateBranch);
        assertTrue("Initial depth copy must bind the destination texture inside the allocation branch",
            allocateBind > allocateBranch);
        assertTrue(copyImage > allocateBind);
        assertTrue(updateBranch > copyImage);
        assertTrue(copyStrategy > updateBranch);
        assertTrue("Depth copy must preserve the primary copy failure before cleanup aggregation",
            catchBlock > copyStrategy && recordFailure > catchBlock && rethrowPrimary > recordFailure);
        assertTrue("Depth copy must enter cleanup after both copy paths",
            finallyBlock > rethrowPrimary && cleanupFailureLocal > finallyBlock);
        assertTrue("Depth copy must restore the previous 2D texture binding after it was captured",
            restoreTextureGuard > cleanupFailureLocal && restoreTexture > restoreTextureGuard);
        assertTrue("Depth copy must restore framebuffer bindings before restoring the caller active unit",
            restoreFramebuffer > restoreTexture && restoreActiveTexture > restoreFramebuffer);
        assertTrue("Depth copy cleanup failures must suppress onto primary failures or rethrow alone",
            suppress > restoreActiveTexture && rethrowCleanupOnly > suppress);
        assertFalse(copyDepth.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(copyDepth.contains("GL11.glCopyTexImage2D"));
        assertFalse(copyDepth.contains("GL11.glCopyTexSubImage2D"));
        String textureRestoreHelper = methodBody(source,
            "private static Throwable restoreDefaultTextureBinding(Throwable failure, int texture)");
        assertTrue("Depth copy texture cleanup must reselect the default unit before rebinding its saved texture",
            textureRestoreHelper.contains("OculusRenderSystem.restoreDefaultActiveTexture();")
                && textureRestoreHelper.contains("GlStateManager.bindTexture(texture);"));

        assertTrue(strategy.contains("OculusRenderSystem.supportsCopyImageSubData()"));
        assertTrue(strategy.contains("return CopyImage.INSTANCE;"));
        assertTrue(strategy.contains("if (safeFormat.isCombinedStencil())"));
        assertTrue(strategy.contains("OculusRenderSystem.supportsFramebufferBlit()"));
        assertTrue(strategy.contains("return BlitFramebuffer.INSTANCE;"));
        assertTrue(strategy.contains("return UnsupportedCombinedDepthStencil.INSTANCE;"));
        assertTrue(strategy.contains("return CopyTexture.INSTANCE;"));
        assertTrue(strategy.contains("OculusRenderSystem.copyTexSubImage2D("));
        assertTrue(strategy.contains("OculusRenderSystem.copyImageSubData("));
        assertTrue(strategy.contains("OculusRenderSystem.blitFramebuffer("));
        assertTrue(strategy.contains("boolean needsDestFramebuffer();"));
        assertTrue(strategy.contains("public boolean needsDestFramebuffer()"));
        assertTrue(strategy.contains("GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT"));
        assertTrue(strategy.contains("Combined depth-stencil depth copies require GL 4.3, ARB_copy_image, or framebuffer blit support"));
        assertFalse(strategy.contains("GL11.glCopyTexSubImage2D"));
    }

    @Test
    public void resizeIfNeededReattachesRecreatedDepthAndKeepsFramebufferOwnership() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");

        int method = source.indexOf("public boolean resizeIfNeeded(int newDepthTextureId, int newDepthBufferVersion, int newWidth, int newHeight,");
        int resizeMethod = source.indexOf("public void resize(int width, int height)", method);
        String resize = source.substring(method, resizeMethod);
        String reattach = methodBody(source, "private void reattachOwnedDepthTexture");
        String resizeColorTargets = methodBody(source, "private void resizeColorTargets");
        String rollbackColorTargets = methodBody(source, "private Throwable rollbackColorTargets");

        int cachedVersion = source.indexOf("private int cachedDepthBufferVersion;");
        int constructorVersion = source.indexOf("this.cachedDepthBufferVersion = depthBufferVersion;");
        int recreateDepth = resize.indexOf("boolean recreateDepth = currentDepthTexture != newDepthTextureId");
        int versionCheck = resize.indexOf("|| cachedDepthBufferVersion != newDepthBufferVersion;", recreateDepth);
        int sizeChanged = resize.indexOf("boolean sizeChanged =", versionCheck);
        int depthFormatChanged = resize.indexOf("boolean depthFormatChanged =", sizeChanged);
        int replacementStrategy = resize.indexOf("DepthCopyStrategy replacementCopyStrategy = depthFormatChanged",
            depthFormatChanged);
        int previousDepthTexture = resize.indexOf("int previousDepthTexture = currentDepthTexture;",
            replacementStrategy);
        int previousVersion = resize.indexOf("int previousDepthBufferVersion = cachedDepthBufferVersion;",
            previousDepthTexture);
        int previousDepthFormat = resize.indexOf("DepthBufferFormat previousDepthFormat = currentDepthFormat;",
            previousVersion);
        int previousCopyStrategy = resize.indexOf("DepthCopyStrategy previousCopyStrategy = copyStrategy;",
            previousDepthFormat);
        int previousWidth = resize.indexOf("int previousWidth = cachedWidth;", previousCopyStrategy);
        int previousHeight = resize.indexOf("int previousHeight = cachedHeight;", previousWidth);
        int previousFullClear = resize.indexOf("boolean previousFullClearRequired = fullClearRequired;",
            previousHeight);
        int previousTranslucentDirty = resize.indexOf(
            "boolean previousTranslucentDepthDirty = translucentDepthDirty;", previousFullClear);
        int previousHandDirty = resize.indexOf("boolean previousHandDepthDirty = handDepthDirty;",
            previousTranslucentDirty);
        int tryReattach = resize.indexOf("try {", previousHandDirty);
        int reattachGuard = resize.indexOf("if (recreateDepth || depthFormatChanged)", tryReattach);
        int reattachNew = resize.indexOf("reattachOwnedDepthTexture(newDepthTextureId);", tryReattach);
        int resizeDepthCopies = resize.indexOf("noTranslucents.resize(safeWidth, safeHeight, safeDepthFormat);",
            reattachNew);
        int reattachDepthCopyDest = resize.indexOf(
            "noTranslucentsDestFramebuffer.addDepthAttachment(noTranslucents.getTextureId());",
            resizeDepthCopies);
        int resizeColorTargetsCall = resize.indexOf("resizeColorTargets(safeWidth, safeHeight);",
            reattachDepthCopyDest);
        int catchBlock = resize.indexOf("catch (RuntimeException | Error exception)", resizeColorTargetsCall);
        int rollbackFailure = resize.indexOf("Throwable failure = null;", catchBlock);
        int rollbackReattach = resize.indexOf(
            "failure = runCleanup(failure, () -> reattachOwnedDepthTexture(previousDepthTexture));",
            rollbackFailure);
        int rollbackNoTranslucents = resize.indexOf(
            "() -> noTranslucents.resize(previousWidth, previousHeight, previousDepthFormat));",
            rollbackReattach);
        int rollbackNoHand = resize.indexOf(
            "() -> noHand.resize(previousWidth, previousHeight, previousDepthFormat));",
            rollbackNoTranslucents);
        int rollbackDepthCopyDest = resize.indexOf(
            "noTranslucentsDestFramebuffer.addDepthAttachment(noTranslucents.getTextureId());",
            rollbackNoHand);
        int rollbackDepthCopyDestVerify = resize.indexOf(
            "verifyComplete(noTranslucentsDestFramebuffer,\n"
                + "                                \"Rolled-back no-translucents depth-copy framebuffer\");",
            rollbackDepthCopyDest);
        int rollbackNoHandDepthCopyDest = resize.indexOf(
            "noHandDestFramebuffer.addDepthAttachment(noHand.getTextureId());",
            rollbackDepthCopyDestVerify);
        int rollbackNoHandDepthCopyDestVerify = resize.indexOf(
            "verifyComplete(noHandDestFramebuffer,\n"
                + "                                \"Rolled-back no-hand depth-copy framebuffer\");",
            rollbackNoHandDepthCopyDest);
        int rollbackColorTargetsCall = resize.indexOf(
            "failure = rollbackColorTargets(failure, previousWidth, previousHeight);",
            rollbackNoHandDepthCopyDestVerify);
        int restoreDepth = resize.indexOf("currentDepthTexture = previousDepthTexture;", rollbackColorTargetsCall);
        int restoreVersion = resize.indexOf("cachedDepthBufferVersion = previousDepthBufferVersion;", restoreDepth);
        int restoreDepthFormat = resize.indexOf("currentDepthFormat = previousDepthFormat;", restoreVersion);
        int restoreCopyStrategy = resize.indexOf("copyStrategy = previousCopyStrategy;", restoreDepthFormat);
        int restoreWidth = resize.indexOf("cachedWidth = previousWidth;", restoreCopyStrategy);
        int restoreHeight = resize.indexOf("cachedHeight = previousHeight;", restoreWidth);
        int restoreFullClear = resize.indexOf("fullClearRequired = previousFullClearRequired;", restoreHeight);
        int restoreTranslucentDirty = resize.indexOf("translucentDepthDirty = previousTranslucentDepthDirty;",
            restoreFullClear);
        int restoreHandDirty = resize.indexOf("handDepthDirty = previousHandDepthDirty;", restoreTranslucentDirty);
        int suppressed = resize.indexOf("addSuppressedCleanupFailure(exception, failure);", restoreHandDirty);
        int rethrow = resize.indexOf("throw exception;", suppressed);
        int updateDepth = resize.indexOf("currentDepthTexture = newDepthTextureId;", rethrow);
        int updateVersion = resize.indexOf("cachedDepthBufferVersion = newDepthBufferVersion;", updateDepth);
        int updateDepthFormat = resize.indexOf("currentDepthFormat = safeDepthFormat;", updateVersion);
        int updateCopyStrategy = resize.indexOf("copyStrategy = replacementCopyStrategy;", updateDepthFormat);
        int markTranslucentDirty = resize.indexOf("translucentDepthDirty = true;", updateCopyStrategy);
        int markHandDirty = resize.indexOf("handDepthDirty = true;", markTranslucentDirty);
        int commitWidth = resize.indexOf("cachedWidth = safeWidth;", markHandDirty);
        int commitHeight = resize.indexOf("cachedHeight = safeHeight;", commitWidth);
        int fullClear = resize.indexOf("fullClearRequired = true;", commitHeight);
        int returns = resize.indexOf("return sizeChanged;", fullClear);

        int invalidGuard = reattach.indexOf("if (replacementDepthTexture <= 0)");
        int loop = reattach.indexOf("for (GlFramebuffer framebuffer : ownedFramebuffers)", invalidGuard);
        int skipNoTranslucentsDest = reattach.indexOf("if (framebuffer == noTranslucentsDestFramebuffer || framebuffer == noHandDestFramebuffer)", loop);
        int continueSkip = reattach.indexOf("continue;", skipNoTranslucentsDest);
        int depthAttachmentGuard = reattach.indexOf("if (framebuffer.hasDepthAttachment())", loop);
        int reattachCall = reattach.indexOf("framebuffer.addDepthAttachment(replacementDepthTexture);",
            depthAttachmentGuard);

        assertTrue(method >= 0);
        assertTrue(cachedVersion >= 0);
        assertTrue(constructorVersion > cachedVersion);
        assertTrue(recreateDepth >= 0);
        assertTrue(versionCheck > recreateDepth);
        assertTrue(sizeChanged > versionCheck);
        assertTrue(depthFormatChanged > sizeChanged);
        assertTrue("Replacement copy strategy should be prepared before committing the new depth format",
            replacementStrategy > depthFormatChanged);
        assertTrue("Resize must snapshot old depth, size, clear, and dirty state before touching GL resources",
            previousDepthTexture > replacementStrategy && previousVersion > previousDepthTexture
                && previousDepthFormat > previousVersion && previousCopyStrategy > previousDepthFormat
                && previousWidth > previousCopyStrategy && previousHeight > previousWidth
                && previousFullClear > previousHeight && previousTranslucentDirty > previousFullClear
                && previousHandDirty > previousTranslucentDirty);
        assertTrue("Owned framebuffers must be reattached before the new depth texture is recorded",
            tryReattach > previousHandDirty && reattachGuard > tryReattach && reattachNew > reattachGuard
                && updateDepth > rethrow);
        assertTrue("Depth copy textures must resize using the replacement format before committing it",
            resizeDepthCopies > reattachNew && reattachDepthCopyDest > resizeDepthCopies);
        assertTrue("Color targets must resize before any new size is committed",
            resizeColorTargetsCall > reattachDepthCopyDest && commitWidth > rethrow);
        assertTrue("A failed resize must restore the previous depth attachment and depth-copy textures before rethrowing",
            catchBlock > resizeColorTargetsCall && rollbackFailure > catchBlock && rollbackReattach > rollbackFailure
                && rollbackNoTranslucents > rollbackReattach && rollbackNoHand > rollbackNoTranslucents
                && rollbackDepthCopyDest > rollbackNoHand && rollbackDepthCopyDestVerify > rollbackDepthCopyDest
                && rollbackNoHandDepthCopyDest > rollbackDepthCopyDestVerify
                && rollbackNoHandDepthCopyDestVerify > rollbackNoHandDepthCopyDest
                && rollbackColorTargetsCall > rollbackNoHandDepthCopyDestVerify);
        assertTrue("A failed resize must restore scalar state before suppressing rollback failures",
            restoreDepth > rollbackColorTargetsCall && restoreVersion > restoreDepth
                && restoreDepthFormat > restoreVersion && restoreCopyStrategy > restoreDepthFormat
                && restoreWidth > restoreCopyStrategy && restoreHeight > restoreWidth
                && restoreFullClear > restoreHeight && restoreTranslucentDirty > restoreFullClear
                && restoreHandDirty > restoreTranslucentDirty && suppressed > restoreHandDirty
                && rethrow > suppressed);
        assertTrue(updateVersion > updateDepth);
        assertTrue(updateDepthFormat > updateVersion);
        assertTrue(updateCopyStrategy > updateDepthFormat);
        assertTrue("Main depth attachments must refresh when either the texture ID or depth format changes",
            invalidGuard >= 0 && loop > invalidGuard);
        assertTrue("Depth copy destination framebuffers must not be reattached to Minecraft's depth texture",
            skipNoTranslucentsDest > loop && continueSkip > skipNoTranslucentsDest);
        assertTrue(depthAttachmentGuard > loop);
        assertTrue(reattachCall > depthAttachmentGuard);
        assertTrue("Dirty depth-copy flags must update only after resize succeeds",
            markTranslucentDirty > updateCopyStrategy && markHandDirty > markTranslucentDirty);
        assertTrue("Cached dimensions and full-clear state must commit only after successful color target resize",
            commitWidth > markHandDirty && commitHeight > commitWidth && fullClear > commitHeight);
        assertTrue(returns > fullClear);

        assertTrue(resizeColorTargets.contains("for (int i = 0; i < targets.length; i++)"));
        assertTrue(resizeColorTargets.contains("packDirectives.getTextureScaleOverride(i, width, height);"));
        assertTrue(resizeColorTargets.contains("target.resize(Math.max(1, dimensions.x()), Math.max(1, dimensions.y()));"));
        assertTrue(rollbackColorTargets.contains("failure = runCleanup(failure, () -> target.resize(targetWidth, targetHeight));"));
        assertTrue(rollbackColorTargets.contains("return failure;"));
    }

    @Test
    public void depthTextureRequiredForResizeAndDepthCopiesInsteadOfSilentSkips() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String constructor = source.substring(
            source.indexOf("public RenderTargets(int width, int height"),
            source.indexOf("private void destroyPartialConstructorResources"));
        String resize = methodBody(source,
            "public boolean resizeIfNeeded(int newDepthTextureId, int newDepthBufferVersion, int newWidth, int newHeight,");
        String copyPreTranslucent = methodBody(source, "public void copyPreTranslucentDepth()");
        String copyPreHand = methodBody(source, "public void copyPreHandDepth()");
        String requireDepth = methodBody(source,
            "private static void requireValidDepthTexture(int depthTexture, String operation)");

        int constructorRequire = constructor.indexOf(
            "requireValidDepthTexture(depthTexture, \"Render target setup\");");
        int constructorAssign = constructor.indexOf("this.currentDepthTexture = depthTexture;", constructorRequire);
        int resizeGuard = resize.indexOf("if (recreateDepth || depthFormatChanged)");
        int resizeRequire = resize.indexOf(
            "requireValidDepthTexture(newDepthTextureId, \"Render target depth reattachment\");", resizeGuard);
        int resizeReattach = resize.indexOf("reattachOwnedDepthTexture(newDepthTextureId);", resizeRequire);
        int translucentRequire = copyPreTranslucent.indexOf(
            "requireValidDepthTexture(currentDepthTexture, \"Pre-translucent depth copy\");");
        int translucentAllocate = copyPreTranslucent.indexOf(
            "boolean allocate = translucentDepthDirty;", translucentRequire);
        int translucentCopy = copyPreTranslucent.indexOf(
            "copyDepth(noTranslucents, noTranslucentsDestFramebuffer, allocate);", translucentAllocate);
        int translucentClean = copyPreTranslucent.indexOf("translucentDepthDirty = false;", translucentCopy);
        int handRequire = copyPreHand.indexOf(
            "requireValidDepthTexture(currentDepthTexture, \"Pre-hand depth copy\");");
        int handAllocate = copyPreHand.indexOf("boolean allocate = handDepthDirty;", handRequire);
        int handCopy = copyPreHand.indexOf(
            "copyDepth(noHand, noHandDestFramebuffer, allocate);", handAllocate);
        int handClean = copyPreHand.indexOf("handDepthDirty = false;", handCopy);
        int invalidGuard = requireDepth.indexOf("if (depthTexture <= 0)");
        int failClearly = requireDepth.indexOf(
            "throw new IllegalStateException(operation + \" requires a valid Minecraft depth texture\");",
            invalidGuard);

        assertTrue("RenderTargets construction must fail clearly before recording an invalid depth texture",
            constructorRequire >= 0 && constructorAssign > constructorRequire);
        assertTrue("Depth reattachment must reject invalid replacement texture IDs before touching owned FBOs",
            resizeRequire > resizeGuard && resizeReattach > resizeRequire);
        assertTrue("Pre-translucent depth copy must fail clearly instead of silently skipping required depthtex1 work",
            translucentRequire >= 0 && translucentAllocate > translucentRequire && translucentCopy > translucentAllocate
                && translucentClean > translucentCopy);
        assertTrue("Pre-hand depth copy must fail clearly instead of silently skipping required depthtex2 work",
            handRequire >= 0 && handAllocate > handRequire && handCopy > handAllocate && handClean > handCopy);
        assertTrue("The shared validation helper must throw a precise setup error",
            invalidGuard >= 0 && failClearly > invalidGuard);
        assertFalse(copyPreTranslucent.contains("return;"));
        assertFalse(copyPreHand.contains("return;"));
    }

    @Test
    public void framebufferConstructionFailuresCleanOwnedFramebufferBeforeRethrow() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String createGbuffer = source.substring(
            source.indexOf("public GlFramebuffer createGbufferFramebuffer"),
            source.indexOf("public GlFramebuffer createClearFramebuffer"));
        String createEmpty = methodBody(source, "private GlFramebuffer createEmptyFramebuffer()");
        String createColor = source.substring(
            source.indexOf("public GlFramebuffer createColorFramebuffer(Set<Integer> stageWritesToMain"),
            source.indexOf("private static void verifyComplete"));
        String createDepthCopyDestination = source.substring(
            source.indexOf("private GlFramebuffer createDepthCopyDestinationFramebuffer"),
            source.indexOf("public boolean isFullClearRequired()"));
        String createOwned = methodBody(source, "private GlFramebuffer createOwnedFramebuffer()");
        String destroyFramebuffer = methodBody(source, "private Throwable destroyFramebuffer(Throwable failure, GlFramebuffer framebuffer)");

        assertConstructionCleanup(createGbuffer, "createGbufferFramebuffer");
        assertConstructionCleanup(createEmpty, "createEmptyFramebuffer");
        assertConstructionCleanup(createColor, "createColorFramebuffer");
        assertConstructionCleanup(createDepthCopyDestination, "createDepthCopyDestinationFramebuffer");
        assertOwnedFramebufferRegistrationCleanup(createOwned);
        assertTrue("Empty framebuffer setup must use guarded owned-framebuffer allocation",
            createEmpty.contains("GlFramebuffer framebuffer = createOwnedFramebuffer();"));
        assertTrue("Color framebuffer setup must use guarded owned-framebuffer allocation",
            createColor.contains("GlFramebuffer framebuffer = createOwnedFramebuffer();"));
        assertFalse("Render target framebuffer setup must not register raw framebuffers outside guarded allocation",
            createEmpty.contains("ownedFramebuffers.add(framebuffer);"));
        assertFalse("Render target framebuffer setup must not register raw framebuffers outside guarded allocation",
            createColor.contains("ownedFramebuffers.add(framebuffer);"));

        int destroyTry = destroyFramebuffer.indexOf("try {");
        int wrapperDestroy = destroyFramebuffer.indexOf("framebuffer.destroy();", destroyTry);
        int recordFailure = destroyFramebuffer.indexOf("failure = addCleanupFailure(failure, exception);",
            wrapperDestroy);
        int finallyBlock = destroyFramebuffer.indexOf("finally {", recordFailure);
        int removeOwned = destroyFramebuffer.indexOf("ownedFramebuffers.remove(framebuffer);", finallyBlock);
        int returnFailure = destroyFramebuffer.indexOf("return failure;", recordFailure);

        assertTrue("Owned framebuffer teardown must record destroy failures",
            destroyTry >= 0 && wrapperDestroy > destroyTry && recordFailure > wrapperDestroy);
        assertTrue("Owned framebuffer bookkeeping must clear stale ownership even when wrapper destroy reports failure",
            finallyBlock > recordFailure && removeOwned > finallyBlock && returnFailure > removeOwned);
    }

    @Test
    public void depthCopyDestinationFramebuffersVerifyCompletenessAfterDepthReplacement() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String createDepthCopyDestination = methodBody(source,
            "private GlFramebuffer createDepthCopyDestinationFramebuffer");
        String resize = methodBody(source,
            "public boolean resizeIfNeeded(int newDepthTextureId, int newDepthBufferVersion, int newWidth, int newHeight,");

        int createAttach = createDepthCopyDestination.indexOf(
            "framebuffer.addDepthAttachment(depthTexture.getTextureId());");
        int createVerify = createDepthCopyDestination.indexOf(
            "verifyComplete(framebuffer, \"Depth-copy destination framebuffer\");", createAttach);

        int successNoTranslucentsAttach = resize.indexOf(
            "noTranslucentsDestFramebuffer.addDepthAttachment(noTranslucents.getTextureId());");
        int successNoTranslucentsVerify = resize.indexOf(
            "verifyComplete(noTranslucentsDestFramebuffer,\n"
                + "                        \"No-translucents depth-copy framebuffer after depth format change\");",
            successNoTranslucentsAttach);
        int successNoHandAttach = resize.indexOf(
            "noHandDestFramebuffer.addDepthAttachment(noHand.getTextureId());",
            successNoTranslucentsVerify);
        int successNoHandVerify = resize.indexOf(
            "verifyComplete(noHandDestFramebuffer,\n"
                + "                        \"No-hand depth-copy framebuffer after depth format change\");",
            successNoHandAttach);

        int catchBlock = resize.indexOf("catch (RuntimeException | Error exception)", successNoHandVerify);
        int rollbackNoTranslucentsAttach = resize.indexOf(
            "noTranslucentsDestFramebuffer.addDepthAttachment(noTranslucents.getTextureId());",
            catchBlock);
        int rollbackNoTranslucentsVerify = resize.indexOf(
            "verifyComplete(noTranslucentsDestFramebuffer,\n"
                + "                                \"Rolled-back no-translucents depth-copy framebuffer\");",
            rollbackNoTranslucentsAttach);
        int rollbackNoHandAttach = resize.indexOf(
            "noHandDestFramebuffer.addDepthAttachment(noHand.getTextureId());",
            rollbackNoTranslucentsVerify);
        int rollbackNoHandVerify = resize.indexOf(
            "verifyComplete(noHandDestFramebuffer,\n"
                + "                                \"Rolled-back no-hand depth-copy framebuffer\");",
            rollbackNoHandAttach);

        assertTrue("Depth-copy destination FBO setup must validate after replacing the depth attachment",
            createAttach >= 0 && createVerify > createAttach);
        assertTrue("Successful depth-format reattachment must validate the no-translucents destination FBO",
            successNoTranslucentsAttach >= 0 && successNoTranslucentsVerify > successNoTranslucentsAttach);
        assertTrue("Successful depth-format reattachment must validate the no-hand destination FBO",
            successNoHandAttach > successNoTranslucentsVerify && successNoHandVerify > successNoHandAttach);
        assertTrue("Resize rollback must validate the restored no-translucents destination FBO",
            rollbackNoTranslucentsAttach > catchBlock
                && rollbackNoTranslucentsVerify > rollbackNoTranslucentsAttach);
        assertTrue("Resize rollback must validate the restored no-hand destination FBO",
            rollbackNoHandAttach > rollbackNoTranslucentsVerify && rollbackNoHandVerify > rollbackNoHandAttach);
    }

    @Test
    public void constructorCleansPartialTargetDepthAndFramebufferAllocationsOnFailure() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String constructor = source.substring(
            source.indexOf("public RenderTargets(int width, int height"),
            source.indexOf("private void destroyPartialConstructorResources"));
        String cleanup = methodBody(source,
            "private void destroyPartialConstructorResources(DepthTexture createdNoTranslucents, DepthTexture createdNoHand)");

        int ownedList = constructor.indexOf("this.ownedFramebuffers = new ArrayList<>();");
        int noTranslucentsLocal = constructor.indexOf("DepthTexture createdNoTranslucents = null;", ownedList);
        int noHandLocal = constructor.indexOf("DepthTexture createdNoHand = null;", noTranslucentsLocal);
        int depthSourceLocal = constructor.indexOf("GlFramebuffer createdDepthSourceFramebuffer = null;",
            noHandLocal);
        int noTranslucentsDestLocal = constructor.indexOf("GlFramebuffer createdNoTranslucentsDestFramebuffer = null;",
            depthSourceLocal);
        int noHandDestLocal = constructor.indexOf("GlFramebuffer createdNoHandDestFramebuffer = null;",
            noTranslucentsDestLocal);
        int tryBlock = constructor.indexOf("try {", noHandDestLocal);
        int buildTargets = constructor.indexOf("renderTargets.forEach((index, settings) ->", tryBlock);
        int createDepthSource = constructor.indexOf(
            "createdDepthSourceFramebuffer = createGbufferFramebuffer(Collections.emptySet(), new int[] {0});",
            buildTargets);
        int createNoTranslucents = constructor.indexOf(
            "createdNoTranslucents = new DepthTexture(width, height, currentDepthFormat);", createDepthSource);
        int createNoHand = constructor.indexOf("createdNoHand = new DepthTexture(width, height, currentDepthFormat);",
            createNoTranslucents);
        int createNoTranslucentsDest = constructor.indexOf(
            "createdNoTranslucentsDestFramebuffer = createDepthCopyDestinationFramebuffer(createdNoTranslucents);",
            createNoHand);
        int createNoHandDest = constructor.indexOf(
            "createdNoHandDestFramebuffer = createDepthCopyDestinationFramebuffer(createdNoHand);",
            createNoTranslucentsDest);
        int catchBlock = constructor.indexOf("} catch (RuntimeException | Error exception)", createNoHandDest);
        int cleanupCall = constructor.indexOf(
            "destroyPartialConstructorResources(createdNoTranslucents, createdNoHand);", catchBlock);
        int cleanupCatch = constructor.indexOf("catch (RuntimeException | Error cleanupException)", cleanupCall);
        int suppressed = constructor.indexOf("exception.addSuppressed(cleanupException);", cleanupCatch);
        int rethrow = constructor.indexOf("throw exception;", suppressed);
        int assignDepthSource = constructor.indexOf("this.depthSourceFramebuffer = createdDepthSourceFramebuffer;",
            rethrow);
        int assignNoTranslucents = constructor.indexOf("this.noTranslucents = createdNoTranslucents;",
            assignDepthSource);
        int assignNoHand = constructor.indexOf("this.noHand = createdNoHand;", assignNoTranslucents);
        int assignNoTranslucentsDest = constructor.indexOf(
            "this.noTranslucentsDestFramebuffer = createdNoTranslucentsDestFramebuffer;", assignNoHand);
        int assignNoHandDest = constructor.indexOf("this.noHandDestFramebuffer = createdNoHandDestFramebuffer;",
            assignNoTranslucentsDest);

        assertTrue(ownedList >= 0);
        assertTrue(noTranslucentsLocal > ownedList);
        assertTrue(noHandLocal > noTranslucentsLocal);
        assertTrue(depthSourceLocal > noHandLocal);
        assertTrue(noTranslucentsDestLocal > depthSourceLocal);
        assertTrue(noHandDestLocal > noTranslucentsDestLocal);
        assertTrue("RenderTargets constructor must guard render target and framebuffer allocation",
            tryBlock > noHandDestLocal && buildTargets > tryBlock);
        assertTrue(createDepthSource > buildTargets);
        assertTrue(createNoTranslucents > createDepthSource);
        assertTrue(createNoHand > createNoTranslucents);
        assertTrue(createNoTranslucentsDest > createNoHand);
        assertTrue(createNoHandDest > createNoTranslucentsDest);
        assertTrue("Partial constructor failure must clean local depth textures and owned framebuffers",
            cleanupCall > catchBlock && cleanupCatch > cleanupCall && suppressed > cleanupCatch
                && rethrow > suppressed);
        assertTrue("Final fields must only take ownership after guarded construction succeeds",
            assignDepthSource > rethrow && assignNoTranslucents > assignDepthSource && assignNoHand > assignNoTranslucents
                && assignNoTranslucentsDest > assignNoHand && assignNoHandDest > assignNoTranslucentsDest);

        int destroyOwned = cleanup.indexOf("for (GlFramebuffer owned : ownedFramebuffers)");
        int destroyOwnedCall = cleanup.indexOf("failure = destroyFramebufferResource(failure, owned);", destroyOwned);
        int clearOwned = cleanup.indexOf("ownedFramebuffers.clear();", destroyOwnedCall);
        int destroyTargets = cleanup.indexOf("for (RenderTarget target : targets)", clearOwned);
        int targetDestroy = cleanup.indexOf("failure = destroyRenderTarget(failure, target);", destroyTargets);
        int noTranslucentsDestroy = cleanup.indexOf(
            "failure = destroyDepthTexture(failure, createdNoTranslucents);", targetDestroy);
        int noHandDestroy = cleanup.indexOf("failure = destroyDepthTexture(failure, createdNoHand);",
            noTranslucentsDestroy);
        int rethrowCleanup = cleanup.indexOf("rethrowCleanupFailure(failure);", noHandDestroy);

        assertTrue("Constructor cleanup must destroy any owned framebuffer created before failure",
            destroyOwned >= 0 && destroyOwnedCall > destroyOwned && clearOwned > destroyOwnedCall);
        assertTrue("Constructor cleanup must destroy render targets allocated before failure",
            destroyTargets > clearOwned && targetDestroy > destroyTargets);
        assertTrue("Constructor cleanup must destroy local no-translucents depth texture",
            noTranslucentsDestroy > targetDestroy);
        assertTrue("Constructor cleanup must destroy local no-hand depth texture",
            noHandDestroy > noTranslucentsDestroy && rethrowCleanup > noHandDestroy);
    }

    @Test
    public void destroyAggregatesCleanupFailuresAcrossOwnedFramebuffersTargetsAndDepthTextures() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String destroy = methodBody(source, "public void destroy()");
        String helpers = source.substring(source.indexOf("private static Throwable destroyFramebufferResource"));

        int failureLocal = destroy.indexOf("Throwable failure = null;");
        int framebufferLoop = destroy.indexOf("for (GlFramebuffer owned : ownedFramebuffers)", failureLocal);
        int destroyFramebuffer = destroy.indexOf("failure = destroyFramebufferResource(failure, owned);",
            framebufferLoop);
        int targetLoop = destroy.indexOf("for (RenderTarget target : targets)", destroyFramebuffer);
        int destroyTarget = destroy.indexOf("failure = destroyRenderTarget(failure, target);", targetLoop);
        int destroyNoTranslucents = destroy.indexOf("failure = destroyDepthTexture(failure, noTranslucents);",
            destroyTarget);
        int destroyNoHand = destroy.indexOf("failure = destroyDepthTexture(failure, noHand);",
            destroyNoTranslucents);
        int rethrow = destroy.indexOf("rethrowCleanupFailure(failure);", destroyNoHand);
        int finallyBlock = destroy.indexOf("} finally {", rethrow);
        int clearOwned = destroy.indexOf("ownedFramebuffers.clear();", finallyBlock);
        int clearTargets = destroy.indexOf("Arrays.fill(targets, null);", clearOwned);
        int clearDepth = destroy.indexOf("currentDepthTexture = 0;", clearTargets);
        int clearVersion = destroy.indexOf("cachedDepthBufferVersion = 0;", clearDepth);
        int clearWidth = destroy.indexOf("cachedWidth = 0;", clearVersion);
        int clearHeight = destroy.indexOf("cachedHeight = 0;", clearWidth);
        int clearFullClear = destroy.indexOf("fullClearRequired = false;", clearHeight);
        int clearTranslucentDirty = destroy.indexOf("translucentDepthDirty = false;", clearFullClear);
        int clearHandDirty = destroy.indexOf("handDepthDirty = false;", clearTranslucentDirty);

        assertTrue(failureLocal >= 0);
        assertTrue("RenderTargets destroy must attempt framebuffer cleanup before color target cleanup",
            framebufferLoop > failureLocal && destroyFramebuffer > framebufferLoop && targetLoop > destroyFramebuffer);
        assertTrue("RenderTargets destroy must attempt every owned texture class before rethrowing cleanup failures",
            destroyTarget > targetLoop && destroyNoTranslucents > destroyTarget && destroyNoHand > destroyNoTranslucents
                && rethrow > destroyNoHand);
        assertTrue("RenderTargets destroy must clear stale framebuffer, target, and depth lifecycle state even when cleanup rethrows",
            finallyBlock > rethrow && clearOwned > finallyBlock && clearTargets > clearOwned
                && clearDepth > clearTargets && clearVersion > clearDepth && clearWidth > clearVersion
                && clearHeight > clearWidth && clearFullClear > clearHeight
                && clearTranslucentDirty > clearFullClear && clearHandDirty > clearTranslucentDirty);
        assertTrue("RenderTargets destroy must use the structured array clearer for target handles",
            source.contains("import java.util.Arrays;"));
        assertTrue(helpers.contains("failure.addSuppressed(exception);"));
        assertTrue(helpers.contains("throw (RuntimeException) failure;"));
        assertTrue(helpers.contains("throw (Error) failure;"));
    }

    @Test
    public void destroyedRenderTargetsFailClearlyOnRuntimeEntryPoints() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String destroy = methodBody(source, "public void destroy()");
        String requireLive = methodBody(source, "private void requireLive");
        String resizeIfNeeded = methodBody(source,
            "public boolean resizeIfNeeded(int newDepthTextureId, int newDepthBufferVersion, int newWidth, int newHeight,");
        String copyPreTranslucent = methodBody(source, "public void copyPreTranslucentDepth()");
        String copyPreHand = methodBody(source, "public void copyPreHandDepth()");
        String createGbuffer = methodBody(source, "public GlFramebuffer createGbufferFramebuffer");
        String createColor = methodBody(source,
            "public GlFramebuffer createColorFramebuffer(Set<Integer> stageWritesToMain");
        String isFullClearRequired = methodBody(source, "public boolean isFullClearRequired()");
        String onFullClear = methodBody(source, "public void onFullClear()");
        String getDepthTexture = methodBody(source, "public int getDepthTexture()");

        int destroyedField = source.indexOf("private boolean destroyed;");
        int destroyGuard = destroy.indexOf("if (destroyed)");
        int destroyReturn = destroy.indexOf("return;", destroyGuard);
        int finallyCleanup = destroy.indexOf("} finally {");
        int markDestroyed = destroy.indexOf("destroyed = true;", finallyCleanup);
        int requireGuard = requireLive.indexOf("if (destroyed)");
        int failClearly = requireLive.indexOf(
            "throw new IllegalStateException(\"Cannot \" + operation + \" after render targets were destroyed\");",
            requireGuard);

        assertTrue("RenderTargets must remember when teardown has completed", destroyedField >= 0);
        assertTrue("RenderTargets destroy must remain idempotent after the first teardown",
            destroyGuard >= 0 && destroyReturn > destroyGuard && destroyReturn < finallyCleanup);
        assertTrue("RenderTargets destroy must mark the owner destroyed even when cleanup reports a failure",
            markDestroyed > finallyCleanup);
        assertTrue("Destroyed render targets must fail with a precise lifecycle error",
            requireGuard >= 0 && failClearly > requireGuard);

        assertTrue(resizeIfNeeded.contains("requireLive(\"resize render targets\");"));
        assertTrue(copyPreTranslucent.contains("requireLive(\"copy pre-translucent depth\");"));
        assertTrue(copyPreHand.contains("requireLive(\"copy pre-hand depth\");"));
        assertTrue(createGbuffer.contains("requireLive(\"create gbuffer framebuffer\");"));
        assertTrue(createColor.contains("requireLive(\"create color framebuffer\");"));
        assertTrue(isFullClearRequired.contains("requireLive(\"read full-clear state\");"));
        assertTrue(onFullClear.contains("requireLive(\"consume full-clear state\");"));
        assertTrue(getDepthTexture.contains("requireLive(\"read depth texture\");"));
        assertFalse("Destroyed render-target operations must not silently no-op",
            resizeIfNeeded.contains("if (destroyed) {\n            return"));
        assertFalse(copyPreTranslucent.contains("if (destroyed)"));
        assertFalse(copyPreHand.contains("if (destroyed)"));
    }

    @Test
    public void renderTargetAndDepthAllocationUseReferenceStyleBindWrappers() throws Exception {
        String renderSystem = read("src/main/java/net/oculus/gl/OculusRenderSystem.java");
        String renderTarget = read("src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/RenderTarget.java");
        String depthTexture = read("src/main/java/net/oculus/rendertarget/DepthTexture.java");
        String framebufferManager = read(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java");
        String createDepthTexture = framebufferManager.substring(
            framebufferManager.indexOf("private static int createDepthTexture"),
            framebufferManager.indexOf("private static void deleteTexture"));

        assertTrue(renderSystem.contains("public static void texImage2D(int texture, int target, int level"));
        assertTrue(renderSystem.contains("public static void copyTexImage2D(int target, int level"));
        assertTrue(renderSystem.contains("bindTextureForLegacyOperation(target, texture);"));
        assertTrue(renderSystem.contains("TextureLifecycleTracker.onTexImage2D(texture, target, level, internalFormat"));
        assertTrue(renderSystem.contains("public static void withDefaultTextureBindingRestored(Runnable operation)"));

        assertTrue(renderTarget.contains("OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(renderTarget.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(renderTarget.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        assertFalse(renderTarget.contains("GlStateManager.bindTexture(0);"));
        assertFalse(renderTarget.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(renderTarget.contains("GL11.glTexImage2D"));
        assertFalse(renderTarget.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));

        assertTrue(depthTexture.contains("OculusRenderSystem.texImage2D("));
        assertTrue(depthTexture.contains("OculusRenderSystem.texParameteri(getGlId(), GL11.GL_TEXTURE_2D"));
        assertTrue(depthTexture.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        assertFalse(depthTexture.contains("GlStateManager.bindTexture(0);"));
        assertFalse(depthTexture.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(depthTexture.contains("GL11.glTexImage2D"));
        assertFalse(depthTexture.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));

        assertTrue(createDepthTexture.contains("OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(createDepthTexture.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(createDepthTexture.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        assertFalse(createDepthTexture.contains("GlStateManager.bindTexture(0);"));
        assertFalse(createDepthTexture.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(createDepthTexture.contains("GL11.glTexImage2D"));
        assertFalse(createDepthTexture.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));
    }

    @Test
    public void activeRenderTargetAllocationDeletesPartialTexturesBeforeRethrow() throws Exception {
        String renderTarget = read("src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/RenderTarget.java");
        String constructor = renderTarget.substring(
            renderTarget.indexOf("private RenderTarget(Builder builder)"),
            renderTarget.indexOf("private void setupTexture"));

        int createdMainTexture = constructor.indexOf("int createdMainTexture = 0;");
        int createdAltTexture = constructor.indexOf("int createdAltTexture = 0;", createdMainTexture);
        int tryBlock = constructor.indexOf("try {", createdAltTexture);
        int mainTexture = constructor.indexOf(
            "createdMainTexture = createTexture(\"main render target texture\");", tryBlock);
        int altTexture = constructor.indexOf(
            "createdAltTexture = createTexture(\"alternate render target texture\");", mainTexture);
        int setupMainLocal = constructor.indexOf("final int setupMainTexture = createdMainTexture;", altTexture);
        int setupAltLocal = constructor.indexOf("final int setupAltTexture = createdAltTexture;", setupMainLocal);
        int restoreWrapper = constructor.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {",
            setupAltLocal);
        int setupMain = constructor.indexOf("setupTexture(setupMainTexture, builder.width, builder.height, allowsLinear);",
            restoreWrapper);
        int setupAlt = constructor.indexOf("setupTexture(setupAltTexture, builder.width, builder.height, allowsLinear);",
            setupMain);
        int catchBlock = constructor.indexOf("catch (RuntimeException | Error exception)", setupAlt);
        int failedMainLocal = constructor.indexOf("final int failedMainTexture = createdMainTexture;", catchBlock);
        int failedAltLocal = constructor.indexOf("final int failedAltTexture = createdAltTexture;", failedMainLocal);
        int failureLocal = constructor.indexOf("Throwable failure = null;", catchBlock);
        int deleteMain = constructor.indexOf("failure = runCleanup(failure, () -> deleteTexture(failedMainTexture));",
            failureLocal);
        int deleteAlt = constructor.indexOf("failure = runCleanup(failure, () -> deleteTexture(failedAltTexture));",
            deleteMain);
        int suppressCleanup = constructor.indexOf("addSuppressedCleanupFailure(exception, failure);", deleteAlt);
        int rethrow = constructor.indexOf("throw exception;", suppressCleanup);
        int assignMain = constructor.indexOf("this.mainTexture = createdMainTexture;", rethrow);
        int assignAlt = constructor.indexOf("this.altTexture = createdAltTexture;", assignMain);
        int destroy = renderTarget.indexOf("public void destroy()");
        int destroyInvalidate = renderTarget.indexOf("valid = false;", destroy);
        int destroyFailure = renderTarget.indexOf("Throwable failure = null;", destroyInvalidate);
        int destroyMain = renderTarget.indexOf("failure = runCleanup(failure, () -> deleteTexture(mainTexture));",
            destroyFailure);
        int destroyAlt = renderTarget.indexOf("failure = runCleanup(failure, () -> deleteTexture(altTexture));",
            destroyMain);
        int destroyRethrow = renderTarget.indexOf("rethrowCleanupFailure(failure);", destroyAlt);

        assertTrue(createdMainTexture >= 0);
        assertTrue(createdAltTexture > createdMainTexture);
        assertTrue("RenderTarget must generate the paired main texture before setup", mainTexture > tryBlock);
        assertTrue("RenderTarget must generate the paired alt texture before setup", altTexture > mainTexture);
        assertTrue("RenderTarget setup must be guarded after both texture name locals are initialized",
            tryBlock > createdAltTexture);
        assertTrue("RenderTarget setup must snapshot generated texture names for lambda setup",
            setupMainLocal > altTexture && setupAltLocal > setupMainLocal);
        assertTrue("RenderTarget setup must restore default-unit texture binding and caller active unit",
            restoreWrapper > setupAltLocal);
        assertTrue(setupMain > restoreWrapper);
        assertTrue(setupAlt > setupMain);
        assertTrue("RenderTarget construction must catch setup failures after texture state cleanup",
            catchBlock > setupAlt && failureLocal > catchBlock);
        assertTrue("RenderTarget construction must snapshot failed texture names before cleanup lambdas",
            failedMainLocal > catchBlock && failedAltLocal > failedMainLocal);
        assertTrue("RenderTarget must delete the main texture on setup failure", deleteMain > failureLocal);
        assertTrue("RenderTarget must delete the alt texture on setup failure", deleteAlt > deleteMain);
        assertTrue("RenderTarget setup cleanup failures must suppress onto the original setup failure",
            suppressCleanup > deleteAlt && rethrow > suppressCleanup);
        assertTrue("RenderTarget final fields must only take ownership after guarded setup succeeds",
            assignMain > rethrow && assignAlt > assignMain);
        assertTrue("RenderTarget must fail clearly if the GL backend does not allocate a texture name",
            renderTarget.contains("throw new IllegalStateException(\"Failed to create \" + context);"));
        assertTrue("RenderTarget destroy must attempt both paired texture deletes before rethrowing cleanup failures",
            destroyInvalidate > destroy && destroyFailure > destroyInvalidate && destroyMain > destroyFailure && destroyAlt > destroyMain
                && destroyRethrow > destroyAlt);
        assertTrue("RenderTarget destroy must invalidate the pair before cleanup like 1.16.5",
            destroyInvalidate > destroy && destroyInvalidate < destroyFailure);
    }

    @Test
    public void activeRenderTargetResizeCommitsDimensionsAfterTextureStorage() throws Exception {
        String renderTarget = read("src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/RenderTarget.java");
        String resize = methodBody(renderTarget, "public void resize(int width, int height)");

        int requireValid = resize.indexOf("requireValid();");
        int restoreWrapper = resize.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {",
            requireValid);
        int resizeMain = resize.indexOf("resizeTexture(mainTexture, width, height);", restoreWrapper);
        int resizeAlt = resize.indexOf("resizeTexture(altTexture, width, height);", resizeMain);
        int commitWidth = resize.indexOf("this.width = width;", resizeAlt);
        int commitHeight = resize.indexOf("this.height = height;", commitWidth);

        assertTrue("RenderTarget resize must validate the target before touching GL storage", requireValid >= 0);
        assertTrue("RenderTarget resize must restore default-unit texture binding and caller active unit",
            restoreWrapper > requireValid);
        assertTrue("RenderTarget resize must allocate main and alt texture storage before committing dimensions",
            resizeMain > restoreWrapper && resizeAlt > resizeMain);
        assertTrue("RenderTarget resize must not report new dimensions until both textures resize",
            commitWidth > resizeAlt && commitHeight > commitWidth);
    }

    @Test
    public void activeDepthTextureAllocationDestroysPartialTextureBeforeRethrow() throws Exception {
        String depthTexture = read("src/main/java/net/oculus/rendertarget/DepthTexture.java");
        String constructor = depthTexture.substring(
            depthTexture.indexOf("DepthTexture(int width, int height, DepthBufferFormat format)"),
            depthTexture.indexOf("void resize"));

        int generate = constructor.indexOf("super(createTexture(\"depth render target texture\"));");
        int tryBlock = constructor.indexOf("try {", generate);
        int restoreWrapper = constructor.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {",
            tryBlock);
        int allocate = constructor.indexOf("allocate(width, height, format);", restoreWrapper);
        int catchBlock = constructor.indexOf("catch (RuntimeException | Error exception)", allocate);
        String resize = methodBody(depthTexture, "void resize");
        int resizeWrapper = resize.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> allocate(width, height, format));");
        int destroy = constructor.indexOf("runCleanup(null, this::destroy)", catchBlock);
        int suppressCleanup = constructor.indexOf("addSuppressedCleanupFailure(exception,", catchBlock);
        int rethrow = constructor.indexOf("throw exception;", suppressCleanup);

        assertTrue("DepthTexture must generate its texture name before setup", generate >= 0);
        assertTrue("DepthTexture must fail clearly if the GL backend does not allocate a texture name",
            depthTexture.contains("throw new IllegalStateException(\"Failed to create \" + context);"));
        assertTrue("DepthTexture setup must be guarded", tryBlock > generate);
        assertTrue("DepthTexture setup must restore default-unit texture binding and caller active unit",
            restoreWrapper > tryBlock);
        assertTrue("DepthTexture must allocate storage inside the guarded setup", allocate > restoreWrapper);
        assertTrue("DepthTexture construction must catch setup failures after restoring texture binding",
            catchBlock > allocate);
        assertTrue("DepthTexture must delete the generated texture if construction fails", destroy > catchBlock);
        assertTrue("DepthTexture cleanup failures must suppress onto the original setup failure",
            suppressCleanup > catchBlock && rethrow > suppressCleanup);
        assertTrue("DepthTexture resize must also restore texture binding state", resizeWrapper >= 0);
    }

    @Test
    public void renderTargetTextureDeletesNotifyLifecycleEvenWhenGlDeleteFails() throws Exception {
        String renderTarget = read("src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/RenderTarget.java");
        String depthTexture = read("src/main/java/net/oculus/rendertarget/DepthTexture.java");

        assertTextureDeleteNotifiesFromFinally(methodBody(renderTarget, "private static void deleteTexture(int texture)"),
            "texture", "RenderTarget texture delete");
        assertTextureDeleteNotifiesFromFinally(methodBody(depthTexture, "protected void destroyInternal()"),
            "texture", "DepthTexture texture delete");
    }

    @Test
    public void framebufferManagerTextureDeletesNotifyLifecycleEvenWhenGlDeleteFails() throws Exception {
        String activeFramebufferManager = read(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java");
        String legacyFramebufferManager = read("src/main/java/net/oculus/pipeline/framebuffer/FramebufferManager.java");

        assertTextureDeleteNotifiesFromFinally(methodBody(activeFramebufferManager,
            "private static void deleteTexture(int texture)"), "texture", "Active framebuffer texture delete");
        assertTextureDeleteNotifiesFromFinally(methodBody(legacyFramebufferManager,
            "private static void deleteTexture(int texture)"), "texture", "Legacy framebuffer texture delete");
    }

    @Test
    public void framebufferManagerTextureCreationDeletesPartialTexturesBeforeRethrow() throws Exception {
        String activeFramebufferManager = read(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java");
        String legacyFramebufferManager = read("src/main/java/net/oculus/pipeline/framebuffer/FramebufferManager.java");

        assertSingleTextureCreationCleanup(methodBody(activeFramebufferManager, "private static int createDepthTexture"),
            "Active framebuffer depth texture");
        assertSingleTextureCreationCleanup(methodBody(legacyFramebufferManager, "private static int createColorTexture"),
            "Legacy framebuffer color texture");
        assertSingleTextureCreationCleanup(methodBody(legacyFramebufferManager, "private static int createDepthTexture"),
            "Legacy framebuffer depth texture");
        assertSingleTextureCreationCleanup(methodBody(legacyFramebufferManager, "private int createNoiseTexture"),
            "Legacy framebuffer noise texture");
    }

    @Test
    public void activeFramebufferManagerSetupCleanupSuppressesCleanupFailures() throws Exception {
        String framebufferManager = read(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java");
        String initialize = methodBody(framebufferManager, "public void initialize()");
        String createFramebuffer = methodBody(framebufferManager, "public Framebuffer createFramebuffer");
        String destroyFramebuffer = methodBody(framebufferManager, "public void destroyFramebuffer");
        String destroy = methodBody(framebufferManager, "public void destroy()");
        String rebuildDepth = methodBody(framebufferManager, "private void rebuildDepthTexture");

        int initializeCatch = initialize.indexOf("catch (RuntimeException | Error exception)");
        int destroyTry = initialize.indexOf("try {", initializeCatch);
        int destroyCall = initialize.indexOf("destroy();", destroyTry);
        int cleanupCatch = initialize.indexOf("catch (RuntimeException | Error cleanupException)", destroyCall);
        int suppressCleanup = initialize.indexOf("exception.addSuppressed(cleanupException);", cleanupCatch);
        int initializeRethrow = initialize.indexOf("throw exception;", suppressCleanup);

        assertTrue("Initialization cleanup must not mask the original allocation failure",
            initializeCatch >= 0 && destroyTry > initializeCatch && destroyCall > destroyTry
                && cleanupCatch > destroyCall && suppressCleanup > cleanupCatch
                && initializeRethrow > suppressCleanup);

        int framebufferLocal = createFramebuffer.indexOf("Framebuffer framebuffer = null;");
        int framebufferWrap = createFramebuffer.indexOf("framebuffer = new Framebuffer(glFramebuffer, drawBuffers);",
            framebufferLocal);
        int framebufferAddOwned = createFramebuffer.indexOf("ownedFramebuffers.add(framebuffer);", framebufferWrap);
        int framebufferCatch = createFramebuffer.indexOf("catch (RuntimeException | Error exception)");
        int framebufferDestroyTry = createFramebuffer.indexOf("try {", framebufferCatch);
        int framebufferWrapperGuard = createFramebuffer.indexOf("if (framebuffer != null)", framebufferDestroyTry);
        int framebufferDestroyWrapper = createFramebuffer.indexOf("framebuffer.destroy();", framebufferWrapperGuard);
        int framebufferRemoveOwned = createFramebuffer.indexOf("ownedFramebuffers.remove(framebuffer);",
            framebufferDestroyWrapper);
        int framebufferRawElse = createFramebuffer.indexOf("} else {", framebufferRemoveOwned);
        int framebufferDestroy = createFramebuffer.indexOf("glFramebuffer.destroy();", framebufferRawElse);
        int framebufferCleanupCatch = createFramebuffer.indexOf(
            "catch (RuntimeException | Error cleanupException)", framebufferDestroy);
        int framebufferSuppress = createFramebuffer.indexOf("exception.addSuppressed(cleanupException);",
            framebufferCleanupCatch);
        int framebufferRethrow = createFramebuffer.indexOf("throw exception;", framebufferSuppress);

        assertTrue("Framebuffer construction must track wrapper ownership before publishing to the owner list",
            framebufferLocal >= 0 && framebufferWrap > framebufferLocal && framebufferAddOwned > framebufferWrap);
        assertTrue("Framebuffer construction cleanup must suppress FBO destroy failures onto the setup failure",
            framebufferCatch >= 0 && framebufferDestroyTry > framebufferCatch
                && framebufferWrapperGuard > framebufferDestroyTry
                && framebufferDestroyWrapper > framebufferWrapperGuard
                && framebufferRemoveOwned > framebufferDestroyWrapper
                && framebufferRawElse > framebufferRemoveOwned
                && framebufferDestroy > framebufferRawElse
                && framebufferCleanupCatch > framebufferDestroy
                && framebufferSuppress > framebufferCleanupCatch
                && framebufferRethrow > framebufferSuppress);

        int destroyTryBlock = destroyFramebuffer.indexOf("try {");
        int destroyCallInHelper = destroyFramebuffer.indexOf("framebuffer.destroy();", destroyTryBlock);
        int finallyBlock = destroyFramebuffer.indexOf("finally {", destroyCallInHelper);
        int removeOwned = destroyFramebuffer.indexOf("ownedFramebuffers.remove(framebuffer);", finallyBlock);
        assertTrue("Framebuffer owner bookkeeping must clear stale ownership even when wrapper destroy reports failure",
            destroyTryBlock >= 0 && destroyCallInHelper > destroyTryBlock
                && finallyBlock > destroyCallInHelper && removeOwned > finallyBlock);

        int destroyFailure = destroy.indexOf("Throwable failure = null;");
        int destroyOwnedLoop = destroy.indexOf("for (Framebuffer framebuffer : ownedFramebuffers)", destroyFailure);
        int destroyOwned = destroy.indexOf("failure = runCleanup(failure, framebuffer::destroy);", destroyOwnedLoop);
        int destroyTargets = destroy.indexOf("for (RenderTarget target : renderTargets.values())", destroyOwned);
        int destroyTarget = destroy.indexOf("failure = runCleanup(failure, target::destroy);", destroyTargets);
        int destroyDepth = destroy.indexOf("failure = runCleanup(failure, () -> deleteTexture(depthTexture));",
            destroyTarget);
        int destroyRethrow = destroy.indexOf("rethrowCleanupFailure(failure);", destroyDepth);
        int destroyFinally = destroy.indexOf("} finally {", destroyRethrow);
        int clearOwned = destroy.indexOf("ownedFramebuffers.clear();", destroyFinally);
        int clearTargets = destroy.indexOf("renderTargets.clear();", clearOwned);
        int zeroDepth = destroy.indexOf("depthTexture = 0;", clearTargets);
        assertTrue("Active framebuffer manager destroy must clear stale lifecycle state even when cleanup fails",
            destroyFailure >= 0 && destroyOwnedLoop > destroyFailure && destroyOwned > destroyOwnedLoop
                && destroyTargets > destroyOwned && destroyTarget > destroyTargets
                && destroyDepth > destroyTarget && destroyRethrow > destroyDepth
                && destroyFinally > destroyRethrow && clearOwned > destroyFinally
                && clearTargets > clearOwned && zeroDepth > clearTargets);

        int previousDepth = rebuildDepth.indexOf("int previousDepthTexture = depthTexture;");
        int newDepth = rebuildDepth.indexOf("int newDepthTexture = createDepthTexture", previousDepth);
        int tryBlock = rebuildDepth.indexOf("try {", newDepth);
        int attachNew = rebuildDepth.indexOf("reattachOwnedDepthTexture(newDepthTexture);", tryBlock);
        int assignDepth = rebuildDepth.indexOf("depthTexture = newDepthTexture;", attachNew);
        int catchBlock = rebuildDepth.indexOf("catch (RuntimeException | Error exception)", assignDepth);
        int failureLocal = rebuildDepth.indexOf("Throwable failure = null;", catchBlock);
        int rollbackAttach = rebuildDepth.indexOf(
            "failure = runCleanup(failure, () -> reattachOwnedDepthTexture(previousDepthTexture));",
            failureLocal);
        int deleteNew = rebuildDepth.indexOf(
            "failure = runCleanup(failure, () -> deleteTexture(newDepthTexture));",
            rollbackAttach);
        int suppressRollback = rebuildDepth.indexOf("addSuppressedCleanupFailure(exception, failure);",
            deleteNew);
        int rethrow = rebuildDepth.indexOf("throw exception;", suppressRollback);
        int throwPostInstallCleanup = rebuildDepth.indexOf("throwPostInstallCleanupFailure(", rethrow);
        int cleanupPrevious = rebuildDepth.indexOf(
            "runCleanup(null, () -> deleteTexture(previousDepthTexture))", throwPostInstallCleanup);

        assertTrue("Depth reattachment must keep the old texture until replacement attachment succeeds",
            previousDepth >= 0 && newDepth > previousDepth && tryBlock > newDepth
                && attachNew > tryBlock && assignDepth > attachNew);
        assertTrue("Depth reattachment rollback cleanup must preserve the original attachment failure",
            catchBlock > assignDepth && failureLocal > catchBlock && rollbackAttach > failureLocal
                && deleteNew > rollbackAttach && suppressRollback > deleteNew && rethrow > suppressRollback);
        assertTrue("Old depth texture must be deleted only after the replacement is installed",
            throwPostInstallCleanup > rethrow && cleanupPrevious > throwPostInstallCleanup);
        assertFalse("Depth reattachment cleanup must not use a finally path that can mask setup failures",
            rebuildDepth.contains("boolean installed = false;"));
        assertTrue(framebufferManager.contains("private static void addSuppressedCleanupFailure"));
        assertTrue("Post-install cleanup failures must bypass resize rollback",
            framebufferManager.contains("catch (PostInstallCleanupException exception)"));
    }

    @Test
    public void legacyFramebufferManagerReplacesTexturesOnlyAfterSuccessfulAllocation() throws Exception {
        String framebufferManager = read("src/main/java/net/oculus/pipeline/framebuffer/FramebufferManager.java");
        String rebuildDepth = methodBody(framebufferManager, "private void rebuildDepthTexture");
        String rebuildNoise = methodBody(framebufferManager, "private void rebuildNoiseTexture");
        String allocate = methodBody(framebufferManager, "private void allocate");
        String destroyTarget = methodBody(framebufferManager, "private void destroy()");

        int savePreviousDepth = rebuildDepth.indexOf("int previousDepthTexture = depthTexture;");
        int createDepth = rebuildDepth.indexOf("int newDepthTexture = createDepthTexture(targetWidth, targetHeight);",
            savePreviousDepth);
        int assignDepth = rebuildDepth.indexOf("depthTexture = newDepthTexture;", createDepth);
        int postDepthCleanup = rebuildDepth.indexOf("throwPostInstallCleanupFailure(", assignDepth);
        int deletePreviousDepth = rebuildDepth.indexOf(
            "runCleanup(null, () -> deleteTexture(previousDepthTexture))", postDepthCleanup);

        int savePreviousNoise = rebuildNoise.indexOf("int previousNoiseTexture = noiseTexture;");
        int createNoise = rebuildNoise.indexOf("int newNoiseTexture = createNoiseTexture();", savePreviousNoise);
        int assignNoise = rebuildNoise.indexOf("noiseTexture = newNoiseTexture;", createNoise);
        int postNoiseCleanup = rebuildNoise.indexOf("throwPostInstallCleanupFailure(", assignNoise);
        int deletePreviousNoise = rebuildNoise.indexOf(
            "runCleanup(null, () -> deleteTexture(previousNoiseTexture))", postNoiseCleanup);

        int previousMain = allocate.indexOf("int previousMainTexture = textures[0];");
        int previousAlt = allocate.indexOf("int previousAltTexture = textures[1];", previousMain);
        int newMain = allocate.indexOf("int newMainTexture = 0;", previousAlt);
        int newAlt = allocate.indexOf("int newAltTexture = 0;", newMain);
        int tryBlock = allocate.indexOf("try {", newAlt);
        int createMain = allocate.indexOf("newMainTexture = createColorTexture(width, height);", tryBlock);
        int createAlt = allocate.indexOf("newAltTexture = createColorTexture(width, height);", createMain);
        int catchBlock = allocate.indexOf("catch (RuntimeException | Error exception)", createAlt);
        int failedMain = allocate.indexOf("final int failedMainTexture = newMainTexture;", catchBlock);
        int failedAlt = allocate.indexOf("final int failedAltTexture = newAltTexture;", failedMain);
        int failureLocal = allocate.indexOf("Throwable failure = null;", failedAlt);
        int deleteNewMain = allocate.indexOf("failure = runCleanup(failure, () -> deleteTexture(failedMainTexture));",
            failureLocal);
        int deleteNewAlt = allocate.indexOf("failure = runCleanup(failure, () -> deleteTexture(failedAltTexture));",
            deleteNewMain);
        int suppressCleanup = allocate.indexOf("addSuppressedCleanupFailure(exception, failure);", deleteNewAlt);
        int rethrow = allocate.indexOf("throw exception;", suppressCleanup);
        int assignMain = allocate.indexOf("textures[0] = newMainTexture;", rethrow);
        int assignAlt = allocate.indexOf("textures[1] = newAltTexture;", assignMain);
        int resetFlip = allocate.indexOf("flipped = false;", assignAlt);
        int oldFailureLocal = allocate.indexOf("Throwable failure = null;", resetFlip);
        int deleteOldMain = allocate.indexOf("failure = runCleanup(failure, () -> deleteTexture(previousMainTexture));",
            oldFailureLocal);
        int deleteOldAlt = allocate.indexOf("failure = runCleanup(failure, () -> deleteTexture(previousAltTexture));",
            deleteOldMain);
        int rethrowOldCleanup = allocate.indexOf("rethrowCleanupFailure(failure);", deleteOldAlt);
        int captureMain = destroyTarget.indexOf("final int mainTexture = textures[0];");
        int captureAlt = destroyTarget.indexOf("final int altTexture = textures[1];", captureMain);
        int clearMain = destroyTarget.indexOf("textures[0] = 0;", captureAlt);
        int clearAlt = destroyTarget.indexOf("textures[1] = 0;", clearMain);
        int clearFlip = destroyTarget.indexOf("flipped = false;", clearAlt);
        int destroyFailure = destroyTarget.indexOf("Throwable failure = null;", clearFlip);
        int destroyMain = destroyTarget.indexOf("failure = runCleanup(failure, () -> deleteTexture(mainTexture));",
            destroyFailure);
        int destroyAlt = destroyTarget.indexOf("failure = runCleanup(failure, () -> deleteTexture(altTexture));",
            destroyMain);
        int destroyRethrow = destroyTarget.indexOf("rethrowCleanupFailure(failure);", destroyAlt);

        assertTrue("Legacy depth rebuild must keep the old texture until replacement allocation succeeds",
            savePreviousDepth >= 0 && createDepth > savePreviousDepth && assignDepth > createDepth
                && postDepthCleanup > assignDepth && deletePreviousDepth > postDepthCleanup);
        assertTrue("Legacy noise rebuild must keep the old texture until replacement allocation succeeds",
            savePreviousNoise >= 0 && createNoise > savePreviousNoise && assignNoise > createNoise
                && postNoiseCleanup > assignNoise && deletePreviousNoise > postNoiseCleanup);
        assertTrue("Legacy framebuffer manager must treat old texture delete failures as post-install cleanup",
            framebufferManager.contains("private static void throwPostInstallCleanupFailure")
                && framebufferManager.contains("private static final class PostInstallCleanupException extends RuntimeException")
                && framebufferManager.contains("Failed to delete replaced legacy framebuffer depth texture")
                && framebufferManager.contains("Failed to delete replaced legacy framebuffer noise texture"));
        assertTrue("Legacy render target allocation must remember old paired textures before allocating replacements",
            previousMain >= 0 && previousAlt > previousMain);
        assertTrue(newMain > previousAlt && newAlt > newMain);
        assertTrue("Legacy render target allocation must create replacement pair inside a guarded block",
            tryBlock > newAlt && createMain > tryBlock && createAlt > createMain);
        assertTrue("Legacy render target allocation must snapshot failed texture names before cleanup lambdas",
            failedMain > catchBlock && failedAlt > failedMain && failureLocal > failedAlt);
        assertTrue("Legacy render target allocation must delete any completed replacement texture before rethrowing",
            deleteNewMain > failureLocal && deleteNewAlt > deleteNewMain);
        assertTrue("Legacy render target allocation cleanup failures must suppress onto the allocation failure",
            suppressCleanup > deleteNewAlt && rethrow > suppressCleanup);
        assertTrue("Legacy render target allocation must install the replacement pair only after both textures succeed",
            assignMain > rethrow && assignAlt > assignMain);
        assertTrue("Legacy render target allocation must reset flip state before deleting old textures",
            resetFlip > assignAlt);
        assertTrue("Legacy render target allocation must aggregate old texture cleanup after replacement install",
            oldFailureLocal > resetFlip && deleteOldMain > oldFailureLocal && deleteOldAlt > deleteOldMain
                && rethrowOldCleanup > deleteOldAlt);
        assertTrue("Legacy render target destroy must capture texture ids before clearing Java-side state",
            captureMain >= 0 && captureAlt > captureMain && clearMain > captureAlt
                && clearAlt > clearMain && clearFlip > clearAlt);
        assertTrue("Legacy render target destroy must delete captured texture ids after stale state is cleared",
            destroyFailure > clearFlip && destroyMain > destroyFailure && destroyAlt > destroyMain
                && destroyRethrow > destroyAlt);
    }

    @Test
    public void legacyFramebufferManagerPrepareCleansPartialSetupWithoutRollingBackPostInstallCleanup() throws Exception {
        String framebufferManager = read("src/main/java/net/oculus/pipeline/framebuffer/FramebufferManager.java");
        String prepare = methodBody(framebufferManager, "public void prepareGbuffers()");

        int tryBlock = prepare.indexOf("try {");
        int rebuildTargets = prepare.indexOf("rebuildTargets(indices, displayWidth, displayHeight);", tryBlock);
        int rebuildDepth = prepare.indexOf("rebuildDepthTexture(displayWidth, displayHeight);", rebuildTargets);
        int rebuildNoise = prepare.indexOf("rebuildNoiseTexture();", rebuildDepth);
        int registerDepth = prepare.indexOf("registerDepthAliases();", rebuildNoise);
        int registerNoise = prepare.indexOf("registerNoiseAliases();", registerDepth);
        int postInstallCatch = prepare.indexOf("catch (PostInstallCleanupException exception)", registerNoise);
        int postInstallThrow = prepare.indexOf("throw exception;", postInstallCatch);
        int setupCatch = prepare.indexOf("catch (RuntimeException | Error exception)", postInstallThrow);
        int destroyTry = prepare.indexOf("try {", setupCatch);
        int destroy = prepare.indexOf("destroy();", destroyTry);
        int cleanupCatch = prepare.indexOf("catch (RuntimeException | Error cleanupException)", destroy);
        int selfSuppressionGuard = prepare.indexOf("if (cleanupException != exception)", cleanupCatch);
        int suppressCleanup = prepare.indexOf("exception.addSuppressed(cleanupException);", selfSuppressionGuard);
        int rethrow = prepare.indexOf("throw exception;", suppressCleanup);

        assertTrue("Legacy framebuffer prepare must guard all allocation and alias registration as one setup unit",
            tryBlock >= 0 && rebuildTargets > tryBlock && rebuildDepth > rebuildTargets
                && rebuildNoise > rebuildDepth && registerDepth > rebuildNoise && registerNoise > registerDepth);
        assertTrue("Post-install cleanup failures happen after replacement state is live and must bypass setup teardown",
            postInstallCatch > registerNoise && postInstallThrow > postInstallCatch && setupCatch > postInstallThrow);
        assertTrue("Setup failures must destroy partial targets, depth, noise, and bindings before rethrowing",
            destroyTry > setupCatch && destroy > destroyTry && cleanupCatch > destroy
                && selfSuppressionGuard > cleanupCatch && suppressCleanup > selfSuppressionGuard
                && rethrow > suppressCleanup);
    }

    @Test
    public void legacyFramebufferManagerTextureSetupUsesReferenceStyleBindWrappers() throws Exception {
        String framebufferManager = read("src/main/java/net/oculus/pipeline/framebuffer/FramebufferManager.java");
        String textureSetup = framebufferManager.substring(
            framebufferManager.indexOf("private static int createColorTexture"),
            framebufferManager.indexOf("private static void deleteTexture"));

        assertTrue(textureSetup.contains("OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(textureSetup.contains("OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D"));
        assertTrue(textureSetup.contains("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {"));
        assertFalse(textureSetup.contains("GlStateManager.bindTexture(0);"));
        assertTrue(textureSetup.contains("int size = Math.max(1, directives.getNoiseTextureResolution());"));
        assertTrue(textureSetup.contains("Random random = new Random(0L);"));
        assertTrue(textureSetup.contains("for (int x = 0; x < size; x++)"));
        assertTrue(textureSetup.contains("for (int y = 0; y < size; y++)"));
        assertTrue(textureSetup.contains("int offset = ((y * size) + x) * 4;"));
        assertFalse(textureSetup.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(textureSetup.contains("GL11.glTexImage2D"));
        assertFalse(textureSetup.contains("GL11.glTexParameteri(GL11.GL_TEXTURE_2D"));
        assertFalse(textureSetup.contains("TextureLifecycleTracker.onTexImage2D"));
        assertFalse(framebufferManager.contains("private static final int NOISE_SIZE"));
        assertFalse(framebufferManager.contains("private final Random noiseRandom"));
    }

    @Test
    public void legacyFramebufferManagerUnregistersOnlyOwnedSamplerAliasesOnReplacementAndDestroy() throws Exception {
        String framebufferManager = read("src/main/java/net/oculus/pipeline/framebuffer/FramebufferManager.java");
        String destroy = methodBody(framebufferManager, "public void destroy()");
        String registerBinding = methodBody(framebufferManager,
            "private void registerBinding(String alias, TextureBinding binding)");
        String unregisterBindings = methodBody(framebufferManager, "private void unregisterBindings()");

        int unregisterCall = destroy.indexOf("failure = runCleanup(failure, this::unregisterBindings);");
        int destroyTargets = destroy.indexOf("failure = runCleanup(failure, target::destroy);", unregisterCall);
        int rememberBinding = registerBinding.indexOf(
            "TextureBinding previousBinding = registeredBindings.put(alias, binding);");
        int previousGuard = registerBinding.indexOf("if (previousBinding != null)", rememberBinding);
        int unregisterPrevious = registerBinding.indexOf(
            "TextureBindingRegistry.unregister(alias, previousBinding);", previousGuard);
        int registerGlobal = registerBinding.indexOf("TextureBindingRegistry.register(alias, binding);",
            unregisterPrevious);
        int failureLocal = unregisterBindings.indexOf("Throwable failure = null;");
        int unregisterLoop = unregisterBindings.indexOf(
            "for (Map.Entry<String, TextureBinding> entry : registeredBindings.entrySet())", failureLocal);
        int aggregateUnregister = unregisterBindings.indexOf(
            "failure = runCleanup(failure,", unregisterLoop);
        int identityUnregister = unregisterBindings.indexOf(
            "TextureBindingRegistry.unregister(entry.getKey(), entry.getValue())", aggregateUnregister);
        int rethrowUnregister = unregisterBindings.indexOf("rethrowCleanupFailure(failure);", identityUnregister);
        int finallyUnregister = unregisterBindings.indexOf("} finally {", rethrowUnregister);
        int clearOwned = unregisterBindings.indexOf("registeredBindings.clear();", finallyUnregister);

        assertTrue(framebufferManager.contains("private final Map<String, TextureBinding> registeredBindings;"));
        assertTrue("Legacy framebuffer aliases must unregister before owned textures are deleted",
            unregisterCall >= 0 && destroyTargets > unregisterCall);
        assertTrue("Alias registration must store the exact binding identity it installed",
            rememberBinding >= 0);
        assertTrue("Alias replacement must unregister the old owned binding identity before publishing the new one",
            previousGuard > rememberBinding && unregisterPrevious > previousGuard && registerGlobal > unregisterPrevious);
        assertTrue("Alias teardown must attempt every owned binding through the cleanup aggregator",
            failureLocal >= 0 && unregisterLoop > failureLocal && aggregateUnregister > unregisterLoop
                && identityUnregister > aggregateUnregister);
        assertTrue("Alias teardown must remove only this manager's owned binding identity",
            identityUnregister >= 0 && rethrowUnregister > identityUnregister
                && finallyUnregister > rethrowUnregister && clearOwned > finallyUnregister);
        assertTrue("Alias teardown must clear owned identity handles even when unregister cleanup fails",
            clearOwned > finallyUnregister);
        assertFalse("Legacy framebuffer teardown must not clear unrelated global sampler aliases",
            framebufferManager.contains("TextureBindingRegistry.clear();"));
    }

    @Test
    public void legacyFramebufferManagerDestroyAggregatesCleanupFailures() throws Exception {
        String framebufferManager = read("src/main/java/net/oculus/pipeline/framebuffer/FramebufferManager.java");
        String destroy = methodBody(framebufferManager, "public void destroy()");

        int failureLocal = destroy.indexOf("Throwable failure = null;");
        int unregister = destroy.indexOf("failure = runCleanup(failure, this::unregisterBindings);", failureLocal);
        int targetLoop = destroy.indexOf("for (RenderTarget target : renderTargets.values())", unregister);
        int targetDestroy = destroy.indexOf("failure = runCleanup(failure, target::destroy);", targetLoop);
        int deleteDepth = destroy.indexOf("failure = runCleanup(failure, () -> deleteTexture(depthTexture));",
            targetDestroy);
        int deleteNoise = destroy.indexOf("failure = runCleanup(failure, () -> deleteTexture(noiseTexture));",
            deleteDepth);
        int shadowGuard = destroy.indexOf("if (shadowMap != null)", deleteNoise);
        int destroyShadow = destroy.indexOf("failure = runCleanup(failure, shadowMap::destroy);", shadowGuard);
        int rethrow = destroy.indexOf("rethrowCleanupFailure(failure);", destroyShadow);
        int finallyCleanup = destroy.indexOf("} finally {", rethrow);
        int clearTargets = destroy.indexOf("renderTargets.clear();", finallyCleanup);
        int zeroDepth = destroy.indexOf("depthTexture = 0;", clearTargets);
        int zeroNoise = destroy.indexOf("noiseTexture = 0;", zeroDepth);
        int clearShadow = destroy.indexOf("shadowMap = null;", zeroNoise);
        int markDestroyed = destroy.indexOf("destroyed = true;", clearShadow);

        assertTrue("Legacy framebuffer manager destroy must start aggregating before owned cleanup",
            failureLocal >= 0 && unregister > failureLocal);
        assertTrue("Legacy framebuffer manager destroy must attempt targets after sampler unregister",
            targetLoop > unregister && targetDestroy > targetLoop);
        assertTrue("Legacy framebuffer manager destroy must delete depth and noise textures before shadow teardown",
            deleteDepth > targetDestroy && deleteNoise > deleteDepth && shadowGuard > deleteNoise);
        assertTrue("Legacy framebuffer manager destroy must attempt shadow teardown before clearing cached state",
            destroyShadow > shadowGuard);
        assertTrue("Legacy framebuffer manager destroy must rethrow only after all cleanup paths are attempted",
            rethrow > destroyShadow);
        assertTrue("Legacy framebuffer manager must clear owned state even when cleanup fails",
            finallyCleanup > rethrow && clearTargets > finallyCleanup && zeroDepth > clearTargets && zeroNoise > zeroDepth
                && clearShadow > zeroNoise && markDestroyed > clearShadow);
        assertTrue(framebufferManager.contains("private static Throwable runCleanup(Throwable failure, Runnable cleanup)"));
        assertTrue(framebufferManager.contains("failure.addSuppressed(exception);"));
        assertTrue(framebufferManager.contains("throw (RuntimeException) failure;"));
        assertTrue(framebufferManager.contains("throw (Error) failure;"));
    }

    @Test
    public void referenceFramebufferHelperSurfaceIsPresent() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String createMain = methodBody(source, "public GlFramebuffer createFramebufferWritingToMain");
        String createAlt = methodBody(source, "public GlFramebuffer createFramebufferWritingToAlt");
        String createClear = methodBody(source, "public GlFramebuffer createClearFramebuffer");
        String createFull = methodBody(source, "private GlFramebuffer createFullFramebuffer");
        String createGbuffer = methodBody(source, "public GlFramebuffer createGbufferFramebuffer");
        String createWithDepth = methodBody(source, "public GlFramebuffer createColorFramebufferWithDepth(Set<Integer> stageWritesToMain");

        assertTrue(source.contains("public int getDepthTexture()"));
        assertTrue(source.contains("public int getCurrentWidth()"));
        assertTrue(source.contains("public int getCurrentHeight()"));
        assertTrue(createMain.contains("return createFullFramebuffer(false, drawBuffers);"));
        assertTrue(createAlt.contains("return createFullFramebuffer(true, drawBuffers);"));
        assertTrue(createClear.contains("if (!alt)"));
        assertTrue(createClear.contains("stageWritesToMain = invert(Collections.emptySet(), clearBuffers);"));
        assertTrue(createClear.contains("return createColorFramebuffer(stageWritesToMain, clearBuffers);"));
        assertTrue(createFull.contains("return createEmptyFramebuffer();"));
        assertTrue(createFull.contains("if (!clearsAlt)"));
        assertTrue(createFull.contains("stageWritesToMain = invert(Collections.emptySet(), drawBuffers);"));
        assertTrue(createFull.contains("return createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);"));
        assertTrue(createGbuffer.contains("return createColorFramebufferWithDepth(invert(stageWritesToAlt, drawBuffers), drawBuffers);"));
        assertTrue(createWithDepth.contains("GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);"));
        assertTrue(createWithDepth.contains("framebuffer.addDepthAttachment(currentDepthTexture);"));
        assertTrue(createWithDepth.contains(
            "verifyComplete(framebuffer, \"Render target framebuffer with depth for draw buffers \""));
    }

    @Test
    public void emptyGbufferFramebufferUsesColortex0WithNoDrawBuffersLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String createGbuffer = methodBody(source, "public GlFramebuffer createGbufferFramebuffer");
        String createWithDepth = methodBody(source,
            "public GlFramebuffer createColorFramebufferWithDepth(Set<Integer> stageWritesToMain");
        String createFull = methodBody(source, "private GlFramebuffer createFullFramebuffer");
        String createEmpty = methodBody(source, "private GlFramebuffer createEmptyFramebuffer");

        int gbufferEmpty = createGbuffer.indexOf("if (drawBuffers.length == 0)");
        int gbufferFallback = createGbuffer.indexOf("return createEmptyFramebuffer();", gbufferEmpty);
        int withDepthEmpty = createWithDepth.indexOf("if (drawBuffers.length == 0)");
        int withDepthFallback = createWithDepth.indexOf("return createEmptyFramebuffer();", withDepthEmpty);
        int fullEmpty = createFull.indexOf("if (drawBuffers.length == 0)");
        int fullFallback = createFull.indexOf("return createEmptyFramebuffer();", fullEmpty);

        int targetLookup = createEmpty.indexOf("RenderTarget target = get(0);");
        int targetGuard = createEmpty.indexOf("if (target == null)", targetLookup);
        int failClearly = createEmpty.indexOf(
            "throw new IllegalStateException(\"Cannot create empty framebuffer without colortex0\");",
            targetGuard);
        int createFramebuffer = createEmpty.indexOf("GlFramebuffer framebuffer = createOwnedFramebuffer();",
            failClearly);
        int tryBlock = createEmpty.indexOf("try {", createFramebuffer);
        int depthAttachment = createEmpty.indexOf("framebuffer.addDepthAttachment(currentDepthTexture);", tryBlock);
        int colorAttachment = createEmpty.indexOf("framebuffer.addColorAttachment(0, target.getMainTexture());",
            depthAttachment);
        int noDrawBuffers = createEmpty.indexOf("framebuffer.noDrawBuffers();", colorAttachment);
        int verify = createEmpty.indexOf("verifyComplete(framebuffer, \"Render target empty framebuffer\");",
            noDrawBuffers);
        int returnFramebuffer = createEmpty.indexOf("return framebuffer;", verify);
        int catchBlock = createEmpty.indexOf("catch (RuntimeException | Error exception)", returnFramebuffer);
        int destroyOwned = createEmpty.indexOf("destroyFramebuffer(null, framebuffer)", catchBlock);

        assertTrue("Gbuffer framebuffer creation must route empty draw-buffer stages to the empty framebuffer path",
            gbufferEmpty >= 0 && gbufferFallback > gbufferEmpty);
        assertTrue("Color-with-depth framebuffer creation must share the empty framebuffer path",
            withDepthEmpty >= 0 && withDepthFallback > withDepthEmpty);
        assertTrue("Full framebuffer creation must share the empty framebuffer path",
            fullEmpty >= 0 && fullFallback > fullEmpty);
        assertTrue("The empty framebuffer path must require colortex0 before allocating a wrapper",
            targetLookup >= 0 && targetGuard > targetLookup && failClearly > targetGuard
                && createFramebuffer > failClearly);
        assertTrue("The empty framebuffer path must use guarded ownership before GL attachment setup can fail",
            tryBlock > createFramebuffer);
        assertTrue("The empty framebuffer must attach depth and colortex0 before disabling draw buffers",
            depthAttachment > tryBlock && colorAttachment > depthAttachment && noDrawBuffers > colorAttachment);
        assertTrue("The empty framebuffer must validate completeness before returning",
            verify > noDrawBuffers && returnFramebuffer > verify);
        assertTrue("Failed empty framebuffer setup must destroy the owned wrapper before rethrowing",
            catchBlock > returnFramebuffer && destroyOwned > catchBlock);
    }

    @Test
    public void colorFramebufferUsesSequentialAttachmentsAndLogicalDrawBuffersLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/rendertarget/RenderTargets.java");
        String createColor = methodBody(source,
            "public GlFramebuffer createColorFramebuffer(Set<Integer> stageWritesToMain");
        String logicalDrawBuffers = methodBody(source, "private static int[] logicalDrawBuffers");

        int emptyGuard = createColor.indexOf("if (drawBuffers.length == 0)");
        int createFramebuffer = createColor.indexOf("GlFramebuffer framebuffer = createOwnedFramebuffer();", emptyGuard);
        int tryBlock = createColor.indexOf("try {", createFramebuffer);
        int loop = createColor.indexOf("for (int i = 0; i < drawBuffers.length; i++)", tryBlock);
        int bufferIndex = createColor.indexOf("int bufferIndex = drawBuffers[i];", loop);
        int targetLookup = createColor.indexOf("RenderTarget target = get(bufferIndex);", bufferIndex);
        int missingTargetGuard = createColor.indexOf("if (target == null)", targetLookup);
        int textureChoice = createColor.indexOf(
            "int texture = stageWritesToMain.contains(bufferIndex) ? target.getMainTexture() : target.getAltTexture();",
            missingTargetGuard);
        int addAttachment = createColor.indexOf("framebuffer.addColorAttachment(i, texture);", textureChoice);
        int drawBuffers = createColor.indexOf("framebuffer.drawBuffers(logicalDrawBuffers(drawBuffers.length));",
            addAttachment);
        int readBuffer = createColor.indexOf("framebuffer.readBuffer(0);", drawBuffers);
        int verify = createColor.indexOf(
            "verifyComplete(framebuffer, \"Render target color framebuffer for draw buffers \"", readBuffer);
        int returnFramebuffer = createColor.indexOf("return framebuffer;", verify);

        assertTrue("RenderTargets must reject empty color framebuffers before allocating a wrapper",
            emptyGuard >= 0 && createFramebuffer > emptyGuard);
        assertTrue("Owned framebuffer tracking must be guarded before attachment setup can fail",
            tryBlock > createFramebuffer);
        assertTrue("Framebuffer attachments must iterate the shader draw-buffer list in declaration order",
            loop > tryBlock && bufferIndex > loop);
        assertTrue("Each shader draw buffer must resolve to a configured render target before attachment",
            targetLookup > bufferIndex && missingTargetGuard > targetLookup && textureChoice > missingTargetGuard);
        assertTrue("Color attachments must use dense attachment slots, not the shader colortex index",
            addAttachment > textureChoice);
        assertTrue("Draw buffers must use dense logical attachment IDs and then select read buffer 0 before validation",
            drawBuffers > addAttachment && readBuffer > drawBuffers && verify > readBuffer);
        assertTrue("The framebuffer must only be returned after completeness validation",
            returnFramebuffer > verify);
        assertFalse("Sparse shader colortex IDs must not be used directly as GL color attachment slots",
            createColor.contains("framebuffer.addColorAttachment(bufferIndex"));

        assertTrue(logicalDrawBuffers.contains("int[] buffers = new int[count];"));
        assertTrue(logicalDrawBuffers.contains("for (int i = 0; i < count; i++)"));
        assertTrue(logicalDrawBuffers.contains("buffers[i] = i;"));
        assertTrue(logicalDrawBuffers.contains("return buffers;"));
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

    private static void assertSingleTextureCreationCleanup(String body, String context) {
        int allocate = body.indexOf("int texture = createTexture(");
        int tryBlock = body.indexOf("try {", allocate);
        int restoreWrapper = body.indexOf("OculusRenderSystem.withDefaultTextureBindingRestored(() -> {",
            tryBlock);
        int returnTexture = body.indexOf("return texture;", restoreWrapper);
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", returnTexture);
        int cleanup = body.indexOf("runCleanup(null, () -> deleteTexture(texture))", catchBlock);
        int suppressCleanup = body.indexOf("addSuppressedCleanupFailure(exception,", catchBlock);
        int rethrow = body.indexOf("throw exception;", suppressCleanup);

        assertTrue(context + " must generate and validate a texture name before setup", allocate >= 0);
        assertTrue(context + " setup must be guarded", tryBlock > allocate);
        assertTrue(context + " must restore texture binding state during setup", restoreWrapper > tryBlock);
        assertTrue(context + " must return only after restoring texture binding", returnTexture > restoreWrapper);
        assertTrue(context + " must catch setup failures after the successful return path", catchBlock > returnTexture);
        assertTrue(context + " must delete the generated texture if setup fails", cleanup > catchBlock);
        assertTrue(context + " cleanup failures must suppress onto the original setup failure",
            suppressCleanup > catchBlock && rethrow > suppressCleanup);
    }

    private static void assertConstructionCleanup(String body, String context) {
        int tryBlock = body.indexOf("try {");
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", tryBlock);
        int destroy = body.indexOf("destroyFramebuffer(null, framebuffer)", catchBlock);
        int suppress = body.indexOf("addSuppressedCleanupFailure(exception,", catchBlock);
        int rethrow = body.indexOf("throw exception;", suppress);

        assertTrue(context + " must enter a guarded construction block", tryBlock >= 0);
        assertTrue(context + " must catch RuntimeException and Error construction failures",
            catchBlock > tryBlock);
        assertTrue(context + " must destroy the owned framebuffer if setup fails", destroy > catchBlock);
        assertTrue(context + " must suppress framebuffer cleanup failures onto the setup failure",
            suppress > catchBlock && rethrow > suppress);
    }

    private static void assertOwnedFramebufferRegistrationCleanup(String body) {
        int allocation = body.indexOf("GlFramebuffer framebuffer = new GlFramebuffer();");
        int tryBlock = body.indexOf("try {", allocation);
        int register = body.indexOf("ownedFramebuffers.add(framebuffer);", tryBlock);
        int returnFramebuffer = body.indexOf("return framebuffer;", register);
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", returnFramebuffer);
        int destroyRaw = body.indexOf("destroyFramebufferResource(null, framebuffer)", catchBlock);
        int suppress = body.indexOf("addSuppressedCleanupFailure(exception,", catchBlock);
        int rethrow = body.indexOf("throw exception;", suppress);

        assertTrue("Owned framebuffer allocation must create the GL resource before guarded registration",
            allocation >= 0 && tryBlock > allocation);
        assertTrue("Owned framebuffer registration must happen inside the guarded block",
            register > tryBlock && returnFramebuffer > register);
        assertTrue("Failed owned framebuffer registration must destroy the raw GL framebuffer",
            catchBlock > returnFramebuffer && destroyRaw > catchBlock);
        assertTrue("Registration cleanup failures must suppress onto the registration failure",
            suppress > catchBlock && rethrow > suppress);
    }

    private static void assertTextureDeleteNotifiesFromFinally(String body, String textureVariable, String context) {
        int positiveGuard = body.indexOf("if (" + textureVariable + " <= 0)");
        int failureLocal = body.indexOf("Throwable failure = null;", positiveGuard);
        int deleteTry = body.indexOf("try {", failureLocal);
        int glDelete = body.indexOf("GL11.glDeleteTextures(" + textureVariable + ");", deleteTry);
        int deleteCatch = body.indexOf("catch (RuntimeException | Error exception)", glDelete);
        int recordDeleteFailure = body.indexOf("failure = addCleanupFailure(failure, exception);", deleteCatch);
        int finallyBlock = body.indexOf("finally {", recordDeleteFailure);
        int lifecycleTry = body.indexOf("try {", finallyBlock);
        int lifecycleNotify = body.indexOf(
            "TextureLifecycleTracker.onDeleteTexture(" + textureVariable + ");", lifecycleTry);
        int lifecycleCatch = body.indexOf("catch (RuntimeException | Error exception)", lifecycleNotify);
        int recordLifecycleFailure = body.indexOf("failure = addCleanupFailure(failure, exception);",
            lifecycleCatch);
        int rethrow = body.indexOf("rethrowCleanupFailure(failure);", recordLifecycleFailure);

        assertTrue(context + " must skip invalid texture names before cleanup", positiveGuard >= 0);
        assertTrue(context + " must record GL delete failures", failureLocal > positiveGuard
            && deleteTry > failureLocal && glDelete > deleteTry && deleteCatch > glDelete
            && recordDeleteFailure > deleteCatch);
        assertTrue(context + " must notify texture lifecycle from finally after GL delete is attempted",
            finallyBlock > recordDeleteFailure && lifecycleTry > finallyBlock
                && lifecycleNotify > lifecycleTry);
        assertTrue(context + " must aggregate lifecycle notification failures before rethrowing",
            lifecycleCatch > lifecycleNotify && recordLifecycleFailure > lifecycleCatch
                && rethrow > recordLifecycleFailure);
    }
}
