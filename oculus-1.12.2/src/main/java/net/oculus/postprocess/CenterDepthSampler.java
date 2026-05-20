package net.oculus.postprocess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.IntSupplier;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.texture.DepthBufferFormat;
import net.oculus.rendertarget.DepthCopyStrategy;
import net.oculus.samplers.IrisSamplers;
import net.oculus.texture.TextureLifecycleTracker;
import net.oculus.uniforms.SystemTimeUniforms;
import net.oculus.uniforms.transforms.ExponentialSmoothing;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Samples the depth at the center of the main depth texture into a 1x1 smoothed color
 * texture for shader effects like depth of field and auto-focus.
 *
 * <p>This mirrors the 1.16.5 Oculus/Iris contract: shader-pack references to the old
 * {@code uniform float centerDepthSmooth} are transformed into reads from the dynamic
 * {@code iris_centerDepthSmooth} sampler.</p>
 */
public final class CenterDepthSampler {
    private final Program program;
    private final GlFramebuffer framebuffer;
    private final int texture;
    private final int altTexture;
    private boolean hasFirstSample;
    private boolean hasUsage;
    private boolean destroyed;

    public CenterDepthSampler(IntSupplier depthSupplier, float halfLife) {
        int createdTexture = 0;
        int createdAltTexture = 0;
        GlFramebuffer createdFramebuffer = null;
        Program createdProgram = null;
        boolean complete = false;
        try {
            createdTexture = createTexture("center depth texture");
            createdAltTexture = createTexture("center depth alternate texture");
            createdFramebuffer = new GlFramebuffer();

            int internalFormat = usesGl3Output() ? GL30.GL_R32F : GL11.GL_RGB16;
            int pixelFormat = usesGl3Output() ? GL11.GL_RED : GL11.GL_RGB;
            final int textureId = createdTexture;
            final int setupAltTextureId = createdAltTexture;
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                setupColorTexture(textureId, internalFormat, pixelFormat);
                setupColorTexture(setupAltTextureId, internalFormat, pixelFormat);
            });

            createdFramebuffer.addColorAttachment(0, createdTexture);
            createdFramebuffer.drawBuffers(new int[] {0});
            createdFramebuffer.readBuffer(0);
            if (!createdFramebuffer.isComplete()) {
                throw new IllegalStateException("Center-depth framebuffer is incomplete");
            }

            final int altTextureId = createdAltTexture;
            ProgramBuilder builder = ProgramBuilder.beginExplicit(
                "centerDepthSmooth",
                readResource("/centerDepth.vsh"),
                null,
                prepareFragmentSource(readResource("/centerDepth.fsh")),
                IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
            builder.addDynamicSampler(depthSupplier, "depth");
            builder.addDynamicSampler(() -> altTextureId, "altDepth");
            builder.uniforms().addFloat("lastFrameTime", SystemTimeUniforms.TIMER::getLastFrameTime);
            builder.uniforms().addFloat("decay", () -> ExponentialSmoothing.decayFromHalfLifeSeconds(halfLife * 0.1F));
            createdProgram = builder.build();
            complete = true;
        } catch (RuntimeException | Error exception) {
            if (!complete) {
                addSuppressedCleanupFailure(exception, destroyCreatedResources(null, createdProgram,
                    createdFramebuffer, createdTexture, createdAltTexture));
            }
            throw exception;
        }

