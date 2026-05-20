package net.oculus.gl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class OculusRenderSystemCapabilityDispatchTest {
    @Test
    public void computeDispatchUsesCoreOrArbComputeShader() throws IOException {
        String source = readRenderSystem();

        assertTrue(source.contains("capabilities.GL_ARB_compute_shader"));
        assertTrue(source.contains("hasOpenGlFunction(capabilities, \"glDispatchCompute\")"));
        assertTrue(source.contains("GL43.glDispatchCompute(workX, workY, workZ);"));
        assertTrue(source.contains("ARBComputeShader.glDispatchCompute(workX, workY, workZ);"));
    }

    @Test
    public void computeDispatchFailsClearlyInsteadOfSkippingUnsupportedComputeRequirements() throws IOException {
        String source = readRenderSystem();
        String computeProgram = readSource("src/main/java/net/oculus/gl/program/ComputeProgram.java");
        String dispatchCompute = source.substring(
            source.indexOf("public static void dispatchCompute(int workX, int workY, int workZ)"),
            source.indexOf("public static void dispatchCompute(Vector3i workGroups)"));
        String computeDispatch = computeProgram.substring(
            computeProgram.indexOf("public void dispatch(float width, float height)"),
            computeProgram.indexOf("public static void unbind()"));

        assertTrue(dispatchCompute.contains("if (!supportsCompute())"));
        assertTrue(dispatchCompute.contains("throw new IllegalStateException(\"Compute shaders are not supported"));
        assertTrue(dispatchCompute.contains("if (capabilities == null)"));
        assertTrue(dispatchCompute.contains("throw new IllegalStateException(\"Compute shader capabilities are unavailable"));
        assertFalse(dispatchCompute.contains("dispatch skipped"));
        assertFalse(dispatchCompute.contains("Oculus.LOGGER.warn"));
        assertFalse(dispatchCompute.contains("return;"));

        assertTrue(computeDispatch.contains("if (!OculusRenderSystem.supportsCompute())"));
        assertTrue(computeDispatch.contains("throw new IllegalStateException(\"Compute shaders are not supported"));
        assertFalse(computeDispatch.contains("Oculus.LOGGER.warn"));
        assertFalse(computeDispatch.contains("return;"));
    }

    @Test
    public void imageLoadStoreDispatchUsesCoreArbOrExtPath() throws IOException {
        String source = readRenderSystem();

        assertTrue(source.contains("capabilities.OpenGL42"));
        assertTrue(source.contains("capabilities.GL_ARB_shader_image_load_store"));
        assertTrue(source.contains("capabilities.GL_EXT_shader_image_load_store"));
        assertTrue(source.contains("hasOpenGlFunction(capabilities, \"glBindImageTexture\")"));
        assertTrue(source.contains("hasOpenGlFunction(capabilities, \"glMemoryBarrier\")"));
        assertTrue(source.contains("ARBShaderImageLoadStore.glBindImageTexture("));
        assertTrue(source.contains("ARBShaderImageLoadStore.glMemoryBarrier("));
        assertTrue(source.contains("ARBShaderImageLoadStore.GL_MAX_IMAGE_UNITS"));
    }

    @Test
    public void imageLoadStoreBindFailsClearlyInsteadOfSkippingUnsupportedImageRequirements() throws IOException {
        String source = readRenderSystem();
        String bindImageTexture = source.substring(
            source.indexOf("public static void bindImageTexture"),
            source.indexOf("public static int getMaxImageUnits()"));

        assertTrue(bindImageTexture.contains("if (!supportsImageLoadStore())"));
        assertTrue(bindImageTexture.contains("throw new IllegalStateException(\"Image load/store is not supported"));
        assertTrue(bindImageTexture.contains("if (capabilities == null)"));
        assertTrue(bindImageTexture.contains("throw new IllegalStateException(\"Image load/store capabilities are unavailable"));
        assertFalse(bindImageTexture.contains("skipping bind"));
        assertFalse(bindImageTexture.contains("Oculus.LOGGER.warn"));
        assertFalse(bindImageTexture.contains("return;"));
    }

    @Test
    public void capabilityProbeCanBeDisabledForHeadlessLoaderTests() throws IOException {
        String source = readRenderSystem();

        assertTrue(source.contains("DISABLE_GL_CAPABILITY_PROBES_PROPERTY"));
        assertTrue(source.contains("Boolean.getBoolean(DISABLE_GL_CAPABILITY_PROBES_PROPERTY)"));
    }

    @Test
    public void perBufferBlendOverridesFailClearlyInsteadOfSkippingUnsupportedState() throws IOException {
        String source = readRenderSystem();
        String bufferBlendHelpers = source.substring(
            source.indexOf("public static void disableBufferBlend"),
            source.indexOf("private static boolean supportsImageLoadStore()"));

        assertTrue(bufferBlendHelpers.contains("private static boolean supportsBufferBlending(ContextCapabilities capabilities)"));
        assertTrue(bufferBlendHelpers.contains("boolean supportsBlendFunction = capabilities.OpenGL40"));
        assertTrue(bufferBlendHelpers.contains("capabilities.GL_ARB_draw_buffers_blend"));
        assertTrue(bufferBlendHelpers.contains("hasOpenGlFunction(capabilities, \"glBlendFuncSeparatei\")"));
        assertTrue(bufferBlendHelpers.contains("hasOpenGlFunction(capabilities, \"glBlendFuncSeparateiARB\")"));
        assertTrue(bufferBlendHelpers.contains("boolean supportsIndexedBlendState = capabilities.OpenGL30"));
        assertTrue(bufferBlendHelpers.contains("capabilities.GL_EXT_draw_buffers2"));
        assertTrue(bufferBlendHelpers.contains("hasOpenGlFunction(capabilities, \"glEnablei\")"));
        assertTrue(bufferBlendHelpers.contains("hasOpenGlFunction(capabilities, \"glDisablei\")"));
        assertTrue(bufferBlendHelpers.contains("GL30.glDisablei(GL11.GL_BLEND, buffer);"));
        assertTrue(bufferBlendHelpers.contains("GL30.glEnablei(GL11.GL_BLEND, buffer);"));
        assertTrue(bufferBlendHelpers.contains("EXTDrawBuffers2.glDisableIndexedEXT(GL11.GL_BLEND, buffer);"));
        assertTrue(bufferBlendHelpers.contains("EXTDrawBuffers2.glEnableIndexedEXT(GL11.GL_BLEND, buffer);"));
        assertTrue(bufferBlendHelpers.contains("throw new IllegalStateException(\"Per-buffer blending is not supported"));
        assertTrue(bufferBlendHelpers.contains("if (capabilities == null)"));
        assertTrue(bufferBlendHelpers.contains("throw new IllegalStateException(\"Per-buffer blending capabilities are unavailable"));
        assertFalse(bufferBlendHelpers.contains("warnBufferBlendUnsupported"));
        assertFalse(bufferBlendHelpers.contains("LOGGER.warn"));
        assertFalse(bufferBlendHelpers.contains("return;"));
    }

    @Test
    public void clearTextureDispatchUsesCoreOrArbPathAndCallersDoNotProbeDirectly() throws IOException {
        String source = readRenderSystem();
        String imageManager = readSource("src/main/java/net/oculus/pipeline/texture/CustomImageManager.java");

        assertTrue(source.contains("capabilities.GL_ARB_clear_texture"));
        assertTrue(source.contains("hasOpenGlFunction(capabilities, \"glClearTexImage\")"));
        assertTrue(source.contains("GL44.glClearTexImage(texture, level, format, type, data);"));
        assertTrue(source.contains("ARBClearTexture.glClearTexImage(texture, level, format, type, data);"));
        assertTrue(imageManager.contains("OculusRenderSystem.clearTexImage("));
        assertFalse(imageManager.contains("GLContext.getCapabilities()"));
    }

    @Test
    public void mipmapGenerationUsesCoreArbOrExtFramebufferPath() throws IOException {
        String source = readRenderSystem();
        String compositeRenderer = readSource("src/main/java/net/oculus/postprocess/CompositeRenderer.java");
        String finalPassRenderer = readSource("src/main/java/net/oculus/postprocess/FinalPassRenderer.java");
        String shadowMap = readSource("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String generateMipmaps = source.substring(
            source.indexOf("public static void generateMipmaps"),
            source.indexOf("public static void texParameteri"));

        assertTrue(generateMipmaps.contains("bindTextureForLegacyOperation(target, texture);"));
        assertTrue(generateMipmaps.contains("if (capabilities.OpenGL30 || hasOpenGlFunction(capabilities, \"glGenerateMipmap\"))"));
        assertTrue(generateMipmaps.contains("GL30.glGenerateMipmap(target);"));
        assertTrue(generateMipmaps.contains("capabilities.GL_ARB_framebuffer_object"));
        assertTrue(generateMipmaps.contains("ARBFramebufferObject.glGenerateMipmap(target);"));
        assertTrue(generateMipmaps.contains("capabilities.GL_EXT_framebuffer_object"));
        assertTrue(generateMipmaps.contains("EXTFramebufferObject.glGenerateMipmapEXT(target);"));
        assertTrue(generateMipmaps.contains("throw new IllegalStateException(\"Mipmap generation is not supported"));
        assertFalse(generateMipmaps.contains("return;"));

        assertTrue(compositeRenderer.contains("OculusRenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);"));
        assertTrue(finalPassRenderer.contains("OculusRenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);"));
        assertTrue(shadowMap.contains("OculusRenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);"));
    }

    @Test
    public void mipmapGenerationRestoresCallerTextureBindingFromFinally() throws IOException {
        String source = readRenderSystem();
        String generateMipmaps = source.substring(
            source.indexOf("public static void generateMipmaps"),
            source.indexOf("public static void withDefaultTextureBindingRestored"));

        int savePrevious = generateMipmaps.indexOf("int previousTexture = GL11.glGetInteger(textureBindingParameter(target));");
        int failureLocal = generateMipmaps.indexOf("Throwable failure = null;", savePrevious);
        int tryBlock = generateMipmaps.indexOf("try {", failureLocal);
        int bindTexture = generateMipmaps.indexOf("bindTextureForLegacyOperation(target, texture);", tryBlock);
        int capabilities = generateMipmaps.indexOf("ContextCapabilities capabilities = getCapabilities();", bindTexture);
        int generate = generateMipmaps.indexOf("GL30.glGenerateMipmap(target);", capabilities);
        int catchBlock = generateMipmaps.indexOf("catch (RuntimeException | Error exception)", generate);
        int recordFailure = generateMipmaps.indexOf("failure = exception;", catchBlock);
        int rethrowPrimary = generateMipmaps.indexOf("throw exception;", recordFailure);
        int finallyBlock = generateMipmaps.indexOf("} finally {", rethrowPrimary);
        int cleanupFailure = generateMipmaps.indexOf("Throwable cleanupFailure = runTextureCleanup(null,",
            finallyBlock);
        int restorePrevious = generateMipmaps.indexOf("bindTextureForLegacyOperation(target, previousTexture)",
            cleanupFailure);
        int suppressCleanup = generateMipmaps.indexOf("addSuppressedTextureCleanupFailure(failure, cleanupFailure);",
            restorePrevious);
        int rethrowCleanupOnly = generateMipmaps.indexOf("rethrowTextureCleanupFailure(cleanupFailure);",
            suppressCleanup);

        assertTrue("Mipmap generation must save the caller's active-unit texture binding", savePrevious >= 0);
        assertTrue("Mipmap generation must track primary failures before mutating state",
            failureLocal > savePrevious);
        assertTrue("Mipmap generation must bind the target texture only inside the guarded block",
            tryBlock > failureLocal && bindTexture > tryBlock);
        assertTrue("Mipmap generation must probe capabilities after binding the target texture",
            capabilities > bindTexture && generate > capabilities);
        assertTrue("Mipmap generation must preserve primary failures before cleanup",
            catchBlock > generate && recordFailure > catchBlock && rethrowPrimary > recordFailure);
        assertTrue("Mipmap generation must restore the previous binding even when generation fails",
            finallyBlock > rethrowPrimary && cleanupFailure > finallyBlock && restorePrevious > cleanupFailure);
        assertTrue("Mipmap cleanup failures must suppress onto primary failures or rethrow alone",
            suppressCleanup > restorePrevious && rethrowCleanupOnly > suppressCleanup);
    }

    @Test
    public void clearBufferDispatchUsesCoreOrArbPathAndCallersDoNotProbeDirectly() throws IOException {
        String source = readRenderSystem();
        String bufferManager = readSource("src/main/java/net/oculus/pipeline/buffer/ShaderStorageBufferManager.java");

        assertTrue(source.contains("capabilities.GL_ARB_clear_buffer_object"));
        assertTrue(source.contains("hasOpenGlFunction(capabilities, \"glClearBufferData\")"));
        assertTrue(source.contains("hasOpenGlFunction(capabilities, \"glShaderStorageBlockBinding\")"));
        assertTrue(source.contains("GL43.glClearBufferData(target, internalFormat, format, type, data);"));
        assertTrue(source.contains("ARBClearBufferObject.glClearBufferData(target, internalFormat, format, type, data);"));
        assertTrue(bufferManager.contains("OculusRenderSystem.clearBufferData("));
        assertFalse(bufferManager.contains("GLContext.getCapabilities()"));
    }

    @Test
    public void copyImageDispatchUsesCoreOrArbPathForDepthCopyStrategy() throws IOException {
        String source = readRenderSystem();
        String depthCopyStrategy = readSource("src/main/java/net/oculus/rendertarget/DepthCopyStrategy.java");

        assertTrue(source.contains("capabilities.GL_ARB_copy_image"));
        assertTrue(source.contains("hasOpenGlFunction(capabilities, \"glCopyImageSubData\")"));
        assertTrue(source.contains("ContextCapabilities.class.getDeclaredField(fieldName);"));
        assertTrue(source.contains("field.getLong(capabilities) != 0L;"));
        assertTrue(source.contains("GL43.glCopyImageSubData("));
        assertTrue(source.contains("ARBCopyImage.glCopyImageSubData("));
        assertTrue(source.contains("Texture-to-texture image copies are not supported"));
        assertTrue(depthCopyStrategy.contains("OculusRenderSystem.supportsCopyImageSubData()"));
        assertTrue(depthCopyStrategy.contains("OculusRenderSystem.copyImageSubData("));
        assertFalse(depthCopyStrategy.contains("GL43.glCopyImageSubData("));
        assertFalse(depthCopyStrategy.contains("ARBCopyImage.glCopyImageSubData("));
    }

    @Test
    public void copyTexSubImageRestoresDestinationBindingFromFinally() throws IOException {
        String source = readRenderSystem();
        String copyTexSubImage = source.substring(
            source.indexOf("public static void copyTexSubImage2D"),
            source.indexOf("public static void copyImageSubData"));

        int savePrevious = copyTexSubImage.indexOf("int previousTexture = GL11.glGetInteger(textureBindingParameter(target));");
        int failureLocal = copyTexSubImage.indexOf("Throwable failure = null;", savePrevious);
        int tryBlock = copyTexSubImage.indexOf("try {", failureLocal);
        int bindDestination = copyTexSubImage.indexOf("bindTextureForLegacyOperation(target, destinationTexture);",
            tryBlock);
        int copy = copyTexSubImage.indexOf("GL11.glCopyTexSubImage2D(", bindDestination);
        int catchBlock = copyTexSubImage.indexOf("catch (RuntimeException | Error exception)", copy);
        int recordFailure = copyTexSubImage.indexOf("failure = exception;", catchBlock);
        int rethrowPrimary = copyTexSubImage.indexOf("throw exception;", recordFailure);
        int finallyBlock = copyTexSubImage.indexOf("} finally {", rethrowPrimary);
        int cleanupFailure = copyTexSubImage.indexOf("Throwable cleanupFailure = runTextureCleanup(null,",
            finallyBlock);
        int restorePrevious = copyTexSubImage.indexOf("bindTextureForLegacyOperation(target, previousTexture)",
            cleanupFailure);
        int suppressCleanup = copyTexSubImage.indexOf("addSuppressedTextureCleanupFailure(failure, cleanupFailure);",
            restorePrevious);
        int rethrowCleanupOnly = copyTexSubImage.indexOf("rethrowTextureCleanupFailure(cleanupFailure);",
            suppressCleanup);

        assertTrue("copyTexSubImage2D must save the caller's active-unit binding for the requested target",
            savePrevious >= 0);
        assertTrue("copyTexSubImage2D must track primary copy failures before mutating state",
            failureLocal > savePrevious);
        assertTrue("copyTexSubImage2D must enter a guarded destination bind after saving state",
            tryBlock > failureLocal);
        assertTrue("copyTexSubImage2D must bind the destination texture only inside the guarded copy block",
            bindDestination > tryBlock);
        assertTrue("copyTexSubImage2D must issue the GL copy after binding the destination texture",
            copy > bindDestination);
        assertTrue("copyTexSubImage2D must preserve the primary copy failure before cleanup",
            catchBlock > copy && recordFailure > catchBlock && rethrowPrimary > recordFailure);
        assertTrue("copyTexSubImage2D must restore the previous binding even when the GL copy fails",
            finallyBlock > rethrowPrimary && cleanupFailure > finallyBlock && restorePrevious > cleanupFailure);
        assertTrue("copyTexSubImage2D cleanup failures must suppress onto primary failures or rethrow alone",
            suppressCleanup > restorePrevious && rethrowCleanupOnly > suppressCleanup);
    }

    @Test
    public void textureAllocationWrapperRestoresDefaultBindingAndCallerActiveUnit() throws IOException {
        String source = readRenderSystem();
        String wrapper = source.substring(
            source.indexOf("public static void withDefaultTextureBindingRestored"),
            source.indexOf("public static void texParameteri"));
        String restoreTexture = source.substring(
            source.indexOf("private static Throwable restoreDefaultTextureBinding"),
            source.indexOf("private static Throwable restoreActiveTextureUnit"));
        String restoreActive = source.substring(
            source.indexOf("private static Throwable restoreActiveTextureUnit"),
            source.indexOf("private static Throwable runTextureCleanup"));

        int saveActiveTexture = wrapper.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);");
        int previousTexture = wrapper.indexOf("int previousTexture = 0;", saveActiveTexture);
        int capturedFlag = wrapper.indexOf("boolean capturedTexture = false;", previousTexture);
        int failureLocal = wrapper.indexOf("Throwable failure = null;", capturedFlag);
        int tryBlock = wrapper.indexOf("try {", failureLocal);
        int defaultUnit = wrapper.indexOf("restoreDefaultActiveTexture();", tryBlock);
        int saveTexture = wrapper.indexOf("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            defaultUnit);
        int markCaptured = wrapper.indexOf("capturedTexture = true;", saveTexture);
        int operationRun = wrapper.indexOf("operation.run();", markCaptured);
        int catchBlock = wrapper.indexOf("catch (RuntimeException | Error exception)", operationRun);
        int recordFailure = wrapper.indexOf("failure = exception;", catchBlock);
        int rethrow = wrapper.indexOf("throw exception;", recordFailure);
        int finallyBlock = wrapper.indexOf("finally {", rethrow);
        int cleanupFailure = wrapper.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int restoreCaptured = wrapper.indexOf("if (capturedTexture)", cleanupFailure);
        int restoreDefault = wrapper.indexOf("restoreDefaultTextureBinding(cleanupFailure, previousTexture);",
            restoreCaptured);
        int restoreCallerUnit = wrapper.indexOf("restoreActiveTextureUnit(cleanupFailure, previousActiveTexture);",
            restoreDefault);
        int suppress = wrapper.indexOf("addSuppressedTextureCleanupFailure(failure, cleanupFailure);",
            restoreCallerUnit);
        int cleanupOnly = wrapper.indexOf("rethrowTextureCleanupFailure(cleanupFailure);", suppress);
        int helperDefaultUnit = restoreTexture.indexOf("restoreDefaultActiveTexture();");
        int helperBind = restoreTexture.indexOf("GlStateManager.bindTexture(texture);", helperDefaultUnit);

        assertTrue("Texture wrapper must save the caller active texture unit first", saveActiveTexture >= 0);
        assertTrue("Texture wrapper must declare cleanup state before GL mutations",
            previousTexture > saveActiveTexture && capturedFlag > previousTexture && failureLocal > capturedFlag);
        assertTrue("Texture wrapper must switch to the default unit before recording that unit's 2D binding",
            tryBlock > failureLocal && defaultUnit > tryBlock && saveTexture > defaultUnit);
        assertTrue("Texture wrapper must run allocation only after default binding capture",
            markCaptured > saveTexture && operationRun > markCaptured);
        assertTrue("Texture wrapper must preserve primary failures for cleanup suppression",
            catchBlock > operationRun && recordFailure > catchBlock && rethrow > recordFailure);
        assertTrue("Texture wrapper must restore default-unit binding before caller active unit",
            finallyBlock > rethrow && cleanupFailure > finallyBlock && restoreCaptured > cleanupFailure
                && restoreDefault > restoreCaptured && restoreCallerUnit > restoreDefault);
        assertTrue("Texture wrapper must suppress cleanup failures or rethrow cleanup-only failures",
            suppress > restoreCallerUnit && cleanupOnly > suppress);
        assertTrue("Texture binding restore must use GlStateManager on the default unit for vanilla cache coherence",
            helperDefaultUnit >= 0 && helperBind > helperDefaultUnit);
        assertTrue("Active unit restore must route through the vanilla-aware texture-unit helper",
            restoreActive.contains("setActiveTextureUnit(activeTexture)"));
    }

    @Test
    public void framebufferBlitDispatchUsesActive112FramebufferBackendForDepthCopyStrategy() throws IOException {
        String source = readRenderSystem();
        String depthCopyStrategy = readSource("src/main/java/net/oculus/rendertarget/DepthCopyStrategy.java");
        String getFramebufferBinding = source.substring(
            source.indexOf("public static int getFramebufferBinding()"),
            source.indexOf("public static int getReadFramebufferBinding()"));
        String getReadFramebufferBinding = source.substring(
            source.indexOf("public static int getReadFramebufferBinding()"),
            source.indexOf("public static int getDrawFramebufferBinding()"));
        String getDrawFramebufferBinding = source.substring(
            source.indexOf("public static int getDrawFramebufferBinding()"),
            source.indexOf("public static void restoreFramebufferBindings"));

        assertTrue(source.contains("public static boolean supportsFramebufferBlit()"));
        assertTrue(source.contains("OpenGlHelper.framebufferSupported"));
        assertTrue(source.contains("capabilities.OpenGL30"));
        assertTrue(source.contains("capabilities.GL_ARB_framebuffer_object"));
        assertTrue(source.contains("capabilities.GL_EXT_framebuffer_object && capabilities.GL_EXT_framebuffer_blit"));
        assertTrue(getFramebufferBinding.contains("GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);"));
        assertTrue(getFramebufferBinding.contains(
            "GL11.glGetInteger(ARBFramebufferObject.GL_FRAMEBUFFER_BINDING);"));
        assertTrue(getFramebufferBinding.contains(
            "GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);"));
        assertTrue(getReadFramebufferBinding.contains("GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);"));
        assertTrue(getReadFramebufferBinding.contains(
            "GL11.glGetInteger(ARBFramebufferObject.GL_READ_FRAMEBUFFER_BINDING);"));
        assertTrue(getReadFramebufferBinding.contains(
            "GL11.glGetInteger(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_BINDING_EXT);"));
        assertTrue(getDrawFramebufferBinding.contains("GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);"));
        assertTrue(getDrawFramebufferBinding.contains(
            "GL11.glGetInteger(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER_BINDING);"));
        assertTrue(getDrawFramebufferBinding.contains(
            "GL11.glGetInteger(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_BINDING_EXT);"));
        assertTrue(source.contains("GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);"));
        assertTrue(source.contains("ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, sourceFramebuffer);"));
        assertTrue(source.contains("EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_EXT, sourceFramebuffer);"));
        assertTrue(source.contains("GL30.glBlitFramebuffer("));
        assertTrue(source.contains("ARBFramebufferObject.glBlitFramebuffer("));
        assertTrue(source.contains("EXTFramebufferBlit.glBlitFramebufferEXT("));
        assertTrue(source.contains("public static void restoreFramebufferBindings(int framebuffer, int readFramebuffer, int drawFramebuffer)"));
        assertTrue(source.contains("public static void bindReadFramebuffer(int framebuffer)"));
        assertTrue(source.contains("GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);"));
        assertTrue(source.contains("ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, framebuffer);"));
        assertTrue(source.contains("EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_EXT, framebuffer);"));
        assertTrue(source.contains("public static void bindDrawFramebuffer(int framebuffer)"));
        assertTrue(source.contains("GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);"));
        assertTrue(source.contains("ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER, framebuffer);"));
        assertTrue(source.contains("EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_EXT, framebuffer);"));
        assertTrue(depthCopyStrategy.contains("OculusRenderSystem.supportsFramebufferBlit()"));
        assertTrue(depthCopyStrategy.contains("OculusRenderSystem.blitFramebuffer("));
        assertFalse(depthCopyStrategy.contains("GL30.glBlitFramebuffer("));
        assertFalse(depthCopyStrategy.contains("ARBFramebufferObject.glBlitFramebuffer("));
        assertFalse(depthCopyStrategy.contains("EXTFramebufferBlit.glBlitFramebufferEXT("));
    }

    @Test
    public void framebufferCleanupRestoresSplitBindingsOnlyWhenBackendSupportsThem() throws IOException {
        String source = readRenderSystem();
        String restore = source.substring(
            source.indexOf("public static void restoreFramebufferBindings(int framebuffer, int readFramebuffer, int drawFramebuffer)"),
            source.indexOf("public static void blitFramebuffer"));

        int blitGuard = restore.indexOf("if (supportsFramebufferBlit())");
        int equalGuard = restore.indexOf("if (readFramebuffer == drawFramebuffer)", blitGuard);
        int restoreUnifiedWhenEqual = restore.indexOf(
            "OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, readFramebuffer);", equalGuard);
        int splitBranch = restore.indexOf("} else {", restoreUnifiedWhenEqual);
        int restoreRead = restore.indexOf("bindReadFramebuffer(readFramebuffer);", splitBranch);
        int restoreDraw = restore.indexOf("bindDrawFramebuffer(drawFramebuffer);", restoreRead);
        int noBlitBranch = restore.indexOf("} else {", restoreDraw);
        int restoreCombined = restore.indexOf(
            "OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);", noBlitBranch);

        assertTrue("Framebuffer cleanup must first respect the active backend's split-FBO capability",
            blitGuard >= 0);
        assertTrue("Split-capable cleanup can restore a single combined binding when read and draw match",
            equalGuard > blitGuard && restoreUnifiedWhenEqual > equalGuard);
        assertTrue("Split-capable cleanup must restore read before draw when the bindings differ",
            splitBranch > restoreUnifiedWhenEqual && restoreRead > splitBranch && restoreDraw > restoreRead);
        assertTrue("Legacy cleanup must fall back to the saved combined framebuffer binding",
            noBlitBranch > restoreDraw && restoreCombined > noBlitBranch);
        assertFalse("Framebuffer restore must not bypass the 1.12 OpenGlHelper backend for combined binds",
            restore.contains("GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER"));
    }

    @Test
    public void drawBuffersDispatchUsesValidatedDuplicateBufferView() throws IOException {
        String source = readRenderSystem();
        String drawBuffers = source.substring(
            source.indexOf("public static void drawBuffers(IntBuffer buffers)"),
            source.indexOf("private static boolean hasOpenGlFunction"));

        int duplicate = drawBuffers.indexOf("IntBuffer drawBuffers = buffers.duplicate();");
        int count = drawBuffers.indexOf("int count = drawBuffers.remaining();", duplicate);
        int singleBuffer = drawBuffers.indexOf("GL11.glDrawBuffer(drawBuffers.get(drawBuffers.position()));", count);
        int coreDispatch = drawBuffers.indexOf("GL20.glDrawBuffers(drawBuffers);", singleBuffer);
        int arbDispatch = drawBuffers.indexOf("ARBDrawBuffers.glDrawBuffersARB(drawBuffers);", coreDispatch);

        assertTrue("drawBuffers must duplicate the caller buffer before validation", duplicate >= 0);
        assertTrue("drawBuffers must count the validated duplicate's remaining attachments",
            count > duplicate);
        assertTrue("Single draw-buffer fallback must read from the duplicate without advancing it",
            singleBuffer > count);
        assertTrue("Core dispatch must pass the same validated duplicate buffer view",
            coreDispatch > singleBuffer);
        assertTrue("ARB dispatch must pass the same validated duplicate buffer view",
            arbDispatch > coreDispatch);
        assertFalse("Core dispatch must not depend on the caller IntBuffer instance after validation",
            drawBuffers.contains("GL20.glDrawBuffers(buffers);"));
        assertFalse("ARB dispatch must not depend on the caller IntBuffer instance after validation",
            drawBuffers.contains("ARBDrawBuffers.glDrawBuffersARB(buffers);"));
    }

    @Test
    public void centerDepthUsesNullSafeOpenGl32Probe() throws IOException {
        String source = readRenderSystem();
        String centerDepthSampler = readSource("src/main/java/net/oculus/postprocess/CenterDepthSampler.java");

        assertTrue(source.contains("public static boolean supportsOpenGL32()"));
        assertTrue(centerDepthSampler.contains("OculusRenderSystem.supportsOpenGL32()"));
        assertFalse(centerDepthSampler.contains("GLContext.getCapabilities()"));
    }

    @Test
    public void copyTexImage2DUpdatesTextureLifecycleCacheForDepthCopies() throws IOException {
        String source = readRenderSystem();
        String copyTexImage2D = source.substring(
            source.indexOf("public static void copyTexImage2D(int target, int level"),
            source.indexOf("public static void copyTexSubImage2D"));

        int copy = copyTexImage2D.indexOf("GL11.glCopyTexImage2D(");
        int track = copyTexImage2D.indexOf(
            "TextureLifecycleTracker.onCopyTexImage2D(target, level, internalFormat, width, height, border);",
            copy);

        assertTrue("copyTexImage2D reallocates the bound texture and must update tracked texture metadata",
            copy >= 0 && track > copy);
    }

    @Test
    public void textureUnitCleanupAvoidsVanillaCachePastEightUnits() throws IOException {
        String source = readRenderSystem();
        String pipelineManager = readSource("src/main/java/net/oculus/pipeline/PipelineManager.java");
        String bindTextureForLegacyOperation = source.substring(
            source.indexOf("private static void bindTextureForLegacyOperation"),
            source.indexOf("private static Throwable restoreDefaultTextureBinding"));
        String setActiveTextureUnit = source.substring(
            source.indexOf("public static void setActiveTextureUnit"),
            source.indexOf("public static void restoreDefaultActiveTexture"));

        assertTrue(source.contains("public static final int VANILLA_CACHED_TEXTURE_UNITS = 8;"));
        assertTrue(source.contains("if (textureUnit < VANILLA_CACHED_TEXTURE_UNITS)"));
        assertTrue(source.contains("public static void setActiveTextureUnit(int glTextureUnit)"));
        assertTrue(source.contains("GlStateManager.setActiveTexture(glTextureUnit);"));
        assertTrue(source.contains("GlStateManager.bindTexture(texture);"));
        assertTrue(source.contains("OpenGlHelper.setActiveTexture(glTextureUnit);"));
        assertTrue(source.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);"));
        assertTrue(source.contains("GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);"));
        assertTrue(source.contains("OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);"));
        int cachedUnitSwitch = setActiveTextureUnit.indexOf("GlStateManager.setActiveTexture(glTextureUnit);");
        int forcedCachedUnitSwitch = setActiveTextureUnit.indexOf(
            "OpenGlHelper.setActiveTexture(glTextureUnit);", cachedUnitSwitch);
        int cachedUnitReturn = setActiveTextureUnit.indexOf("return;", forcedCachedUnitSwitch);
        int highUnitSwitch = setActiveTextureUnit.indexOf("OpenGlHelper.setActiveTexture(glTextureUnit);",
            cachedUnitReturn);
        assertTrue("Low texture units must force the real GL active unit after updating GlStateManager's cache",
            cachedUnitSwitch >= 0 && forcedCachedUnitSwitch > cachedUnitSwitch);
        assertTrue("High texture units must still bypass GlStateManager's fixed-size texture cache",
            highUnitSwitch > cachedUnitReturn);
        assertTrue(bindTextureForLegacyOperation.contains("int textureUnit = currentTextureUnitIndex();"));
        assertTrue(bindTextureForLegacyOperation.contains(
            "if (textureUnit >= 0 && textureUnit < VANILLA_CACHED_TEXTURE_UNITS)"));
        assertTrue(bindTextureForLegacyOperation.contains("GlStateManager.bindTexture(texture);"));
        assertTrue(bindTextureForLegacyOperation.contains("GL11.glBindTexture(target, texture);"));

        assertTrue(pipelineManager.contains("OculusRenderSystem.unbindTexture2DFromUnits(textureUnitCleanupCount());"));
        assertTrue(pipelineManager.contains("Math.max(minimumCleanupUnits, SamplerLimits.get().getMaxTextureUnits())"));
        assertTrue(pipelineManager.contains("return minimumCleanupUnits;"));
        assertFalse(pipelineManager.contains("GlStateManager.bindTexture(0);"));
    }

    @Test
    public void internalPbrTextureParameterBindsSuppressBaseTextureCallbacks() throws IOException {
        String renderSystem = readRenderSystem();
        String mixin = readSource("src/main/java/net/oculus/mixin/pipeline/GlStateManagerStateMixin.java");
        String textureFormat = readSource("src/main/java/net/oculus/texture/format/TextureFormat.java");

        String suppression = renderSystem.substring(
            renderSystem.indexOf("public static void runWithoutTextureBindCallback"),
            renderSystem.indexOf("public static boolean isTextureBindCallbackSuppressed"));
        String suppressed = renderSystem.substring(
            renderSystem.indexOf("public static boolean isTextureBindCallbackSuppressed"),
            renderSystem.indexOf("public static void setActiveTextureUnit"));
        String setup = textureFormat.substring(
            textureFormat.indexOf("default void setupTextureParameters"),
            textureFormat.indexOf("interface Factory"));

        int depthField = renderSystem.indexOf("private static int textureBindCallbackSuppressionDepth;");
        int increment = suppression.indexOf("textureBindCallbackSuppressionDepth++;");
        int tryBlock = suppression.indexOf("try {", increment);
        int run = suppression.indexOf("operation.run();", tryBlock);
        int finallyBlock = suppression.indexOf("finally {", run);
        int decrement = suppression.indexOf("textureBindCallbackSuppressionDepth--;", finallyBlock);
        int query = suppressed.indexOf("return textureBindCallbackSuppressionDepth > 0;");
        int mixinSuppressed = mixin.indexOf("OculusRenderSystem.isTextureBindCallbackSuppressed()");
        int notify = mixin.indexOf("StateUpdateNotifiers::notifyTextureBindingChanged", mixinSuppressed);
        int setupGuard = setup.indexOf("OculusRenderSystem.runWithoutTextureBindCallback(() -> {");
        int bindPbr = setup.indexOf("GlStateManager.bindTexture(textureId);", setupGuard);
        int texParameter = setup.indexOf("GL11.glTexParameteri(", bindPbr);
        int restore = setup.indexOf("GlStateManager.bindTexture(previousTextureBinding);", texParameter);

        assertTrue("Render system must own a nesting-aware callback suppression counter", depthField >= 0);
        assertTrue("Suppression must cover the whole operation and always decrement",
            increment >= 0 && tryBlock > increment && run > tryBlock && finallyBlock > run && decrement > finallyBlock);
        assertTrue("Mixin suppression checks must read the counter",
            query >= 0 && mixinSuppressed >= 0 && notify > mixinSuppressed);
        assertTrue("Texture format PBR parameter edits must bind through GlStateManager under suppression",
            setupGuard >= 0 && bindPbr > setupGuard && texParameter > bindPbr && restore > texParameter);
    }

    private static String readRenderSystem() throws IOException {
        return readSource("src/main/java/net/oculus/gl/OculusRenderSystem.java");
    }

    private static String readSource(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
