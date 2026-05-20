package net.oculus.colorspace;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.TextureBinding;
import net.oculus.postprocess.FullScreenQuadRenderer;
import net.oculus.texture.TextureLifecycleTracker;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * 1.12.2 fragment-shader fallback for the Oculus color-space converter. It
 * renders the converted screen color into a temporary RGBA8 texture and copies
 * that texture back into Minecraft's main framebuffer color texture.
 */
public final class ColorSpaceFragmentConverter implements ColorSpaceConverter {
    private int width;
    private int height;
    private ColorSpace colorSpace = ColorSpace.SRGB;
    private Program program;
    private GlFramebuffer framebuffer;
    private int swapTexture;
    private int targetTexture;

    public ColorSpaceFragmentConverter(int width, int height, ColorSpace colorSpace) {
        rebuildProgram(width, height, colorSpace);
    }

    @Override
    public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
        destroyResources();

        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.colorSpace = colorSpace == null ? ColorSpace.SRGB : colorSpace;

        ProgramBuilder builder = ProgramBuilder.beginExplicit(
            "colorSpaceFragment",
            ColorSpaceShaderSource.createFragmentVertexSource(),
            null,
            ColorSpaceShaderSource.createFragmentSource(this.colorSpace)
        );
        builder.overrideSamplerBinding("readImage", TextureBinding.texture2D(() -> targetTexture));
        try {
            this.program = builder.build();
            this.swapTexture = createSwapTexture(this.width, this.height);
            this.framebuffer = new GlFramebuffer();
            this.framebuffer.addColorAttachment(0, this.swapTexture);
            this.framebuffer.drawBuffers(new int[] {0});
            this.framebuffer.readBuffer(0);
            if (!this.framebuffer.isComplete()) {
                throw new IllegalStateException("Color-space converter framebuffer is incomplete");
            }
        } catch (RuntimeException | Error exception) {
            try {
                destroyResources();
            } catch (RuntimeException | Error cleanupException) {
                addSuppressedCleanupFailure(exception, cleanupException);
            }
            throw exception;
        }
    }

    @Override
    public void process(int targetTexture) {
        if (this.colorSpace == ColorSpace.SRGB || this.program == null || this.framebuffer == null || targetTexture <= 0) {
            return;
        }

        SavedState state = SavedState.capture();
        this.targetTexture = targetTexture;

        Throwable failure = null;
        try {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();

            this.framebuffer.bind();
            GL11.glViewport(0, 0, this.width, this.height);
            this.program.use();
            FullScreenQuadRenderer.INSTANCE.begin();
            try {
                FullScreenQuadRenderer.INSTANCE.renderQuad();
            } finally {
                FullScreenQuadRenderer.end();
            }
            Program.unbind();

            this.framebuffer.bindAsReadBuffer();
            copyBackToTargetTexture(targetTexture, this.width, this.height);
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            this.targetTexture = 0;
            cleanupFailure = runCleanup(cleanupFailure, Program::unbind);
            cleanupFailure = runCleanup(cleanupFailure, state::restore);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    @Override
    public void destroy() {
        destroyResources();
    }

    private void destroyResources() {
        Program oldProgram = program;
        GlFramebuffer oldFramebuffer = framebuffer;
        int oldSwapTexture = swapTexture;
        program = null;
        framebuffer = null;
        swapTexture = 0;
        targetTexture = 0;

        Throwable failure = null;
        if (oldProgram != null) {
            failure = runCleanup(failure, oldProgram::destroy);
        }
        if (oldFramebuffer != null) {
            failure = runCleanup(failure, oldFramebuffer::destroy);
        }
        if (oldSwapTexture > 0) {
            failure = runCleanup(failure, () -> deleteTexture(oldSwapTexture));
        }
        rethrowCleanupFailure(failure);
    }

    private static void copyBackToTargetTexture(int targetTexture, int width, int height) {
        OculusRenderSystem.copyTexSubImage2D(
            targetTexture,
            GL11.GL_TEXTURE_2D,
            0,
            0,
            0,
            0,
            0,
            width,
            height);
    }

    private static int createSwapTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        if (texture <= 0) {
            throw new IllegalStateException("Failed to allocate color-space swap texture");
        }

        boolean initialized = false;
        Throwable setupFailure = null;
        try {
            final int textureId = texture;
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                OculusRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                OculusRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                OculusRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texImage2D(textureId, GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            });
            initialized = true;
        } catch (RuntimeException | Error exception) {
            setupFailure = exception;
            throw exception;
        } finally {
            if (!initialized) {
                try {
                    deleteTexture(texture);
                } catch (RuntimeException | Error cleanupException) {
                    if (setupFailure != null) {
                        addSuppressedCleanupFailure(setupFailure, cleanupException);
                    } else {
                        throw cleanupException;
                    }
                }
            }
        }
        return texture;
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

    private static final class SavedState {
        private final IntBuffer viewport;
        private final int framebuffer;
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int activeTexture;
        private final boolean depthEnabled;
        private final boolean depthMask;
        private final boolean blendEnabled;
        private final boolean alphaEnabled;

        private SavedState(IntBuffer viewport, int framebuffer, int readFramebuffer, int drawFramebuffer,
                           int activeTexture,
                           boolean depthEnabled, boolean depthMask, boolean blendEnabled, boolean alphaEnabled) {
            this.viewport = viewport;
            this.framebuffer = framebuffer;
            this.readFramebuffer = readFramebuffer;
            this.drawFramebuffer = drawFramebuffer;
            this.activeTexture = activeTexture;
            this.depthEnabled = depthEnabled;
            this.depthMask = depthMask;
            this.blendEnabled = blendEnabled;
            this.alphaEnabled = alphaEnabled;
        }

        private static SavedState capture() {
            IntBuffer viewport = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            return new SavedState(
                viewport,
                OculusRenderSystem.getFramebufferBinding(),
                OculusRenderSystem.getReadFramebufferBinding(),
                OculusRenderSystem.getDrawFramebufferBinding(),
                GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
            );
        }

        private void restore() {
            Throwable failure = null;
            failure = runCleanup(failure,
                () -> OculusRenderSystem.restoreFramebufferBindings(framebuffer, readFramebuffer, drawFramebuffer));
            failure = runCleanup(failure,
                () -> GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3)));
            failure = runCleanup(failure, () -> {
                if (depthEnabled) {
                    GlStateManager.enableDepth();
                } else {
                    GlStateManager.disableDepth();
                }
                GlStateManager.depthMask(depthMask);
            });
            failure = runCleanup(failure, () -> {
                if (blendEnabled) {
                    GlStateManager.enableBlend();
                } else {
                    GlStateManager.disableBlend();
                }
            });
            failure = runCleanup(failure, () -> {
                if (alphaEnabled) {
                    GlStateManager.enableAlpha();
                } else {
                    GlStateManager.disableAlpha();
                }
            });
            failure = runCleanup(failure, () -> GL20.glUseProgram(0));
            failure = runCleanup(failure, () -> OculusRenderSystem.setActiveTextureUnit(activeTexture));
            rethrowCleanupFailure(failure);
        }
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
        throw new RuntimeException(failure);
    }
}
