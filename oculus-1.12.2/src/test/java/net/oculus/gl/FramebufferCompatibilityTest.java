package net.oculus.gl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

public class FramebufferCompatibilityTest {
    private static final List<String> FORBIDDEN_DIRECT_GL30_FBO_CALLS = Arrays.asList(
        "GL30.glBindFramebuffer(",
        "GL30.glGenFramebuffers(",
        "GL30.glFramebufferTexture2D(",
        "GL30.glCheckFramebufferStatus(",
        "GL30.glDeleteFramebuffers("
    );
    private static final List<String> FORBIDDEN_SPLIT_FRAMEBUFFER_TARGETS = Arrays.asList(
        "OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER",
        "OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER"
    );
    private static final List<String> FORBIDDEN_DIRECT_DRAW_BUFFER_CALLS = Arrays.asList(
        "GL20.glDrawBuffers(",
        "ARBDrawBuffers.glDrawBuffersARB("
    );

    @Test
    public void activeFramebufferCodeDoesNotBypassMinecraft112FramebufferDispatch() throws IOException {
        StringBuilder violations = new StringBuilder();

        scanRoot(Paths.get("src/main/java/net/oculus"), violations);
        scanRoot(Paths.get("src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer"), violations);

        assertFalse(
            "Use OpenGlHelper framebuffer dispatch on 1.12.2 so BASE/ARB/EXT FBO modes all attach to the bound FBO:\n"
                + violations,
            violations.length() > 0);
    }

