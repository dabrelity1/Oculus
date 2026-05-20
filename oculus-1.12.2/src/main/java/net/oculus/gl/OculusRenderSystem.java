package net.oculus.gl;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.oculus.gl.shader.ShaderType;
import net.oculus.texture.TextureLifecycleTracker;
import net.oculus.vendored.joml.Vector3i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBClearBufferObject;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.ARBCopyImage;
import org.lwjgl.opengl.ARBDrawBuffers;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBComputeShader;
import org.lwjgl.opengl.ARBShaderImageLoadStore;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTDrawBuffers2;
import org.lwjgl.opengl.EXTShaderImageLoadStore;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLContext;

/**
 * Lightweight shim that emulates the 1.16.5 {@code IrisRenderSystem} entry points while
 * delegating to the LWJGL 2 OpenGL bindings available on 1.12.2.
 */
public final class OculusRenderSystem {
    public static final String DISABLE_GL_CAPABILITY_PROBES_PROPERTY = "oculus.disableGlCapabilityProbes";
    public static final int VANILLA_CACHED_TEXTURE_UNITS = 8;
    private static int textureBindCallbackSuppressionDepth;

    private OculusRenderSystem() {
    }

    public static int glCreateShader(int type) {
        int handle = GL20.glCreateShader(type);
        if (handle == 0) {
            throw new IllegalStateException("Failed to create shader object (type=" + type + ")");
        }
        return handle;
    }

    public static int createShader(ShaderType type) {
        return glCreateShader(type.id);
    }

    public static void glDeleteShader(int shaderId) {
        if (shaderId != 0) {
            GL20.glDeleteShader(shaderId);
        }
    }

    public static void deleteShader(int shaderId) {
        glDeleteShader(shaderId);
    }

    public static void glCompileShader(int shaderId) {
        GL20.glCompileShader(shaderId);
    }

    public static void compileShader(int shaderId) {
        glCompileShader(shaderId);
    }

    public static void glShaderSource(int shaderId, String source) {
        GL20.glShaderSource(shaderId, source);
    }

    public static int glGetShaderi(int shaderId, int pname) {
        return GL20.glGetShaderi(shaderId, pname);
    }

    public static int getShaderParameter(int shaderId, int parameter) {
        return glGetShaderi(shaderId, parameter);
    }

    public static int glCreateProgram() {
        int handle = GL20.glCreateProgram();
        if (handle == 0) {
            throw new IllegalStateException("Failed to create shader program");
        }
        return handle;
    }

    public static int createProgram() {
        return glCreateProgram();
    }

    public static void glDeleteProgram(int programId) {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
    }

    public static void deleteProgram(int programId) {
        glDeleteProgram(programId);
    }

    public static void glAttachShader(int programId, int shaderId) {
        GL20.glAttachShader(programId, shaderId);
    }

    public static void attachShader(int programId, int shaderId) {
        glAttachShader(programId, shaderId);
    }

    public static void glDetachShader(int programId, int shaderId) {
        GL20.glDetachShader(programId, shaderId);
    }

    public static void detachShader(int programId, int shaderId) {
        glDetachShader(programId, shaderId);
    }

    public static void glLinkProgram(int programId) {
        GL20.glLinkProgram(programId);
    }

    public static void linkProgram(int programId) {
        glLinkProgram(programId);
    }

    public static int glGetProgrami(int programId, int pname) {
        return GL20.glGetProgrami(programId, pname);
    }

    public static int getProgramParameter(int programId, int parameter) {
        return glGetProgrami(programId, parameter);
    }

    public static void bindAttributeLocation(int programId, int index, String name) {
        GL20.glBindAttribLocation(programId, index, name);
    }

    public static String glGetProgramInfoLog(int programId, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        String log = GL20.glGetProgramInfoLog(programId, maxLength);
        return log == null ? "" : log.trim();
    }

    public static String getProgramInfoLog(int programId) {
        int length = glGetProgrami(programId, GL20.GL_INFO_LOG_LENGTH);
        return glGetProgramInfoLog(programId, length);
    }