        this.texture = createdTexture;
        this.altTexture = createdAltTexture;
        this.framebuffer = createdFramebuffer;
        this.program = createdProgram;
    }

    /**
     * Gets the smoothed center-depth texture ID for binding to shader-pack samplers.
     */
    public int getCenterDepthTexture() {
        if (destroyed) {
            throw new IllegalStateException("Cannot bind center depth after the center-depth sampler was destroyed");
        }
        return altTexture;
    }

    /**
     * Sets whether this sampler is used by any shader programs.
     */
    public void setUsage(boolean usage) {
        if (destroyed) {
            throw new IllegalStateException("Cannot update center-depth usage after the center-depth sampler was destroyed");
        }
        this.hasUsage |= usage;
    }

    /**
     * Returns whether any shader program uses the center depth sampler.
     */
    public boolean hasUsage() {
        if (destroyed) {
            throw new IllegalStateException("Cannot query center-depth usage after the center-depth sampler was destroyed");
        }
        return hasUsage;
    }

    /**
     * Updates the 1x1 smoothed center-depth texture from the current depth texture.
     */
    public void sampleCenterDepth() {
        if (destroyed) {
            throw new IllegalStateException("Cannot sample center depth after the center-depth sampler was destroyed");
        }
        if (hasFirstSample && !hasUsage) {
            return;
        }
        hasFirstSample = true;

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        boolean fullscreenQuadBegun = false;
        boolean programStateCleared = false;
        Throwable failure = null;
        try {
            framebuffer.bind();
            program.use();
            GL11.glViewport(0, 0, 1, 1);
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.colorMask(true, true, true, true);

            FullScreenQuadRenderer.INSTANCE.begin();
            fullscreenQuadBegun = true;
            FullScreenQuadRenderer.INSTANCE.renderQuad();
            try {
                FullScreenQuadRenderer.INSTANCE.end();
            } finally {
                fullscreenQuadBegun = false;
            }

            Program.unbind();
            programStateCleared = true;

            DepthCopyStrategy.fastest(DepthBufferFormat.DEPTH)
                .copy(framebuffer, texture, null, altTexture, 1, 1);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (fullscreenQuadBegun) {
                cleanupFailure = runCleanup(cleanupFailure, () -> FullScreenQuadRenderer.INSTANCE.end());
            }
            cleanupFailure = cleanupAfterSample(cleanupFailure, programStateCleared, previousActiveTexture,
                blendWasEnabled, alphaWasEnabled, previousColorMask);

            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private static Throwable cleanupAfterSample(
            Throwable failure,
            boolean programStateCleared,
            int previousActiveTexture,
            boolean blendWasEnabled,
            boolean alphaWasEnabled,
            ByteBuffer previousColorMask) {
        if (!programStateCleared) {
            failure = runCleanup(failure, Program::unbind);
        }
        failure = runCleanup(failure, CenterDepthSampler::bindMainFramebufferForPostprocess);
        failure = runCleanup(failure, () -> OculusRenderSystem.setActiveTextureUnit(previousActiveTexture));
        failure = runCleanup(failure, () -> restoreBlendAlphaState(blendWasEnabled, alphaWasEnabled));
        failure = runCleanup(failure, () -> restoreColorMask(previousColorMask));
        return failure;
    }

    private static void restoreBlendAlphaState(boolean blendWasEnabled, boolean alphaWasEnabled) {
        if (blendWasEnabled) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
        if (alphaWasEnabled) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
    }

    private static void restoreColorMask(ByteBuffer previousColorMask) {
        GlStateManager.colorMask(
            previousColorMask.get(0) != 0,
            previousColorMask.get(1) != 0,
            previousColorMask.get(2) != 0,
            previousColorMask.get(3) != 0);
    }

    private static void bindMainFramebufferForPostprocess() {
        Minecraft minecraft = Minecraft.getMinecraft();
        Framebuffer mainFramebuffer = minecraft == null ? null : minecraft.getFramebuffer();
        if (mainFramebuffer != null) {
            mainFramebuffer.bindFramebuffer(true);
            return;
        }

        OculusRenderSystem.restoreFramebufferBindings(0, 0, 0);
        if (minecraft != null) {
            GL11.glViewport(0, 0, Math.max(1, minecraft.displayWidth), Math.max(1, minecraft.displayHeight));
        }
    }

    private static void setupColorTexture(int texture, int internalFormat, int pixelFormat) {
        OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, internalFormat, 1, 1, 0, pixelFormat,
            GL11.GL_FLOAT, null);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static String prepareFragmentSource(String source) {
        if (usesGl3Output()) {
            return source.replace("VERSIONPLACEHOLDER", "150 compatibility");
        }

        return source.replace("#define IS_GL3", "")
            .replace("VERSIONPLACEHOLDER", "120");
    }

    private static int createTexture(String context) {
        int texture = GL11.glGenTextures();
        if (texture <= 0) {
            throw new IllegalStateException("Failed to create " + context);
        }
        return texture;
    }

    private static boolean usesGl3Output() {
        return OculusRenderSystem.supportsOpenGL32();
    }

    private static String readResource(String resourcePath) {
        try (InputStream input = CenterDepthSampler.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing center-depth shader resource " + resourcePath);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read center-depth shader resource " + resourcePath, exception);
        }
    }

    /**
     * Destroys OpenGL resources.
     */
    public void destroy() {
        if (destroyed) {
            return;
        }

        try {
            Throwable failure = null;
            failure = runCleanup(failure, program::destroy);
            failure = runCleanup(failure, framebuffer::destroy);
            failure = runCleanup(failure, () -> deleteTexture(texture));
            failure = runCleanup(failure, () -> deleteTexture(altTexture));
            rethrowCleanupFailure(failure);
        } finally {
            destroyed = true;
        }
    }

    private static void deleteTexture(int texture) {
        if (texture <= 0) {
            return;
        }

        Throwable failure = null;
        try {
            GL11.glDeleteTextures(texture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        } finally {
            try {
                TextureLifecycleTracker.onDeleteTexture(texture);
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }

        rethrowCleanupFailure(failure);
    }

    private static Throwable destroyCreatedResources(Throwable failure, Program program, GlFramebuffer framebuffer,
                                                     int texture, int altTexture) {
        if (program != null) {
            failure = runCleanup(failure, program::destroy);
        }
        if (framebuffer != null) {
            failure = runCleanup(failure, framebuffer::destroy);
        }
        failure = runCleanup(failure, () -> deleteTexture(texture));
        failure = runCleanup(failure, () -> deleteTexture(altTexture));
        return failure;
    }

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable addCleanupFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }
        if (exception != failure) {
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure) {
        if (primary != null && cleanupFailure != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private static void rethrowCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected center-depth cleanup failure", failure);
    }
}