    @Test
    public void depthAttachmentsUseTrackedTextureFormatForCombinedStencilParity() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);

        assertTrue(source.contains("TextureInfoCache.INSTANCE.getInfo(texture).getInternalFormat();"));
        assertTrue(source.contains("DepthBufferFormat.fromGlEnumOrDefault(internalFormat);"));
        assertTrue(source.contains("depthBufferFormat.isCombinedStencil()"));
        assertTrue(source.contains("GL30.GL_DEPTH_STENCIL_ATTACHMENT"));
        assertTrue(source.contains(": OpenGlHelper.GL_DEPTH_ATTACHMENT;"));
        assertTrue(source.contains("OpenGlHelper.glFramebufferTexture2D("));
    }

    @Test
    public void activeFramebufferWrapperFailsFastWhenFramebufferNameAllocationFails() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);
        String constructor = source.substring(
            source.indexOf("public GlFramebuffer()"),
            source.indexOf("public void addDepthAttachment"));
        String createFramebuffer = methodBody(source, "private static int createFramebuffer");

        int superCall = constructor.indexOf("super(createFramebuffer());");
        int allocate = createFramebuffer.indexOf("int framebuffer = OpenGlHelper.glGenFramebuffers();");
        int guard = createFramebuffer.indexOf("if (framebuffer <= 0)", allocate);
        int fail = createFramebuffer.indexOf(
            "throw new IllegalStateException(\"Failed to create framebuffer\");", guard);
        int returnFramebuffer = createFramebuffer.indexOf("return framebuffer;", fail);

        assertTrue("GlFramebuffer construction must validate the generated framebuffer name before use",
            superCall >= 0);
        assertTrue("Framebuffer allocation must fail clearly when OpenGlHelper returns no framebuffer name",
            allocate >= 0 && guard > allocate && fail > guard && returnFramebuffer > fail);
    }

    @Test
    public void depthAttachmentSwitchingDetachesStaleAttachmentPointOnLegacyFramebufferBackend() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);
        String addDepthAttachment = source.substring(
            source.indexOf("public void addDepthAttachment"),
            source.indexOf("public void addColorAttachment"));

        int field = source.indexOf("private int depthAttachment;");
        int switchGuard = addDepthAttachment.indexOf("if (hasDepthAttachment && depthAttachment != attachment)");
        int detachAttachment = addDepthAttachment.indexOf("depthAttachment,", switchGuard);
        int detach = addDepthAttachment.lastIndexOf("OpenGlHelper.glFramebufferTexture2D(",
            detachAttachment);
        int detachTextureTarget = addDepthAttachment.indexOf("GL11.GL_TEXTURE_2D,", detachAttachment);
        int detachTextureZero = addDepthAttachment.indexOf("0,", detachTextureTarget);
        int attachNewAttachment = addDepthAttachment.indexOf("attachment,", detachTextureZero);
        int attachNew = addDepthAttachment.lastIndexOf("OpenGlHelper.glFramebufferTexture2D(",
            attachNewAttachment);
        int recordAttachment = addDepthAttachment.indexOf("depthAttachment = attachment;", attachNew);

        assertTrue("GlFramebuffer must remember the active depth attachment point", field >= 0);
        assertTrue("GlFramebuffer must detect depth-only versus depth-stencil attachment changes",
            switchGuard >= 0);
        assertTrue("GlFramebuffer must detach the stale depth attachment through OpenGlHelper",
            detach > switchGuard);
        assertTrue("GlFramebuffer must detach by attaching texture 0 before adding the new depth texture",
            detachTextureTarget > detachAttachment && detachTextureZero > detachTextureTarget);
        assertTrue("GlFramebuffer must attach the new depth texture after stale attachment cleanup",
            attachNew > detachTextureZero);
        assertTrue("GlFramebuffer must remember the new depth attachment point",
            recordAttachment > attachNew);
    }

    @Test
    public void depthAttachmentSwitchingRollsBackPreviousAttachmentOnSetupFailure() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);
        String addDepthAttachment = methodBody(source, "public void addDepthAttachment");
        String rollback = methodBody(source, "private Throwable rollbackDepthAttachment");

        int depthTextureField = source.indexOf("private int depthTexture;");
        int previousHasDepth = addDepthAttachment.indexOf("boolean previousHasDepthAttachment = hasDepthAttachment;");
        int previousAttachment = addDepthAttachment.indexOf("int previousDepthAttachment = depthAttachment;",
            previousHasDepth);
        int previousTexture = addDepthAttachment.indexOf("int previousDepthTexture = depthTexture;",
            previousAttachment);
        int framebufferBoundLocal = addDepthAttachment.indexOf("boolean framebufferBound = false;", previousTexture);
        int bindFramebuffer = addDepthAttachment.indexOf("bind();", framebufferBoundLocal);
        int markFramebufferBound = addDepthAttachment.indexOf("framebufferBound = true;", bindFramebuffer);
        int attachTextureArg = addDepthAttachment.indexOf("texture,", markFramebufferBound);
        int attachNew = addDepthAttachment.lastIndexOf("OpenGlHelper.glFramebufferTexture2D(",
            attachTextureArg);
        int recordHasDepth = addDepthAttachment.indexOf("hasDepthAttachment = true;", attachNew);
        int recordAttachment = addDepthAttachment.indexOf("depthAttachment = attachment;", recordHasDepth);
        int recordTexture = addDepthAttachment.indexOf("depthTexture = texture;", recordAttachment);
        int catchBlock = addDepthAttachment.indexOf("catch (RuntimeException | Error exception)", recordTexture);
        int recordFailure = addDepthAttachment.indexOf("failure = exception;", catchBlock);
        int rollbackGuard = addDepthAttachment.indexOf("if (framebufferBound)", recordFailure);
        int suppressRollback = addDepthAttachment.indexOf(
            "addSuppressedCleanupFailure(exception, rollbackDepthAttachment(null, previousHasDepthAttachment,",
            rollbackGuard);
        int rethrow = addDepthAttachment.indexOf("throw exception;", suppressRollback);

        assertTrue("GlFramebuffer must track the active depth texture for rollback", depthTextureField >= 0);
        assertTrue("Depth attachment setup must snapshot previous depth attachment state before mutating the FBO",
            previousHasDepth >= 0 && previousAttachment > previousHasDepth && previousTexture > previousAttachment);
        assertTrue("Depth attachment setup must only enable rollback after binding this framebuffer",
            framebufferBoundLocal > previousTexture && bindFramebuffer > framebufferBoundLocal
                && markFramebufferBound > bindFramebuffer);
        assertTrue("Depth attachment setup must only record the replacement after GL attachment succeeds",
            attachTextureArg > markFramebufferBound && attachNew > markFramebufferBound && recordHasDepth > attachNew
                && recordAttachment > recordHasDepth && recordTexture > recordAttachment);
        assertTrue("Depth attachment setup failures must rollback the previous attachment only if this FBO was bound",
            catchBlock > recordTexture && recordFailure > catchBlock
                && rollbackGuard > recordFailure && suppressRollback > rollbackGuard && rethrow > suppressRollback);

        int noPreviousGuard = rollback.indexOf("if (!previousHasDepthAttachment || previousDepthTexture <= 0)");
        int clearHasDepth = rollback.indexOf("hasDepthAttachment = false;", noPreviousGuard);
        int clearAttachment = rollback.indexOf("depthAttachment = 0;", clearHasDepth);
        int clearTexture = rollback.indexOf("depthTexture = 0;", clearAttachment);
        int reattach = rollback.indexOf("OpenGlHelper.glFramebufferTexture2D(", clearTexture);
        int previousAttachmentArg = rollback.indexOf("previousDepthAttachment,", reattach);
        int previousTextureArg = rollback.indexOf("previousDepthTexture,", previousAttachmentArg);
        int restoreHasDepth = rollback.indexOf("hasDepthAttachment = true;", previousTextureArg);
        int restoreAttachment = rollback.indexOf("depthAttachment = previousDepthAttachment;", restoreHasDepth);
        int restoreTexture = rollback.indexOf("depthTexture = previousDepthTexture;", restoreAttachment);
        int cleanupCatch = rollback.indexOf("catch (RuntimeException | Error exception)", restoreTexture);
        int recordCleanupFailure = rollback.indexOf("failure = addCleanupFailure(failure, exception);", cleanupCatch);

        assertTrue("Rollback must clear internal state when there was no previous depth attachment",
            noPreviousGuard >= 0 && clearHasDepth > noPreviousGuard
                && clearAttachment > clearHasDepth && clearTexture > clearAttachment);
        assertTrue("Rollback must reattach the previous depth texture to the previous attachment point",
            reattach > clearTexture && previousAttachmentArg > reattach && previousTextureArg > previousAttachmentArg);
        assertTrue("Rollback must restore internal depth state only after reattachment succeeds",
            restoreHasDepth > previousTextureArg && restoreAttachment > restoreHasDepth
                && restoreTexture > restoreAttachment);
        assertTrue("Rollback failures must be returned for suppression onto the primary setup failure",
            cleanupCatch > restoreTexture && recordCleanupFailure > cleanupCatch);
    }

    @Test
    public void colorAttachmentSwitchingRollsBackPreviousAttachmentOnSetupFailure() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);
        String addColorAttachment = methodBody(source, "public void addColorAttachment");
        String rollback = methodBody(source, "private Throwable rollbackColorAttachment");

        int previousHasColor = addColorAttachment.indexOf("boolean previousHasColorAttachment = attachments.containsKey(index);");
        int previousTexture = addColorAttachment.indexOf(
            "int previousColorTexture = attachments.getOrDefault(index, 0);", previousHasColor);
        int framebufferBoundLocal = addColorAttachment.indexOf("boolean framebufferBound = false;", previousTexture);
        int bindFramebuffer = addColorAttachment.indexOf("bind();", framebufferBoundLocal);
        int markFramebufferBound = addColorAttachment.indexOf("framebufferBound = true;", bindFramebuffer);
        int attachNew = addColorAttachment.indexOf("OpenGlHelper.glFramebufferTexture2D(", markFramebufferBound);
        int recordAttachment = addColorAttachment.indexOf("attachments.put(index, texture);", attachNew);
        int catchBlock = addColorAttachment.indexOf("catch (RuntimeException | Error exception)", recordAttachment);
        int recordFailure = addColorAttachment.indexOf("failure = exception;", catchBlock);
        int rollbackGuard = addColorAttachment.indexOf("if (framebufferBound)", recordFailure);
        int suppressRollback = addColorAttachment.indexOf(
            "addSuppressedCleanupFailure(exception,\n"
                + "                    rollbackColorAttachment(null, index, previousHasColorAttachment, previousColorTexture));",
            rollbackGuard);
        int rethrow = addColorAttachment.indexOf("throw exception;", suppressRollback);

        assertTrue("Color attachment setup must snapshot previous attachment state before mutating the FBO",
            previousHasColor >= 0 && previousTexture > previousHasColor);
        assertTrue("Color attachment setup must only enable rollback after binding this framebuffer",
            framebufferBoundLocal > previousTexture && bindFramebuffer > framebufferBoundLocal
                && markFramebufferBound > bindFramebuffer);
        assertTrue("Color attachment setup must only record the replacement after GL attachment succeeds",
            attachNew > markFramebufferBound && recordAttachment > attachNew);
        assertTrue("Color attachment setup failures must rollback the previous attachment only if this FBO was bound",
            catchBlock > recordAttachment && recordFailure > catchBlock
                && rollbackGuard > recordFailure && suppressRollback > rollbackGuard && rethrow > suppressRollback);

        int attachment = rollback.indexOf("int attachment = OpenGlHelper.GL_COLOR_ATTACHMENT0 + index;");
        int texture = rollback.indexOf("int texture = previousHasColorAttachment ? previousColorTexture : 0;",
            attachment);
        int reattach = rollback.indexOf("OpenGlHelper.glFramebufferTexture2D(", texture);
        int attachmentArg = rollback.indexOf("attachment,", reattach);
        int textureArg = rollback.indexOf("texture,", attachmentArg);
        int restoreGuard = rollback.indexOf("if (previousHasColorAttachment)", textureArg);
        int restoreMap = rollback.indexOf("attachments.put(index, previousColorTexture);", restoreGuard);
        int removeMap = rollback.indexOf("attachments.remove(index);", restoreMap);
        int cleanupCatch = rollback.indexOf("catch (RuntimeException | Error exception)", removeMap);
        int recordCleanupFailure = rollback.indexOf("failure = addCleanupFailure(failure, exception);", cleanupCatch);

        assertTrue("Color rollback must target the same logical color attachment index",
            attachment >= 0 && texture > attachment);
        assertTrue("Color rollback must reattach the previous texture or detach texture zero",
            reattach > texture && attachmentArg > reattach && textureArg > attachmentArg);
        assertTrue("Color rollback must restore or clear the attachment map after GL rollback succeeds",
            restoreGuard > textureArg && restoreMap > restoreGuard && removeMap > restoreMap);
        assertTrue("Color rollback failures must be returned for suppression onto the primary setup failure",
            cleanupCatch > removeMap && recordCleanupFailure > cleanupCatch);
    }

    @Test
    public void splitFramebufferBindsUseBackendAwareRenderSystemDispatch() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);
        String bindAsRead = source.substring(
            source.indexOf("public void bindAsReadBuffer()"),
            source.indexOf("public void bindAsDrawBuffer()"));
        String bindAsDraw = source.substring(
            source.indexOf("public void bindAsDrawBuffer()"),
            source.indexOf("@Override", source.indexOf("public void bindAsDrawBuffer()")));

        assertTrue(bindAsRead.contains("OculusRenderSystem.bindReadFramebuffer(getGlId());"));
        assertTrue(bindAsDraw.contains("OculusRenderSystem.bindDrawFramebuffer(getGlId());"));
        assertFalse(bindAsRead.contains("bind();"));
        assertFalse(bindAsDraw.contains("bind();"));
    }

    @Test
    public void drawBufferSelectionUsesBackendAwareRenderSystemDispatch() throws IOException {
        String framebuffer = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);
        String renderSystem = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/OculusRenderSystem.java")),
            StandardCharsets.UTF_8);

        assertTrue(framebuffer.contains("this.maxDrawBuffers = OculusRenderSystem.getMaxDrawBuffers();"));
        assertTrue(framebuffer.contains("this.maxColorAttachments = OculusRenderSystem.getMaxColorAttachments();"));
        assertTrue(framebuffer.contains("OculusRenderSystem.drawBuffers(buffer);"));
        assertFalse(framebuffer.contains("GL20.glDrawBuffers("));
        assertFalse(framebuffer.contains("GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)"));
        assertFalse(framebuffer.contains("GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS)"));

        assertTrue(renderSystem.contains("public static int getMaxDrawBuffers()"));
        assertTrue(renderSystem.contains("capabilities.OpenGL20 || hasOpenGlFunction(capabilities, \"glDrawBuffers\")"));
        assertTrue(renderSystem.contains("capabilities.GL_ARB_draw_buffers"));
        assertTrue(renderSystem.contains("ARBDrawBuffers.GL_MAX_DRAW_BUFFERS_ARB"));
        assertTrue(renderSystem.contains("public static int getMaxColorAttachments()"));
        assertTrue(renderSystem.contains("ARBFramebufferObject.GL_MAX_COLOR_ATTACHMENTS"));
        assertTrue(renderSystem.contains("EXTFramebufferObject.GL_MAX_COLOR_ATTACHMENTS_EXT"));
        assertTrue(renderSystem.contains("public static void drawBuffers(IntBuffer buffers)"));
        assertTrue(renderSystem.contains("GL11.glDrawBuffer(drawBuffers.get(drawBuffers.position()));"));
        assertTrue(renderSystem.contains("GL20.glDrawBuffers(drawBuffers);"));
        assertTrue(renderSystem.contains("ARBDrawBuffers.glDrawBuffersARB(drawBuffers);"));
        assertFalse(renderSystem.contains("GL20.glDrawBuffers(buffers);"));
        assertFalse(renderSystem.contains("ARBDrawBuffers.glDrawBuffersARB(buffers);"));
        assertTrue(renderSystem.contains(
            "Multiple draw buffers require OpenGL 2.0 or ARB_draw_buffers on the 1.12.2 framebuffer backend."));
    }

    @Test
    public void framebufferObjectSetupRestoresCallerBindingsLikeReferenceDsaHelpers() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);

        assertFramebufferSetupRestoresCallerBindings(methodBody(source, "public void addDepthAttachment"),
            "addDepthAttachment", "OpenGlHelper.glFramebufferTexture2D(");
        assertFramebufferSetupRestoresCallerBindings(methodBody(source, "public void addColorAttachment"),
            "addColorAttachment", "OpenGlHelper.glFramebufferTexture2D(");
        assertFramebufferSetupRestoresCallerBindings(methodBody(source, "public void noDrawBuffers"),
            "noDrawBuffers", "OculusRenderSystem.drawBuffers(buffer);");
        assertFramebufferSetupRestoresCallerBindings(methodBody(source, "public void drawBuffers"),
            "drawBuffers", "OculusRenderSystem.drawBuffers(buffer);");
        assertFramebufferSetupRestoresCallerBindings(methodBody(source, "public void readBuffer"),
            "readBuffer", "GL11.glReadBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0 + buffer);");
        assertFramebufferSetupRestoresCallerBindings(methodBody(source, "public boolean isComplete"),
            "isComplete", "OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);");
        assertTrue(source.contains("private static Throwable restoreFramebufferBindings("));
        assertTrue(source.contains("addSuppressedCleanupFailure(failure, cleanupFailure);"));
        assertTrue(source.contains("rethrowCleanupFailure(cleanupFailure);"));
    }

    @Test
    public void legacyFramebufferManagerEmptyFramebufferMatchesReferenceAttachmentRules() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String createFramebuffer = source.substring(
            source.indexOf("public Framebuffer createFramebuffer"),
            source.indexOf("public RenderTarget getRenderTarget"));

        int requireDepth = createFramebuffer.indexOf("requireValidDepthTexture(\"Framebuffer creation\");");
        int emptyBranch = createFramebuffer.indexOf("if (drawBuffers.length == 0)");
        int target = createFramebuffer.indexOf("RenderTarget target = getRenderTarget(0);", emptyBranch);
        int depthAttachment = createFramebuffer.indexOf("glFramebuffer.addDepthAttachment(depthTexture);",
            target);
        int colorAttachment = createFramebuffer.indexOf(
            "glFramebuffer.addColorAttachment(0, target.getMainTexture());", depthAttachment);
        int noDrawBuffers = createFramebuffer.indexOf("glFramebuffer.noDrawBuffers();", colorAttachment);
        int completeness = createFramebuffer.indexOf("if (!glFramebuffer.isComplete())", noDrawBuffers);
        int fail = createFramebuffer.indexOf(
            "throw new IllegalStateException(\"Framebuffer creation produced incomplete framebuffer for draw buffers \"",
            completeness);
        int drawBuffersContext = createFramebuffer.indexOf("+ Arrays.toString(drawBuffers)", fail);

        assertTrue("Shader framebuffer construction must require initialized depth before allocating an FBO",
            requireDepth >= 0 && requireDepth < emptyBranch);
        assertTrue(emptyBranch >= 0);
        assertTrue(target > emptyBranch);
        assertTrue("Empty framebuffers must attach the shared depth texture before the dummy color target",
            depthAttachment > target);
        assertTrue(colorAttachment > target);
        assertTrue(noDrawBuffers > colorAttachment);
        assertTrue(completeness > noDrawBuffers);
        assertTrue(fail > completeness);
        assertTrue("Incomplete framebuffer failures must include the shader draw-buffer set",
            drawBuffersContext > fail);
        assertFalse("Shader framebuffers must fail clearly instead of silently skipping depth attachment",
            createFramebuffer.contains("if (depthTexture != 0)"));
    }

    @Test
    public void activeFramebufferManagerRequiresDepthTextureForFramebufferCreation() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String createFramebuffer = methodBody(source, "public Framebuffer createFramebuffer");
        String requireDepth = methodBody(source, "private void requireValidDepthTexture");

        int nullGuard = createFramebuffer.indexOf("Objects.requireNonNull(drawBuffers, \"drawBuffers\");");
        int require = createFramebuffer.indexOf("requireValidDepthTexture(\"Framebuffer creation\");", nullGuard);
        int allocate = createFramebuffer.indexOf("GlFramebuffer glFramebuffer = new GlFramebuffer();", require);
        int nonEmptyReadBuffer = createFramebuffer.indexOf("glFramebuffer.readBuffer(0);", allocate);
        int nonEmptyDepthAttachment = createFramebuffer.indexOf("glFramebuffer.addDepthAttachment(depthTexture);",
            nonEmptyReadBuffer);

        int invalidGuard = requireDepth.indexOf("if (depthTexture <= 0)");
        int fail = requireDepth.indexOf(
            "throw new IllegalStateException(operation + \" requires an initialized framebuffer depth texture\");",
            invalidGuard);

        assertTrue("Depth validation must run after argument validation and before FBO allocation",
            nullGuard >= 0 && require > nullGuard && allocate > require);
        assertTrue("Non-empty shader framebuffers must attach the shared depth texture after draw/read setup",
            nonEmptyReadBuffer > allocate && nonEmptyDepthAttachment > nonEmptyReadBuffer);
        assertTrue("Depth validation must fail clearly when framebuffer setup has not initialized depth",
            invalidGuard >= 0 && fail > invalidGuard);
    }

    @Test
    public void activeFramebufferManagerDestroysPartiallyConstructedFramebufferOnFailure() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String createFramebuffer = methodBody(source, "public Framebuffer createFramebuffer");

        int allocate = createFramebuffer.indexOf("GlFramebuffer glFramebuffer = new GlFramebuffer();");
        int wrapperLocal = createFramebuffer.indexOf("Framebuffer framebuffer = null;", allocate);
        int tryBlock = createFramebuffer.indexOf("try {", wrapperLocal);
        int completeness = createFramebuffer.indexOf("if (!glFramebuffer.isComplete())", tryBlock);
        int successWrap = createFramebuffer.indexOf("framebuffer = new Framebuffer(glFramebuffer, drawBuffers);",
            completeness);
        int addOwned = createFramebuffer.indexOf("ownedFramebuffers.add(framebuffer);", successWrap);
        int returnFramebuffer = createFramebuffer.indexOf("return framebuffer;", addOwned);
        int catchBlock = createFramebuffer.indexOf("catch (RuntimeException | Error exception)", returnFramebuffer);
        int cleanupTry = createFramebuffer.indexOf("try {", catchBlock);
        int wrapperGuard = createFramebuffer.indexOf("if (framebuffer != null)", cleanupTry);
        int destroyWrapper = createFramebuffer.indexOf("framebuffer.destroy();", wrapperGuard);
        int cleanupFinally = createFramebuffer.indexOf("} finally {", destroyWrapper);
        int removeOwned = createFramebuffer.indexOf("ownedFramebuffers.remove(framebuffer);", cleanupFinally);
        int rawElse = createFramebuffer.indexOf("} else {", removeOwned);
        int destroyPartial = createFramebuffer.indexOf("glFramebuffer.destroy();", rawElse);
        int rethrow = createFramebuffer.indexOf("throw exception;", destroyPartial);

        assertTrue("Framebuffer construction must allocate before entering guarded setup", allocate >= 0);
        assertTrue("Framebuffer construction must track whether wrapper ownership has begun",
            wrapperLocal > allocate);
        assertTrue("Framebuffer attachment/completeness setup must be guarded", tryBlock > wrapperLocal);
        assertTrue("Completeness failure must occur inside the guarded setup", completeness > tryBlock);
        assertTrue("The framebuffer should only be recorded as owned after completeness succeeds",
            addOwned > successWrap && returnFramebuffer > addOwned);
        assertTrue("Failed framebuffer construction must catch RuntimeException and Error setup failures",
            catchBlock > returnFramebuffer);
        assertTrue("Failed framebuffer publication must destroy a created wrapper and remove stale ownership",
            cleanupTry > catchBlock && wrapperGuard > cleanupTry && destroyWrapper > wrapperGuard
                && cleanupFinally > destroyWrapper && removeOwned > cleanupFinally);
        assertTrue("Failed raw framebuffer setup must still destroy the partially constructed FBO",
            rawElse > removeOwned && destroyPartial > rawElse);
        assertTrue("Failed framebuffer construction must rethrow the original setup failure",
            rethrow > destroyPartial);
    }

    @Test
    public void activeFramebufferWrapperMarksDestroyedEvenWhenHandleDestroyFails() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/Framebuffer.java")),
            StandardCharsets.UTF_8);
        String destroy = methodBody(source, "public void destroy()");

        int guard = destroy.indexOf("if (destroyed)");
        int returnStatement = destroy.indexOf("return;", guard);
        int destroyHandle = destroy.indexOf("handle.destroy();", returnStatement);
        int finallyBlock = destroy.indexOf("} finally {", destroyHandle);
        int markDestroyed = destroy.indexOf("destroyed = true;", finallyBlock);

        assertTrue("Framebuffer wrapper destroy must keep the existing idempotence guard", guard >= 0);
        assertTrue("Framebuffer wrapper destroy must drop stale wrapper state even when handle cleanup fails",
            destroyHandle > returnStatement && finallyBlock > destroyHandle && markDestroyed > finallyBlock);
    }

    @Test
    public void activeFramebufferWrapperFailsClearlyAfterDestroy() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/Framebuffer.java")),
            StandardCharsets.UTF_8);
        String getHandle = methodBody(source, "public GlFramebuffer getHandle()");
        String getDrawBuffers = methodBody(source, "public int[] getDrawBuffers()");
        String bind = methodBody(source, "public void bind()");
        String bindForRead = methodBody(source, "public void bindForRead()");
        String bindForWrite = methodBody(source, "public void bindForWrite()");
        String requireLive = methodBody(source, "private void requireLive");

        assertTrue(getHandle.contains("requireLive(\"read framebuffer handle\");"));
        assertTrue(getDrawBuffers.contains("requireLive(\"read framebuffer draw buffers\");"));
        assertTrue(bind.contains("requireLive(\"bind framebuffer\");"));
        assertTrue(bindForRead.contains("requireLive(\"bind framebuffer for read\");"));
        assertTrue(bindForWrite.contains("requireLive(\"bind framebuffer for write\");"));
        assertTrue(requireLive.contains("if (destroyed)"));
        assertTrue(requireLive.contains(
            "throw new IllegalStateException(\"Cannot \" + operation + \" after framebuffer was destroyed\");"));
    }

    @Test
    public void activeGlFramebufferMetadataAccessorsRespectGlResourceValidity() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java")),
            StandardCharsets.UTF_8);
        String getColorAttachment = methodBody(source, "public int getColorAttachment");
        String hasDepthAttachment = methodBody(source, "public boolean hasDepthAttachment()");

        assertTrue("Color attachment metadata must not be readable from a destroyed GL framebuffer",
            getColorAttachment.contains("assertValid();"));
        assertTrue("Depth attachment metadata must not be readable from a destroyed GL framebuffer",
            hasDepthAttachment.contains("assertValid();"));
    }

    @Test
    public void activeRenderTargetMetadataAccessorsFailAfterDestroy() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/RenderTarget.java")),
            StandardCharsets.UTF_8);
        String getWidth = methodBody(source, "public int getWidth()");
        String getHeight = methodBody(source, "public int getHeight()");
        String getInternalFormat = methodBody(source, "public InternalTextureFormat getInternalFormat()");
        String requireValid = methodBody(source, "private void requireValid()");

        assertTrue("Render target width must not be readable from destroyed storage",
            getWidth.contains("requireValid();"));
        assertTrue("Render target height must not be readable from destroyed storage",
            getHeight.contains("requireValid();"));
        assertTrue("Render target format metadata must not be readable from destroyed storage",
            getInternalFormat.contains("requireValid();"));
        assertTrue(requireValid.contains("throw new IllegalStateException(\"Attempted to use a destroyed render target\");"));
    }

    @Test
    public void activeFramebufferManagerDestroysPartiallyInitializedTargetsOnInitializeFailure() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String initialize = methodBody(source, "public void initialize()");

        int tryBlock = initialize.indexOf("try {");
        int rebuildTargets = initialize.indexOf("rebuildRenderTargets(displayWidth, displayHeight);", tryBlock);
        int rebuildDepth = initialize.indexOf("rebuildDepthTexture(displayWidth, displayHeight);", rebuildTargets);
        int postInstallCleanupCatch = initialize.indexOf("catch (PostInstallCleanupException exception)", rebuildDepth);
        int rethrowPostInstallCleanup = initialize.indexOf("throw exception;", postInstallCleanupCatch);
        int catchBlock = initialize.indexOf("catch (RuntimeException | Error exception)", rethrowPostInstallCleanup);
        int destroyPartial = initialize.indexOf("destroy();", catchBlock);
        int rethrow = initialize.indexOf("throw exception;", destroyPartial);

        assertTrue("Framebuffer manager initialization must guard render-target and depth allocation",
            tryBlock >= 0);
        assertTrue("Render targets should be allocated before depth to match the active setup order",
            rebuildTargets > tryBlock);
        assertTrue("Depth allocation must happen inside the guarded initialization block",
            rebuildDepth > rebuildTargets);
        assertTrue("Initialization must not destroy coherent state for post-install cleanup failures",
            postInstallCleanupCatch > rebuildDepth && rethrowPostInstallCleanup > postInstallCleanupCatch);
        assertTrue("Initialization must catch allocation failures after partial render-target setup",
            catchBlock > rebuildDepth);
        assertTrue("Initialization failure must destroy any partially allocated render targets or depth texture",
            destroyPartial > catchBlock);
        assertTrue("Initialization failure must rethrow the original allocation failure",
            rethrow > destroyPartial);
    }

    @Test
    public void activeFramebufferManagerCommitsDisplaySizeOnlyAfterRenderTargetRebuildSucceeds() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String rebuildRenderTargets = methodBody(source, "private void rebuildRenderTargets");

        int settings = rebuildRenderTargets.indexOf(
            "Map<Integer, RenderTargetSettings> settings = renderTargetDirectives.getRenderTargetSettings();");
        int loop = rebuildRenderTargets.indexOf("settings.forEach((index, renderTargetSettings) ->", settings);
        int resize = rebuildRenderTargets.indexOf(
            "target.resize(Math.max(1, dimensions.x()), Math.max(1, dimensions.y()));", loop);
        int commitWidth = rebuildRenderTargets.indexOf("this.width = targetWidth;", resize);
        int commitHeight = rebuildRenderTargets.indexOf("this.height = targetHeight;", commitWidth);

        assertTrue("Render target rebuild must load the pack target settings before resizing",
            settings >= 0);
        assertTrue("Render target rebuild must visit every configured target before committing dimensions",
            loop > settings);
        assertTrue("Render target storage must be resized before the manager records the new display width",
            resize > loop && commitWidth > resize);
        assertTrue("The manager must only record the new display height after target rebuild succeeds",
            commitHeight > commitWidth);
    }

    @Test
    public void activeFramebufferManagerRestoresPreviousSizeWhenResizeDepthRebuildFails() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String resizeIfNeeded = methodBody(source, "public void resizeIfNeeded");

        int positiveGuard = resizeIfNeeded.indexOf("if (newWidth <= 0 || newHeight <= 0)");
        int sameSizeGuard = resizeIfNeeded.indexOf("if (newWidth == width && newHeight == height)",
            positiveGuard);
        int previousWidth = resizeIfNeeded.indexOf("int previousWidth = width;", sameSizeGuard);
        int previousHeight = resizeIfNeeded.indexOf("int previousHeight = height;", previousWidth);
        int tryBlock = resizeIfNeeded.indexOf("try {", previousHeight);
        int rebuildTargets = resizeIfNeeded.indexOf("rebuildRenderTargets(newWidth, newHeight);", tryBlock);
        int rebuildDepth = resizeIfNeeded.indexOf("rebuildDepthTexture(newWidth, newHeight);", rebuildTargets);
        int postInstallCleanupCatch = resizeIfNeeded.indexOf("catch (PostInstallCleanupException exception)", rebuildDepth);
        int rethrowPostInstallCleanup = resizeIfNeeded.indexOf("throw exception;", postInstallCleanupCatch);
        int catchBlock = resizeIfNeeded.indexOf("catch (RuntimeException | Error exception)",
            rethrowPostInstallCleanup);
        int rollbackGuard = resizeIfNeeded.indexOf("if (previousWidth > 0 && previousHeight > 0)", catchBlock);
        int rollbackTry = resizeIfNeeded.indexOf("try {", rollbackGuard);
        int rollbackTargets = resizeIfNeeded.indexOf("rebuildRenderTargets(previousWidth, previousHeight);",
            rollbackTry);
        int rollbackCatch = resizeIfNeeded.indexOf("catch (RuntimeException | Error rollbackException)",
            rollbackTargets);
        int suppressed = resizeIfNeeded.indexOf("exception.addSuppressed(rollbackException);", rollbackCatch);
        int restoreWidth = resizeIfNeeded.indexOf("this.width = previousWidth;", suppressed);
        int restoreHeight = resizeIfNeeded.indexOf("this.height = previousHeight;", restoreWidth);
        int rethrow = resizeIfNeeded.indexOf("throw exception;", restoreHeight);

        assertTrue("Resize must still ignore invalid dimensions before touching allocation state",
            positiveGuard >= 0);
        assertTrue("Resize must keep the existing no-op guard for unchanged dimensions",
            sameSizeGuard > positiveGuard);
        assertTrue("Resize must remember the previous display width before any partial rebuild",
            previousWidth > sameSizeGuard);
        assertTrue("Resize must remember the previous display height before any partial rebuild",
            previousHeight > previousWidth);
        assertTrue("Render-target and depth rebuilds must be guarded as one resize transaction",
            tryBlock > previousHeight);
        assertTrue("Color targets may resize before the depth texture is recreated",
            rebuildTargets > tryBlock);
        assertTrue("Depth texture recreation must happen inside the same guarded resize transaction",
            rebuildDepth > rebuildTargets);
        assertTrue("Resize must not roll back coherent state when only old depth cleanup fails",
            postInstallCleanupCatch > rebuildDepth && rethrowPostInstallCleanup > postInstallCleanupCatch);
        assertTrue("Failed resize must catch allocation failures after partial color target resize",
            catchBlock > rebuildDepth);
        assertTrue("Failed resize must try to restore render-target texture dimensions before restoring manager state",
            rollbackGuard > catchBlock && rollbackTry > rollbackGuard && rollbackTargets > rollbackTry);
        assertTrue("Failed resize must preserve rollback failures as suppressed context on the original resize failure",
            rollbackCatch > rollbackTargets && suppressed > rollbackCatch);
        assertTrue("Failed resize must restore the previous width so the next call can retry",
            restoreWidth > suppressed);
        assertTrue("Failed resize must restore the previous height so the next call can retry",
            restoreHeight > restoreWidth);
        assertTrue("Failed resize must rethrow the original allocation failure",
            rethrow > restoreHeight);
    }

    @Test
    public void activeFramebufferManagerDestroyClearsCachedLifecycleStateForReloads() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String destroy = methodBody(source, "public void destroy()");

        int guard = destroy.indexOf("if (destroyed)");
        int idempotentReturn = destroy.indexOf("return;", guard);
        int failureLocal = destroy.indexOf("Throwable failure = null;", idempotentReturn);
        int destroyFramebuffers = destroy.indexOf("for (Framebuffer framebuffer : ownedFramebuffers)", failureLocal);
        int destroyFramebufferCall = destroy.indexOf("failure = runCleanup(failure, framebuffer::destroy);",
            destroyFramebuffers);
        int destroyTargets = destroy.indexOf("for (RenderTarget target : renderTargets.values())",
            destroyFramebufferCall);
        int destroyTargetCall = destroy.indexOf("failure = runCleanup(failure, target::destroy);", destroyTargets);
        int deleteDepth = destroy.indexOf("failure = runCleanup(failure, () -> deleteTexture(depthTexture));",
            destroyTargetCall);
        int rethrowCleanup = destroy.indexOf("rethrowCleanupFailure(failure);", deleteDepth);
        int finallyCleanup = destroy.indexOf("} finally {", rethrowCleanup);
        int clearFramebuffers = destroy.indexOf("ownedFramebuffers.clear();", finallyCleanup);
        int clearTargets = destroy.indexOf("renderTargets.clear();", clearFramebuffers);
        int zeroDepth = destroy.indexOf("depthTexture = 0;", clearTargets);
        int zeroVersion = destroy.indexOf("depthTextureVersion = 0;", zeroDepth);
        int zeroWidth = destroy.indexOf("width = 0;", zeroVersion);
        int zeroHeight = destroy.indexOf("height = 0;", zeroWidth);
        int markDestroyed = destroy.indexOf("destroyed = true;", zeroHeight);

        assertTrue("Framebuffer manager destroy must stay idempotent after teardown", guard >= 0
            && idempotentReturn > guard && failureLocal > idempotentReturn);
        assertTrue("Framebuffer manager destroy must release owned framebuffer wrappers before render targets",
            destroyFramebuffers > failureLocal && destroyFramebufferCall > destroyFramebuffers);
        assertTrue("Framebuffer manager destroy must release render targets before deleting the shared depth texture",
            destroyTargets > destroyFramebufferCall && destroyTargetCall > destroyTargets && deleteDepth > destroyTargetCall);
        assertTrue("Framebuffer manager destroy must clear cached lifecycle state even when cleanup fails",
            rethrowCleanup > deleteDepth && finallyCleanup > rethrowCleanup
                && clearFramebuffers > finallyCleanup && clearTargets > clearFramebuffers
                && zeroDepth > clearTargets && zeroVersion > zeroDepth);
        assertTrue("Framebuffer manager destroy must clear cached dimensions so later resize checks cannot no-op stale state",
            zeroWidth > zeroVersion && zeroHeight > zeroWidth);
        assertTrue("Framebuffer manager destroy must make teardown terminal even when cleanup reports a failure",
            markDestroyed > zeroHeight);
        assertTrue(source.contains("private static Throwable runCleanup(Throwable failure, Runnable cleanup)"));
        assertTrue(source.contains("catch (RuntimeException | Error exception)"));
        assertTrue(source.contains("failure.addSuppressed(exception);"));
    }

    @Test
    public void activeFramebufferManagerFailsClearlyAfterDestroy() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String requireLive = methodBody(source, "private void requireLive");

        assertTrue(source.contains("private boolean destroyed;"));
        assertTrue(requireLive.contains("if (destroyed)"));
        assertTrue(requireLive.contains(
            "throw new IllegalStateException(\"Cannot \" + operation + \" after framebuffer manager was destroyed\");"));
        assertTrue(methodBody(source, "public void initialize()")
            .contains("requireLive(\"initialize framebuffer manager\");"));
        assertTrue(methodBody(source, "public void resizeIfNeeded")
            .contains("requireLive(\"resize framebuffer manager\");"));
        assertTrue(methodBody(source, "public Framebuffer createFramebuffer")
            .contains("requireLive(\"create framebuffer\");"));
        assertTrue(methodBody(source, "public RenderTarget getRenderTarget")
            .contains("requireLive(\"read render target\");"));
        assertTrue(methodBody(source, "public void destroyFramebuffer")
            .contains("requireLive(\"destroy owned framebuffer\");"));
        assertTrue(methodBody(source, "public int getWidth()")
            .contains("requireLive(\"read framebuffer manager width\");"));
        assertTrue(methodBody(source, "public int getHeight()")
            .contains("requireLive(\"read framebuffer manager height\");"));
        assertTrue(methodBody(source, "public int getDepthTexture()")
            .contains("requireLive(\"read framebuffer manager depth texture\");"));
        assertTrue(methodBody(source, "public int getDepthTextureVersion()")
            .contains("requireLive(\"read framebuffer manager depth texture version\");"));
    }

    @Test
    public void activeFramebufferManagerTracksDepthTextureRecreationVersion() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String renderTargets = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/rendertarget/RenderTargets.java")), StandardCharsets.UTF_8);
        String worldPipeline = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java")), StandardCharsets.UTF_8);

        int field = source.indexOf("private int depthTextureVersion;");
        int getter = source.indexOf("public int getDepthTextureVersion()", field);
        int rebuild = source.indexOf("private void rebuildDepthTexture", getter);
        int create = source.indexOf("int newDepthTexture = createDepthTexture(targetWidth, targetHeight);", rebuild);
        int assign = source.indexOf("depthTexture = newDepthTexture;", create);
        int increment = source.indexOf("depthTextureVersion++;", assign);

        assertTrue(field >= 0);
        assertTrue(getter > field);
        assertTrue(rebuild > getter);
        assertTrue(create > rebuild);
        assertTrue(assign > create);
        assertTrue(increment > assign);

        assertTrue(renderTargets.contains("private int cachedDepthBufferVersion;"));
        assertTrue(renderTargets.contains("|| cachedDepthBufferVersion != newDepthBufferVersion;"));
        assertTrue(worldPipeline.contains("framebufferManager.getDepthTextureVersion(),"));
    }

    @Test
    public void activeFramebufferManagerReplacesDepthTextureOnlyAfterSuccessfulReattachment() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java")),
            StandardCharsets.UTF_8);
        String rebuildDepth = methodBody(source, "private void rebuildDepthTexture");
        String reattachDepth = methodBody(source, "private void reattachOwnedDepthTexture");

        int savePrevious = rebuildDepth.indexOf("int previousDepthTexture = depthTexture;");
        int createNew = rebuildDepth.indexOf("int newDepthTexture = createDepthTexture(targetWidth, targetHeight);",
            savePrevious);
        int tryBlock = rebuildDepth.indexOf("try {", createNew);
        int reattachOwned = rebuildDepth.indexOf("reattachOwnedDepthTexture(newDepthTexture);", tryBlock);
        int assignNew = rebuildDepth.indexOf("depthTexture = newDepthTexture;", reattachOwned);
        int incrementVersion = rebuildDepth.indexOf("depthTextureVersion++;", assignNew);
        int catchBlock = rebuildDepth.indexOf("catch (RuntimeException | Error exception)", incrementVersion);
        int failureLocal = rebuildDepth.indexOf("Throwable failure = null;", catchBlock);
        int rollbackReattach = rebuildDepth.indexOf(
            "failure = runCleanup(failure, () -> reattachOwnedDepthTexture(previousDepthTexture));",
            failureLocal);
        int deleteFailedReplacement = rebuildDepth.indexOf(
            "failure = runCleanup(failure, () -> deleteTexture(newDepthTexture));",
            rollbackReattach);
        int suppressFailure = rebuildDepth.indexOf("addSuppressedCleanupFailure(exception, failure);",
            deleteFailedReplacement);
        int rethrow = rebuildDepth.indexOf("throw exception;", suppressFailure);
        int throwPostInstallCleanup = rebuildDepth.indexOf("throwPostInstallCleanupFailure(", rethrow);
        int cleanupPrevious = rebuildDepth.indexOf(
            "runCleanup(null, () -> deleteTexture(previousDepthTexture))", throwPostInstallCleanup);

        int zeroGuard = reattachDepth.indexOf("if (replacementDepthTexture == 0)");
        int loop = reattachDepth.indexOf("for (Framebuffer framebuffer : ownedFramebuffers)", zeroGuard);
        int handle = reattachDepth.indexOf("GlFramebuffer handle = framebuffer.getHandle();", loop);
        int hasDepth = reattachDepth.indexOf("if (handle.hasDepthAttachment())", handle);
        int addDepth = reattachDepth.indexOf("handle.addDepthAttachment(replacementDepthTexture);", hasDepth);

        assertTrue("Depth rebuild must remember the old depth texture before allocating a replacement",
            savePrevious >= 0);
        assertTrue("Depth rebuild must prove the replacement allocation before entering reattachment",
            createNew > savePrevious && tryBlock > createNew);
        assertTrue("Owned framebuffers must reattach the recreated depth texture before the manager installs it",
            reattachOwned > tryBlock && assignNew > reattachOwned);
        assertTrue("Depth texture version must advance only after the replacement is installed",
            incrementVersion > assignNew);
        assertTrue("Failed reattachment must restore the old depth texture before deleting the replacement",
            catchBlock > incrementVersion && failureLocal > catchBlock
                && rollbackReattach > failureLocal && deleteFailedReplacement > rollbackReattach);
        assertTrue("Failed reattachment cleanup must be suppressed onto the original setup failure",
            suppressFailure > deleteFailedReplacement && rethrow > suppressFailure);
        assertTrue("The old depth texture must be deleted only after replacement reattachment succeeds",
            throwPostInstallCleanup > rethrow && cleanupPrevious > throwPostInstallCleanup);
        assertFalse("Depth reattachment cleanup must not use a finally path that can mask setup failures",
            rebuildDepth.contains("boolean installed = false;"));
        assertTrue("Depth reattachment must skip invalid replacement texture IDs", zeroGuard >= 0);
        assertTrue("Depth reattachment must inspect every owned framebuffer", loop > zeroGuard);
        assertTrue("Depth reattachment must use the wrapped GlFramebuffer handle", handle > loop);
        assertTrue("Only framebuffers with depth attachments should be reattached", hasDepth > handle);
        assertTrue("Owned depth attachments must point at the replacement texture", addDepth > hasDepth);
        assertTrue("Post-install cleanup failures must have a dedicated exception type",
            source.contains("private static final class PostInstallCleanupException extends RuntimeException"));
        assertTrue("Post-install cleanup failures must preserve the cleanup cause",
            source.contains("Failed to delete replaced framebuffer depth texture"));
    }

    private static void scanRoot(Path root, StringBuilder violations) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> files = Files.walk(root)) {
            Iterator<Path> iterator = files
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .iterator();

            while (iterator.hasNext()) {
                scanFile(iterator.next(), violations);
            }
        }
    }

    private static void scanFile(Path path, StringBuilder violations) throws IOException {
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        for (String forbiddenCall : FORBIDDEN_DIRECT_GL30_FBO_CALLS) {
            if (path.endsWith(Paths.get("net/oculus/gl/OculusRenderSystem.java"))
                    && "GL30.glBindFramebuffer(".equals(forbiddenCall)) {
                continue;
            }
            if (source.contains(forbiddenCall)) {
                violations.append(path)
                    .append(" contains ")
                    .append(forbiddenCall)
                    .append('\n');
            }
        }

        for (String forbiddenTarget : FORBIDDEN_SPLIT_FRAMEBUFFER_TARGETS) {
            if (source.contains(forbiddenTarget)) {
                violations.append(path)
                    .append(" contains ")
                    .append(forbiddenTarget)
                    .append('\n');
            }
        }

        for (String forbiddenCall : FORBIDDEN_DIRECT_DRAW_BUFFER_CALLS) {
            if (path.endsWith(Paths.get("net/oculus/gl/OculusRenderSystem.java"))) {
                continue;
            }
            if (source.contains(forbiddenCall)) {
                violations.append(path)
                    .append(" contains ")
                    .append(forbiddenCall)
                    .append('\n');
            }
        }
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

    private static void assertFramebufferSetupRestoresCallerBindings(
            String body,
            String context,
            String operation) {
        int saveCombined = body.indexOf("int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();");
        int saveRead = body.indexOf("int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();",
            saveCombined);
        int saveDraw = body.indexOf("int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();",
            saveRead);
        int failureLocal = body.indexOf("Throwable failure = null;", saveDraw);
        int tryBlock = body.indexOf("try {", failureLocal);
        int bind = body.indexOf("bind();", tryBlock);
        int operationCall = body.indexOf(operation, bind);
        int catchBlock = body.indexOf("catch (RuntimeException | Error exception)", operationCall);
        int recordFailure = body.indexOf("failure = exception;", catchBlock);
        int rethrowPrimary = body.indexOf("throw exception;", recordFailure);
        int finallyBlock = body.indexOf("finally {", rethrowPrimary);
        int cleanupFailure = body.indexOf(
            "Throwable cleanupFailure = restoreFramebufferBindings(null, previousFramebuffer, previousReadFramebuffer,\n"
                + "                previousDrawFramebuffer);",
            finallyBlock);
        int suppressFailure = body.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", cleanupFailure);
        int rethrowCleanup = body.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressFailure);

        assertTrue(context + " must save the caller's combined framebuffer binding", saveCombined >= 0);
        assertTrue(context + " must save the caller's read framebuffer binding", saveRead > saveCombined);
        assertTrue(context + " must save the caller's draw framebuffer binding", saveDraw > saveRead);
        assertTrue(context + " must track primary framebuffer operation failures", failureLocal > saveDraw);
        assertTrue(context + " must bind the target framebuffer only inside a guarded setup block",
            tryBlock > failureLocal && bind > tryBlock);
        assertTrue(context + " must perform framebuffer-object setup while its framebuffer is bound",
            operationCall > bind);
        assertTrue(context + " must preserve the primary framebuffer operation failure before cleanup",
            catchBlock > operationCall && recordFailure > catchBlock && rethrowPrimary > recordFailure);
        assertTrue(context + " must restore the caller's framebuffer bindings from a finally block",
            finallyBlock > rethrowPrimary && cleanupFailure > finallyBlock);
        assertTrue(context + " cleanup failures must suppress onto primary failures or rethrow alone",
            suppressFailure > cleanupFailure && rethrowCleanup > suppressFailure);
    }
}