    public static String glGetShaderInfoLog(int shaderId, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        String log = GL20.glGetShaderInfoLog(shaderId, maxLength);
        return log == null ? "" : log.trim();
    }

    public static String getShaderInfoLog(int shaderId) {
        int length = glGetShaderi(shaderId, GL20.GL_INFO_LOG_LENGTH);
        return glGetShaderInfoLog(shaderId, length);
    }

    public static void glUseProgram(int programId) {
        GL20.glUseProgram(programId);
    }

    public static void useProgram(int programId) {
        glUseProgram(programId);
    }

    public static void bindTexture2DToUnit(int textureUnit, int texture) {
        if (textureUnit < 0) {
            throw new IllegalArgumentException("Texture unit cannot be negative: " + textureUnit);
        }

        int glTextureUnit = GL13.GL_TEXTURE0 + textureUnit;
        if (textureUnit < VANILLA_CACHED_TEXTURE_UNITS) {
            setActiveTextureUnit(glTextureUnit);
            GlStateManager.bindTexture(texture);
            return;
        }

        setActiveTextureUnit(glTextureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    public static void bindSamplerTexture2DToUnit(int textureUnit, int texture) {
        if (textureUnit < 0) {
            throw new IllegalArgumentException("Texture unit cannot be negative: " + textureUnit);
        }

        int glTextureUnit = GL13.GL_TEXTURE0 + textureUnit;
        setActiveTextureUnit(glTextureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        if (textureUnit < VANILLA_CACHED_TEXTURE_UNITS) {
            runWithoutTextureBindCallback(() -> GlStateManager.bindTexture(texture));
        }
    }

    public static void runWithoutTextureBindCallback(Runnable operation) {
        if (operation == null) {
            throw new NullPointerException("operation");
        }

        textureBindCallbackSuppressionDepth++;
        try {
            operation.run();
        } finally {
            textureBindCallbackSuppressionDepth--;
        }
    }

    public static boolean isTextureBindCallbackSuppressed() {
        return textureBindCallbackSuppressionDepth > 0;
    }

    public static void setActiveTextureUnit(int glTextureUnit) {
        int textureUnit = glTextureUnit - OpenGlHelper.defaultTexUnit;
        if (textureUnit >= 0 && textureUnit < VANILLA_CACHED_TEXTURE_UNITS) {
            GlStateManager.setActiveTexture(glTextureUnit);
            OpenGlHelper.setActiveTexture(glTextureUnit);
            return;
        }

        OpenGlHelper.setActiveTexture(glTextureUnit);
    }

    public static void restoreDefaultActiveTexture() {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    public static void unbindTexture2DFromUnits(int textureUnitCount) {
        if (textureUnitCount < 0) {
            throw new IllegalArgumentException("Texture unit count cannot be negative: " + textureUnitCount);
        }

        for (int i = 0; i < textureUnitCount; i++) {
            bindTexture2DToUnit(i, 0);
        }
        restoreDefaultActiveTexture();
    }

    public static int glGetUniformLocation(int programId, String name) {
        if (name == null) {
            throw new IllegalArgumentException("Uniform name cannot be null");
        }
        return GL20.glGetUniformLocation(programId, name);
    }

    public static int getUniformLocation(int programId, String name) {
        return glGetUniformLocation(programId, name);
    }

    public static String getActiveUniform(int program, int index) {
        int maxLength = glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
        if (maxLength <= 0) {
            return "";
        }

        IntBuffer lengthBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);
        ByteBuffer nameBuffer = BufferUtils.createByteBuffer(maxLength);

        GL20.glGetActiveUniform(program, index, lengthBuffer, sizeBuffer, typeBuffer, nameBuffer);

        int nameLength = lengthBuffer.get(0);
        if (nameLength <= 0) {
            return "";
        }

        byte[] bytes = new byte[nameLength];
        for (int i = 0; i < nameLength; i++) {
            bytes[i] = nameBuffer.get(i);
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static boolean supportsCompute() {
        ContextCapabilities capabilities = getCapabilities();
        return capabilities != null
            && (capabilities.OpenGL43
                || capabilities.GL_ARB_compute_shader
                || hasOpenGlFunction(capabilities, "glDispatchCompute"));
    }

    public static boolean supportsCopyImageSubData() {
        ContextCapabilities capabilities = getCapabilities();
        return capabilities != null
            && (capabilities.OpenGL43
                || capabilities.GL_ARB_copy_image
                || hasOpenGlFunction(capabilities, "glCopyImageSubData"));
    }

    public static boolean supportsFramebufferBlit() {
        ContextCapabilities capabilities = getCapabilities();
        return OpenGlHelper.framebufferSupported
            && capabilities != null
            && (capabilities.OpenGL30
                || capabilities.GL_ARB_framebuffer_object
                || (capabilities.GL_EXT_framebuffer_object && capabilities.GL_EXT_framebuffer_blit));
    }

    public static boolean supportsOpenGL32() {
        ContextCapabilities capabilities = getCapabilities();
        return capabilities != null && capabilities.OpenGL32;
    }

    public static boolean supportsShaderStorageBuffers() {
        ContextCapabilities capabilities = getCapabilities();
        return capabilities != null
            && (capabilities.OpenGL43
                || capabilities.GL_ARB_shader_storage_buffer_object
                || hasOpenGlFunction(capabilities, "glShaderStorageBlockBinding"));
    }

    public static boolean supportsBufferBlending() {
        ContextCapabilities capabilities = getCapabilities();
        return supportsBufferBlending(capabilities);
    }

    public static boolean areCapabilityProbesDisabled() {
        return Boolean.getBoolean(DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
    }

    public static void disableBufferBlend(int buffer) {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Per-buffer blending capabilities are unavailable, but a shader program attempted to disable blend state for draw buffer "
                + buffer + ".");
        }

        if (!supportsBufferBlending(capabilities)) {
            throw new IllegalStateException("Per-buffer blending is not supported, but a shader program attempted to disable blend state for draw buffer "
                + buffer + ".");
        }

        if (capabilities.OpenGL30 || hasOpenGlFunction(capabilities, "glDisablei")) {
            GL30.glDisablei(GL11.GL_BLEND, buffer);
        } else {
            EXTDrawBuffers2.glDisableIndexedEXT(GL11.GL_BLEND, buffer);
        }
    }

    public static void enableBufferBlend(int buffer) {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Per-buffer blending capabilities are unavailable, but a shader program attempted to enable blend state for draw buffer "
                + buffer + ".");
        }

        if (!supportsBufferBlending(capabilities)) {
            throw new IllegalStateException("Per-buffer blending is not supported, but a shader program attempted to enable blend state for draw buffer "
                + buffer + ".");
        }

        if (capabilities.OpenGL30 || hasOpenGlFunction(capabilities, "glEnablei")) {
            GL30.glEnablei(GL11.GL_BLEND, buffer);
        } else {
            EXTDrawBuffers2.glEnableIndexedEXT(GL11.GL_BLEND, buffer);
        }
    }

    public static void blendFuncSeparatei(int buffer, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Per-buffer blending capabilities are unavailable, but a shader program attempted to set blend functions for draw buffer "
                + buffer + ".");
        }

        if (!supportsBufferBlending(capabilities)) {
            throw new IllegalStateException("Per-buffer blending is not supported, but a shader program attempted to set blend functions for draw buffer "
                + buffer + ".");
        }

        if (capabilities.OpenGL40 || hasOpenGlFunction(capabilities, "glBlendFuncSeparatei")) {
            GL40.glBlendFuncSeparatei(buffer, srcRgb, dstRgb, srcAlpha, dstAlpha);
        } else {
            ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }

    private static boolean supportsBufferBlending(ContextCapabilities capabilities) {
        if (capabilities == null) {
            return false;
        }

        boolean supportsBlendFunction = capabilities.OpenGL40
            || capabilities.GL_ARB_draw_buffers_blend
            || hasOpenGlFunction(capabilities, "glBlendFuncSeparatei")
            || hasOpenGlFunction(capabilities, "glBlendFuncSeparateiARB");
        boolean supportsIndexedBlendState = capabilities.OpenGL30
            || capabilities.GL_EXT_draw_buffers2
            || (hasOpenGlFunction(capabilities, "glEnablei")
                && hasOpenGlFunction(capabilities, "glDisablei"));

        return supportsBlendFunction && supportsIndexedBlendState;
    }

    private static boolean supportsImageLoadStore() {
        ContextCapabilities capabilities = getCapabilities();
        return capabilities != null && (capabilities.OpenGL42
            || capabilities.GL_ARB_shader_image_load_store
            || capabilities.GL_EXT_shader_image_load_store
            || hasOpenGlFunction(capabilities, "glBindImageTexture"));
    }

    public static void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        if (!supportsImageLoadStore()) {
            throw new IllegalStateException("Image load/store is not supported, but a shader program attempted to bind image texture "
                + texture + " to image unit " + unit + ".");
        }

        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Image load/store capabilities are unavailable, but a shader program attempted to bind image texture "
                + texture + " to image unit " + unit + ".");
        }

        if (capabilities.OpenGL42 || hasOpenGlFunction(capabilities, "glBindImageTexture")) {
            GL42.glBindImageTexture(unit, texture, level, layered, layer, access, format);
        } else if (capabilities.GL_ARB_shader_image_load_store) {
            ARBShaderImageLoadStore.glBindImageTexture(unit, texture, level, layered, layer, access, format);
        } else {
            EXTShaderImageLoadStore.glBindImageTextureEXT(unit, texture, level, layered, layer, access, format);
        }
    }

    public static int getMaxImageUnits() {
        if (!supportsImageLoadStore()) {
            return 0;
        }

        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            return 0;
        }

        if (capabilities.OpenGL42 || hasOpenGlFunction(capabilities, "glBindImageTexture")) {
            return GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS);
        }

        if (capabilities.GL_ARB_shader_image_load_store) {
            return GL11.glGetInteger(ARBShaderImageLoadStore.GL_MAX_IMAGE_UNITS);
        }

        return GL11.glGetInteger(EXTShaderImageLoadStore.GL_MAX_IMAGE_UNITS_EXT);
    }

    public static boolean supportsClearTexture() {
        ContextCapabilities capabilities = getCapabilities();
        return capabilities != null
            && (capabilities.OpenGL44
                || capabilities.GL_ARB_clear_texture
                || hasOpenGlFunction(capabilities, "glClearTexImage"));
    }

    public static boolean clearTexImage(int texture, int level, int format, int type, ByteBuffer data) {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            return false;
        }

        if (capabilities.OpenGL44 || hasOpenGlFunction(capabilities, "glClearTexImage")) {
            GL44.glClearTexImage(texture, level, format, type, data);
            return true;
        }

        if (capabilities.GL_ARB_clear_texture) {
            ARBClearTexture.glClearTexImage(texture, level, format, type, data);
            return true;
        }

        return false;
    }

    public static void generateMipmaps(int texture, int target) {
        int previousTexture = GL11.glGetInteger(textureBindingParameter(target));
        Throwable failure = null;
        try {
            bindTextureForLegacyOperation(target, texture);

            ContextCapabilities capabilities = getCapabilities();
            if (capabilities == null) {
                throw new IllegalStateException("Mipmap generation capabilities are unavailable for texture " + texture + ".");
            }

            if (capabilities.OpenGL30 || hasOpenGlFunction(capabilities, "glGenerateMipmap")) {
                GL30.glGenerateMipmap(target);
            } else if (capabilities.GL_ARB_framebuffer_object) {
                ARBFramebufferObject.glGenerateMipmap(target);
            } else if (capabilities.GL_EXT_framebuffer_object) {
                EXTFramebufferObject.glGenerateMipmapEXT(target);
            } else {
                throw new IllegalStateException("Mipmap generation is not supported by the active OpenGL context for texture "
                    + texture + ".");
            }
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = runTextureCleanup(null,
                () -> bindTextureForLegacyOperation(target, previousTexture));
            addSuppressedTextureCleanupFailure(failure, cleanupFailure);
            if (failure == null) {
                rethrowTextureCleanupFailure(cleanupFailure);
            }
        }
    }

    public static void withDefaultTextureBindingRestored(Runnable operation) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = 0;
        boolean capturedTexture = false;
        Throwable failure = null;

        try {
            restoreDefaultActiveTexture();
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            capturedTexture = true;
            operation.run();
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (capturedTexture) {
                cleanupFailure = restoreDefaultTextureBinding(cleanupFailure, previousTexture);
            }
            cleanupFailure = restoreActiveTextureUnit(cleanupFailure, previousActiveTexture);
            addSuppressedTextureCleanupFailure(failure, cleanupFailure);
            if (failure == null) {
                rethrowTextureCleanupFailure(cleanupFailure);
            }
        }
    }

    public static void texParameteri(int texture, int target, int pname, int param) {
        bindTextureForLegacyOperation(target, texture);
        GL11.glTexParameteri(target, pname, param);
    }

    public static void texParameter(int texture, int target, int pname, IntBuffer params) {
        bindTextureForLegacyOperation(target, texture);
        GL11.glTexParameter(target, pname, params);
    }

    public static void texImage2D(int texture, int target, int level, int internalFormat, int width, int height,
                                  int border, int format, int type, ByteBuffer pixels) {
        bindTextureForLegacyOperation(target, texture);
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
        TextureLifecycleTracker.onTexImage2D(texture, target, level, internalFormat, width, height);
    }

    public static void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height,
                                      int border) {
        GL11.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
        TextureLifecycleTracker.onCopyTexImage2D(target, level, internalFormat, width, height, border);
    }

    public static void copyTexSubImage2D(int destinationTexture, int target, int level, int xOffset, int yOffset,
                                         int x, int y, int width, int height) {
        int previousTexture = GL11.glGetInteger(textureBindingParameter(target));
        Throwable failure = null;
        try {
            bindTextureForLegacyOperation(target, destinationTexture);
            GL11.glCopyTexSubImage2D(target, level, xOffset, yOffset, x, y, width, height);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = runTextureCleanup(null,
                () -> bindTextureForLegacyOperation(target, previousTexture));
            addSuppressedTextureCleanupFailure(failure, cleanupFailure);
            if (failure == null) {
                rethrowTextureCleanupFailure(cleanupFailure);
            }
        }
    }

    public static void copyImageSubData(int sourceTexture, int sourceTarget, int sourceLevel,
                                        int sourceX, int sourceY, int sourceZ,
                                        int destinationTexture, int destinationTarget, int destinationLevel,
                                        int destinationX, int destinationY, int destinationZ,
                                        int width, int height, int depth) {
        if (!supportsCopyImageSubData()) {
            throw new IllegalStateException("Texture-to-texture image copies are not supported, but a depth-copy path attempted to copy texture "
                + sourceTexture + " to texture " + destinationTexture + ".");
        }

        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Texture-to-texture image copy capabilities are unavailable, but a depth-copy path attempted to copy texture "
                + sourceTexture + " to texture " + destinationTexture + ".");
        }

        if (capabilities.OpenGL43 || hasOpenGlFunction(capabilities, "glCopyImageSubData")) {
            GL43.glCopyImageSubData(
                sourceTexture, sourceTarget, sourceLevel, sourceX, sourceY, sourceZ,
                destinationTexture, destinationTarget, destinationLevel, destinationX, destinationY, destinationZ,
                width, height, depth);
        } else {
            ARBCopyImage.glCopyImageSubData(
                sourceTexture, sourceTarget, sourceLevel, sourceX, sourceY, sourceZ,
                destinationTexture, destinationTarget, destinationLevel, destinationX, destinationY, destinationZ,
                width, height, depth);
        }
    }

    public static int getFramebufferBinding() {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities != null && capabilities.OpenGL30) {
            return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        }
        if (capabilities != null && capabilities.GL_ARB_framebuffer_object) {
            return GL11.glGetInteger(ARBFramebufferObject.GL_FRAMEBUFFER_BINDING);
        }
        if (capabilities != null && capabilities.GL_EXT_framebuffer_object) {
            return GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        }
        return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    public static int getReadFramebufferBinding() {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities != null && capabilities.OpenGL30) {
            return GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        }
        if (capabilities != null && capabilities.GL_ARB_framebuffer_object) {
            return GL11.glGetInteger(ARBFramebufferObject.GL_READ_FRAMEBUFFER_BINDING);
        }
        if (capabilities != null && capabilities.GL_EXT_framebuffer_object && capabilities.GL_EXT_framebuffer_blit) {
            return GL11.glGetInteger(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_BINDING_EXT);
        }
        return getFramebufferBinding();
    }

    public static int getDrawFramebufferBinding() {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities != null && capabilities.OpenGL30) {
            return GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        }
        if (capabilities != null && capabilities.GL_ARB_framebuffer_object) {
            return GL11.glGetInteger(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER_BINDING);
        }
        if (capabilities != null && capabilities.GL_EXT_framebuffer_object && capabilities.GL_EXT_framebuffer_blit) {
            return GL11.glGetInteger(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_BINDING_EXT);
        }
        return getFramebufferBinding();
    }

    public static void restoreFramebufferBindings(int framebuffer, int readFramebuffer, int drawFramebuffer) {
        if (supportsFramebufferBlit()) {
            if (readFramebuffer == drawFramebuffer) {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, readFramebuffer);
            } else {
                bindReadFramebuffer(readFramebuffer);
                bindDrawFramebuffer(drawFramebuffer);
            }
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        }
    }

    public static void blitFramebuffer(int sourceFramebuffer, int destinationFramebuffer,
                                       int sourceX0, int sourceY0, int sourceX1, int sourceY1,
                                       int destinationX0, int destinationY0, int destinationX1, int destinationY1,
                                       int mask, int filter) {
        if (!supportsFramebufferBlit()) {
            throw new IllegalStateException("Framebuffer blits are not supported, but a depth-copy path attempted to blit framebuffer "
                + sourceFramebuffer + " to framebuffer " + destinationFramebuffer + ".");
        }

        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Framebuffer blit capabilities are unavailable, but a depth-copy path attempted to blit framebuffer "
                + sourceFramebuffer + " to framebuffer " + destinationFramebuffer + ".");
        }

        if (capabilities.OpenGL30) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destinationFramebuffer);
            GL30.glBlitFramebuffer(sourceX0, sourceY0, sourceX1, sourceY1,
                destinationX0, destinationY0, destinationX1, destinationY1, mask, filter);
        } else if (capabilities.GL_ARB_framebuffer_object) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER, destinationFramebuffer);
            ARBFramebufferObject.glBlitFramebuffer(sourceX0, sourceY0, sourceX1, sourceY1,
                destinationX0, destinationY0, destinationX1, destinationY1, mask, filter);
        } else if (capabilities.GL_EXT_framebuffer_object && capabilities.GL_EXT_framebuffer_blit) {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_EXT, sourceFramebuffer);
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_EXT, destinationFramebuffer);
            EXTFramebufferBlit.glBlitFramebufferEXT(sourceX0, sourceY0, sourceX1, sourceY1,
                destinationX0, destinationY0, destinationX1, destinationY1, mask, filter);
        } else {
            throw new IllegalStateException("Framebuffer blits are not supported by the active framebuffer backend.");
        }
    }

    public static void bindReadFramebuffer(int framebuffer) {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities != null && capabilities.OpenGL30) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        } else if (capabilities != null && capabilities.GL_ARB_framebuffer_object) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_READ_FRAMEBUFFER, framebuffer);
        } else if (capabilities != null && capabilities.GL_EXT_framebuffer_object && capabilities.GL_EXT_framebuffer_blit) {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_EXT, framebuffer);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        }
    }

    public static void bindDrawFramebuffer(int framebuffer) {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities != null && capabilities.OpenGL30) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        } else if (capabilities != null && capabilities.GL_ARB_framebuffer_object) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_DRAW_FRAMEBUFFER, framebuffer);
        } else if (capabilities != null && capabilities.GL_EXT_framebuffer_object && capabilities.GL_EXT_framebuffer_blit) {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_EXT, framebuffer);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        }
    }

    public static int getMaxDrawBuffers() {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities != null && (capabilities.OpenGL20 || hasOpenGlFunction(capabilities, "glDrawBuffers"))) {
            return GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        }
        if (capabilities != null && capabilities.GL_ARB_draw_buffers) {
            return GL11.glGetInteger(ARBDrawBuffers.GL_MAX_DRAW_BUFFERS_ARB);
        }
        return 1;
    }

    public static int getMaxColorAttachments() {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities != null && capabilities.OpenGL30) {
            return GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
        }
        if (capabilities != null && capabilities.GL_ARB_framebuffer_object) {
            return GL11.glGetInteger(ARBFramebufferObject.GL_MAX_COLOR_ATTACHMENTS);
        }
        if (capabilities != null && capabilities.GL_EXT_framebuffer_object) {
            return GL11.glGetInteger(EXTFramebufferObject.GL_MAX_COLOR_ATTACHMENTS_EXT);
        }
        return 1;
    }

    public static void drawBuffers(IntBuffer buffers) {
        if (buffers == null) {
            throw new NullPointerException("buffers");
        }

        IntBuffer drawBuffers = buffers.duplicate();
        int count = drawBuffers.remaining();
        if (count <= 0) {
            throw new IllegalArgumentException("At least one draw buffer must be specified.");
        }

        if (count == 1) {
            GL11.glDrawBuffer(drawBuffers.get(drawBuffers.position()));
            return;
        }

        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Multiple draw buffer capabilities are unavailable for "
                + count + " framebuffer attachments.");
        }
        if (capabilities.OpenGL20 || hasOpenGlFunction(capabilities, "glDrawBuffers")) {
            GL20.glDrawBuffers(drawBuffers);
            return;
        }
        if (capabilities.GL_ARB_draw_buffers) {
            ARBDrawBuffers.glDrawBuffersARB(drawBuffers);
            return;
        }

        throw new IllegalStateException("Multiple draw buffers require OpenGL 2.0 or ARB_draw_buffers on the 1.12.2 framebuffer backend.");
    }

    private static boolean hasOpenGlFunction(ContextCapabilities capabilities, String fieldName) {
        if (capabilities == null) {
            return false;
        }

        try {
            Field field = ContextCapabilities.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getLong(capabilities) != 0L;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    private static void bindTextureForLegacyOperation(int target, int texture) {
        if (target == GL11.GL_TEXTURE_2D) {
            int textureUnit = currentTextureUnitIndex();
            if (textureUnit >= 0 && textureUnit < VANILLA_CACHED_TEXTURE_UNITS) {
                GlStateManager.bindTexture(texture);
            } else {
                GL11.glBindTexture(target, texture);
            }
        } else {
            GL11.glBindTexture(target, texture);
        }
    }

    private static int currentTextureUnitIndex() {
        return GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - OpenGlHelper.defaultTexUnit;
    }

    private static int textureBindingParameter(int target) {
        if (target == GL11.GL_TEXTURE_1D) {
            return GL11.GL_TEXTURE_BINDING_1D;
        }
        if (target == GL11.GL_TEXTURE_2D) {
            return GL11.GL_TEXTURE_BINDING_2D;
        }
        if (target == GL12.GL_TEXTURE_3D) {
            return GL12.GL_TEXTURE_BINDING_3D;
        }
        if (target == GL13.GL_TEXTURE_CUBE_MAP) {
            return GL13.GL_TEXTURE_BINDING_CUBE_MAP;
        }
        throw new IllegalArgumentException("Unsupported texture target for texture binding restore: " + target);
    }

    private static Throwable restoreDefaultTextureBinding(Throwable failure, int texture) {
        return runTextureCleanup(failure, () -> {
            restoreDefaultActiveTexture();
            GlStateManager.bindTexture(texture);
        });
    }

    private static Throwable restoreActiveTextureUnit(Throwable failure, int activeTexture) {
        return runTextureCleanup(failure, () -> setActiveTextureUnit(activeTexture));
    }

    private static Throwable runTextureCleanup(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            failure = addTextureCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable addTextureCleanupFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }

    private static void addSuppressedTextureCleanupFailure(Throwable primary, Throwable cleanupFailure) {
        if (primary != null && cleanupFailure != null) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private static void rethrowTextureCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }

    public static boolean supportsClearBufferData() {
        ContextCapabilities capabilities = getCapabilities();
        return capabilities != null
            && (capabilities.OpenGL43
                || capabilities.GL_ARB_clear_buffer_object
                || hasOpenGlFunction(capabilities, "glClearBufferData"));
    }

    public static boolean clearBufferData(int target, int internalFormat, int format, int type, ByteBuffer data) {
        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            return false;
        }

        if (capabilities.OpenGL43 || hasOpenGlFunction(capabilities, "glClearBufferData")) {
            GL43.glClearBufferData(target, internalFormat, format, type, data);
            return true;
        }

        if (capabilities.GL_ARB_clear_buffer_object) {
            ARBClearBufferObject.glClearBufferData(target, internalFormat, format, type, data);
            return true;
        }

        return false;
    }

    public static void dispatchCompute(int workX, int workY, int workZ) {
        if (!supportsCompute()) {
            throw new IllegalStateException("Compute shaders are not supported, but a shader program attempted to dispatch work groups "
                + workX + "x" + workY + "x" + workZ + ".");
        }

        ContextCapabilities capabilities = getCapabilities();
        if (capabilities == null) {
            throw new IllegalStateException("Compute shader capabilities are unavailable, but a shader program attempted to dispatch work groups "
                + workX + "x" + workY + "x" + workZ + ".");
        }

        if (capabilities.OpenGL43 || hasOpenGlFunction(capabilities, "glDispatchCompute")) {
            GL43.glDispatchCompute(workX, workY, workZ);
        } else {
            ARBComputeShader.glDispatchCompute(workX, workY, workZ);
        }
    }

    public static void dispatchCompute(Vector3i workGroups) {
        dispatchCompute(workGroups.x(), workGroups.y(), workGroups.z());
    }

    public static void memoryBarrier(int barriers) {
        int effectiveBarriers = barriers;
        if (!supportsShaderStorageBuffers()) {
            effectiveBarriers &= ~ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BARRIER_BIT;
        }

        if (!supportsImageLoadStore()) {
            effectiveBarriers &= ~(GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }

        ContextCapabilities capabilities = getCapabilities();
        boolean supportsCoreMemoryBarrier = capabilities != null
            && (capabilities.OpenGL42 || hasOpenGlFunction(capabilities, "glMemoryBarrier"));
        if (effectiveBarriers == 0 || capabilities == null
            || (!supportsCoreMemoryBarrier
                && !capabilities.GL_ARB_shader_image_load_store
                && !capabilities.GL_EXT_shader_image_load_store)) {
            return;
        }

        if (supportsCoreMemoryBarrier) {
            GL42.glMemoryBarrier(effectiveBarriers);
        } else if (capabilities.GL_ARB_shader_image_load_store) {
            ARBShaderImageLoadStore.glMemoryBarrier(effectiveBarriers);
        } else {
            EXTShaderImageLoadStore.glMemoryBarrierEXT(effectiveBarriers);
        }
    }

    private static ContextCapabilities getCapabilities() {
        if (areCapabilityProbesDisabled()) {
            return null;
        }

        try {
            return GLContext.getCapabilities();
        } catch (LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public static void getProgramiv(int program, int pname, IntBuffer params) {
        GL20.glGetProgram(program, pname, params);
    }
}
