package net.oculus.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.PackShadowDirectives;
import net.oculus.vendored.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class ClearPassCreator {
    private ClearPassCreator() {
    }

    public static List<ClearPass> createClearPasses(RenderTargets renderTargets,
                                                    boolean fullClear,
                                                    PackRenderTargetDirectives renderTargetDirectives) {
        int maxDrawBuffers = Math.max(1, OculusRenderSystem.getMaxDrawBuffers());
        Map<ClearPassKey, List<Integer>> buffersByClearPass = new HashMap<>();

        renderTargetDirectives.getRenderTargetSettings().forEach((bufferIndex, settings) -> {
            if (!fullClear && !settings.shouldClear()) {
                return;
            }

            RenderTarget target = renderTargets.get(bufferIndex);
            if (target == null) {
                throw new IllegalStateException("Render target colortex" + bufferIndex + " is not configured");
            }

            Vector4f clearColor = settings.getClearColor().orElse(defaultClearColor(bufferIndex));
            ClearPassKey key = new ClearPassKey(clearColor, target.getWidth(), target.getHeight());
            buffersByClearPass.computeIfAbsent(key, ignored -> new ArrayList<>()).add(bufferIndex);
        });

        List<ClearPass> clearPasses = new ArrayList<>();
        try {
            buffersByClearPass.forEach((key, buffers) -> {
                int startIndex = 0;
                while (startIndex < buffers.size()) {
                    int count = Math.min(buffers.size() - startIndex, maxDrawBuffers);
                    int[] clearBuffers = new int[count];
                    for (int i = 0; i < count; i++) {
                        clearBuffers[i] = buffers.get(startIndex++);
                    }

                    addRenderTargetClearPass(clearPasses, renderTargets, key, true, clearBuffers);
                    addRenderTargetClearPass(clearPasses, renderTargets, key, false, clearBuffers);
                }
            });

            return clearPasses;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyClearPassFramebuffers(null, renderTargets, clearPasses));
            throw exception;
        }
    }

    public static List<ClearPass> createShadowClearPasses(ShadowMap shadowMap,
                                                          boolean fullClear,
                                                          PackShadowDirectives shadowDirectives) {
        int maxDrawBuffers = Math.max(1, OculusRenderSystem.getMaxDrawBuffers());
        Map<Vector4f, List<Integer>> buffersByClearPass = new HashMap<>();

        for (int i = 0; i < shadowDirectives.getColorSamplingSettings().size(); i++) {
            PackShadowDirectives.SamplingSettings settings = shadowDirectives.getColorSamplingSettings().get(i);
            if (!fullClear && !settings.shouldClear()) {
                continue;
            }

            buffersByClearPass.computeIfAbsent(settings.getClearColor(), ignored -> new ArrayList<>()).add(i);
        }

        List<ClearPass> clearPasses = new ArrayList<>();
        try {
            buffersByClearPass.forEach((clearColor, buffers) -> {
                int startIndex = 0;
                while (startIndex < buffers.size()) {
                    int count = Math.min(buffers.size() - startIndex, maxDrawBuffers);
                    int[] clearBuffers = new int[count];
                    for (int i = 0; i < count; i++) {
                        clearBuffers[i] = buffers.get(startIndex++);
                    }

                    addShadowClearPass(clearPasses, shadowMap, clearColor, true, clearBuffers);
                    addShadowClearPass(clearPasses, shadowMap, clearColor, false, clearBuffers);
                }
            });

            return clearPasses;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyShadowClearPassFramebuffers(null, shadowMap, clearPasses));
            throw exception;
        }
    }

    private static void addRenderTargetClearPass(List<ClearPass> clearPasses, RenderTargets renderTargets,
                                                 ClearPassKey key, boolean alt, int[] clearBuffers) {
        GlFramebuffer pendingFramebuffer = renderTargets.createClearFramebuffer(alt, clearBuffers);
        try {
            clearPasses.add(new ClearPass(
                key.color,
                key::getWidth,
                key::getHeight,
                pendingFramebuffer,
                GL11.GL_COLOR_BUFFER_BIT
            ));
            pendingFramebuffer = null;
        } catch (RuntimeException | Error exception) {
            if (pendingFramebuffer != null) {
                final GlFramebuffer framebuffer = pendingFramebuffer;
                addSuppressedCleanupFailure(exception,
                    runCleanup(null, () -> renderTargets.destroyFramebuffer(framebuffer)));
            }
            throw exception;
        }
    }

    private static void addShadowClearPass(List<ClearPass> clearPasses, ShadowMap shadowMap, Vector4f clearColor,
                                           boolean alt, int[] clearBuffers) {
        GlFramebuffer pendingFramebuffer = alt
            ? shadowMap.createFramebufferWritingToAlt(clearBuffers)
            : shadowMap.createFramebufferWritingToMain(clearBuffers);
        try {
            clearPasses.add(new ClearPass(
                clearColor,
                shadowMap::getResolution,
                shadowMap::getResolution,
                pendingFramebuffer,
                GL11.GL_COLOR_BUFFER_BIT
            ));
            pendingFramebuffer = null;
        } catch (RuntimeException | Error exception) {
            if (pendingFramebuffer != null) {
                final GlFramebuffer framebuffer = pendingFramebuffer;
                addSuppressedCleanupFailure(exception,
                    runCleanup(null, () -> shadowMap.destroyFramebuffer(framebuffer)));
            }
            throw exception;
        }
    }

    private static Throwable destroyClearPassFramebuffers(Throwable failure, RenderTargets renderTargets,
                                                         List<ClearPass> clearPasses) {
        for (ClearPass clearPass : clearPasses) {
            if (clearPass != null) {
                failure = runCleanup(failure, () -> renderTargets.destroyFramebuffer(clearPass.getFramebuffer()));
            }
        }
        return failure;
    }

    private static Throwable destroyShadowClearPassFramebuffers(Throwable failure, ShadowMap shadowMap,
                                                               List<ClearPass> clearPasses) {
        for (ClearPass clearPass : clearPasses) {
            if (clearPass != null) {
                failure = runCleanup(failure, () -> shadowMap.destroyFramebuffer(clearPass.getFramebuffer()));
            }
        }
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

    private static Vector4f defaultClearColor(int bufferIndex) {
        if (bufferIndex == 0) {
            return null;
        }
        if (bufferIndex == 1) {
            return new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
        return new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static final class ClearPassKey {
        private final Vector4f color;
        private final int width;
        private final int height;

        private ClearPassKey(Vector4f color, int width, int height) {
            this.color = color;
            this.width = width;
            this.height = height;
        }

        private int getWidth() {
            return width;
        }

        private int getHeight() {
            return height;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ClearPassKey)) {
                return false;
            }
            ClearPassKey other = (ClearPassKey) object;
            return width == other.width
                && height == other.height
                && colorsEqual(color, other.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                width,
                height,
                color == null ? 0 : Float.floatToIntBits(color.x()),
                color == null ? 0 : Float.floatToIntBits(color.y()),
                color == null ? 0 : Float.floatToIntBits(color.z()),
                color == null ? 0 : Float.floatToIntBits(color.w())
            );
        }

        private static boolean colorsEqual(Vector4f left, Vector4f right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            return Float.compare(left.x(), right.x()) == 0
                && Float.compare(left.y(), right.y()) == 0
                && Float.compare(left.z(), right.z()) == 0
                && Float.compare(left.w(), right.w()) == 0;
        }
    }
}
