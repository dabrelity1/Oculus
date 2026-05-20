package net.oculus.rendertarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.renderer.GlStateManager;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.texture.DepthBufferFormat;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.vendored.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Manages the render targets (colortex0-15) used by shader packs.
 * Each target has a main and alternate texture for ping-pong buffering.
 */
public class RenderTargets {
    private final RenderTarget[] targets;
    private final PackDirectives packDirectives;
    private int currentDepthTexture;
    private DepthBufferFormat currentDepthFormat;
    private int cachedDepthBufferVersion;
    private DepthCopyStrategy copyStrategy;
    private final DepthTexture noTranslucents;
    private final DepthTexture noHand;
    private final GlFramebuffer depthSourceFramebuffer;
    private final GlFramebuffer noTranslucentsDestFramebuffer;
    private final GlFramebuffer noHandDestFramebuffer;

    private final List<GlFramebuffer> ownedFramebuffers;

    private int cachedWidth;
    private int cachedHeight;
    private boolean fullClearRequired;
    private boolean translucentDepthDirty;
    private boolean handDepthDirty;
    private boolean destroyed;

    public RenderTargets(int width, int height, int depthTexture, int depthBufferVersion, DepthBufferFormat depthFormat,
                         Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargets,
                         PackDirectives packDirectives) {
        this.packDirectives = Objects.requireNonNull(packDirectives, "packDirectives");

        // Find the maximum target index to size the array
        int maxIndex = renderTargets.keySet().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(-1);

        targets = new RenderTarget[maxIndex + 1];

        requireValidDepthTexture(depthTexture, "Render target setup");
        this.currentDepthTexture = depthTexture;
        this.currentDepthFormat = depthFormat;
        this.cachedDepthBufferVersion = depthBufferVersion;
        this.copyStrategy = DepthCopyStrategy.fastest(currentDepthFormat);

        this.cachedWidth = width;
        this.cachedHeight = height;

        this.ownedFramebuffers = new ArrayList<>();
        this.fullClearRequired = true;

        DepthTexture createdNoTranslucents = null;
        DepthTexture createdNoHand = null;
        GlFramebuffer createdDepthSourceFramebuffer = null;
        GlFramebuffer createdNoTranslucentsDestFramebuffer = null;
        GlFramebuffer createdNoHandDestFramebuffer = null;
        try {
            renderTargets.forEach((index, settings) -> {
                Vector2i dimensions = this.packDirectives.getTextureScaleOverride(index, width, height);
                targets[index] = RenderTarget.builder()
                    .setDimensions(Math.max(1, dimensions.x()), Math.max(1, dimensions.y()))
                    .setInternalFormat(settings.getInternalFormat())
                    .setPixelFormat(settings.getInternalFormat().getPixelFormat())
                    .build();
            });

            createdDepthSourceFramebuffer = createGbufferFramebuffer(Collections.emptySet(), new int[] {0});
            createdNoTranslucents = new DepthTexture(width, height, currentDepthFormat);
            createdNoHand = new DepthTexture(width, height, currentDepthFormat);
            createdNoTranslucentsDestFramebuffer = createDepthCopyDestinationFramebuffer(createdNoTranslucents);
            createdNoHandDestFramebuffer = createDepthCopyDestinationFramebuffer(createdNoHand);
        } catch (RuntimeException | Error exception) {
            try {
                destroyPartialConstructorResources(createdNoTranslucents, createdNoHand);
            } catch (RuntimeException | Error cleanupException) {
                if (cleanupException != exception) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }

        this.depthSourceFramebuffer = createdDepthSourceFramebuffer;
        this.noTranslucents = createdNoTranslucents;
        this.noHand = createdNoHand;
        this.noTranslucentsDestFramebuffer = createdNoTranslucentsDestFramebuffer;
        this.noHandDestFramebuffer = createdNoHandDestFramebuffer;
        this.translucentDepthDirty = true;
        this.handDepthDirty = true;
    }

    private void destroyPartialConstructorResources(DepthTexture createdNoTranslucents, DepthTexture createdNoHand) {
        Throwable failure = null;
        for (GlFramebuffer owned : ownedFramebuffers) {
            failure = destroyFramebufferResource(failure, owned);
        }
        ownedFramebuffers.clear();

        for (RenderTarget target : targets) {
            failure = destroyRenderTarget(failure, target);
        }

        failure = destroyDepthTexture(failure, createdNoTranslucents);
        failure = destroyDepthTexture(failure, createdNoHand);
        rethrowCleanupFailure(failure);
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        try {
            Throwable failure = null;
            for (GlFramebuffer owned : ownedFramebuffers) {
                failure = destroyFramebufferResource(failure, owned);
            }

            for (RenderTarget target : targets) {
                failure = destroyRenderTarget(failure, target);
            }

            failure = destroyDepthTexture(failure, noTranslucents);
            failure = destroyDepthTexture(failure, noHand);
            rethrowCleanupFailure(failure);
        } finally {
            ownedFramebuffers.clear();
            Arrays.fill(targets, null);
            currentDepthTexture = 0;
            cachedDepthBufferVersion = 0;
            cachedWidth = 0;
            cachedHeight = 0;
            fullClearRequired = false;
            translucentDepthDirty = false;
            handDepthDirty = false;
            destroyed = true;
        }
    }

    public int getRenderTargetCount() {
        requireLive("read render-target count");
        return targets.length;
    }

    public RenderTarget get(int index) {
        requireLive("read render target");
        if (index < 0 || index >= targets.length) {
            return null;
        }
        return targets[index];
    }

    public int getCurrentDepthTexture() {
        requireLive("read current depth texture");
        return currentDepthTexture;
    }

    public int getDepthTexture() {
        requireLive("read depth texture");
        return currentDepthTexture;
    }

    public DepthBufferFormat getCurrentDepthFormat() {
        requireLive("read current depth format");
        return currentDepthFormat;
    }

    public DepthTexture getDepthTextureNoTranslucents() {
        requireLive("read no-translucents depth texture");
        return noTranslucents;
    }

    public DepthTexture getDepthTextureNoHand() {
        requireLive("read no-hand depth texture");
        return noHand;
    }

    public int getWidth() {
        requireLive("read render-target width");
        return cachedWidth;
    }

    public int getHeight() {
        requireLive("read render-target height");
        return cachedHeight;
    }

    public int getCurrentWidth() {
        requireLive("read current render-target width");
        return cachedWidth;
    }

    public int getCurrentHeight() {
        requireLive("read current render-target height");
        return cachedHeight;
    }

    public GlFramebuffer createFramebufferWritingToMain(int[] drawBuffers) {
        requireLive("create main render-target framebuffer");
        return createFullFramebuffer(false, drawBuffers);
    }

    public GlFramebuffer createFramebufferWritingToAlt(int[] drawBuffers) {
        requireLive("create alternate render-target framebuffer");
        return createFullFramebuffer(true, drawBuffers);
    }

    public GlFramebuffer createGbufferFramebuffer(Set<Integer> stageWritesToAlt, int[] drawBuffers) {
        requireLive("create gbuffer framebuffer");
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        return createColorFramebufferWithDepth(invert(stageWritesToAlt, drawBuffers), drawBuffers);
    }

    public GlFramebuffer createColorFramebufferWithDepth(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
        requireLive("create color framebuffer with depth");
        return createColorFramebufferWithDepth((Set<Integer>) stageWritesToMain, drawBuffers);
    }

    public GlFramebuffer createColorFramebufferWithDepth(Set<Integer> stageWritesToMain, int[] drawBuffers) {
        requireLive("create color framebuffer with depth");
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);
        try {
            framebuffer.addDepthAttachment(currentDepthTexture);
            verifyComplete(framebuffer, "Render target framebuffer with depth for draw buffers "
                + Arrays.toString(drawBuffers));
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyFramebuffer(null, framebuffer));
            throw exception;
        }
    }

    public GlFramebuffer createClearFramebuffer(boolean alt, int[] clearBuffers) {
        requireLive("create clear framebuffer");
        Set<Integer> stageWritesToMain = Collections.emptySet();
        if (!alt) {
            stageWritesToMain = invert(Collections.emptySet(), clearBuffers);
        }
        return createColorFramebuffer(stageWritesToMain, clearBuffers);
    }

    private GlFramebuffer createFullFramebuffer(boolean clearsAlt, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        Set<Integer> stageWritesToMain = Collections.emptySet();
        if (!clearsAlt) {
            stageWritesToMain = invert(Collections.emptySet(), drawBuffers);
        }
        return createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);
    }

    private GlFramebuffer createEmptyFramebuffer() {
        RenderTarget target = get(0);
        if (target == null) {
            throw new IllegalStateException("Cannot create empty framebuffer without colortex0");
        }

        GlFramebuffer framebuffer = createOwnedFramebuffer();
        try {
            framebuffer.addDepthAttachment(currentDepthTexture);
            framebuffer.addColorAttachment(0, target.getMainTexture());
            framebuffer.noDrawBuffers();
            verifyComplete(framebuffer, "Render target empty framebuffer");
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyFramebuffer(null, framebuffer));
            throw exception;
        }
    }

    private GlFramebuffer createOwnedFramebuffer() {
        GlFramebuffer framebuffer = new GlFramebuffer();
        try {
            ownedFramebuffers.add(framebuffer);
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyFramebufferResource(null, framebuffer));
            throw exception;
        }
    }

    /**
     * Creates a framebuffer whose attachments write to main textures for buffers present in
     * {@code stageWritesToMain}, and alternate textures for the remaining draw buffers.
     */
    public GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
        requireLive("create color framebuffer");
        return createColorFramebuffer((Set<Integer>) stageWritesToMain, drawBuffers);
    }

    public GlFramebuffer createColorFramebuffer(Set<Integer> stageWritesToMain, int[] drawBuffers) {
        requireLive("create color framebuffer");
        if (drawBuffers.length == 0) {
            throw new IllegalArgumentException("Framebuffer must have at least one color buffer");
        }

        GlFramebuffer framebuffer = createOwnedFramebuffer();

        try {
            for (int i = 0; i < drawBuffers.length; i++) {
                int bufferIndex = drawBuffers[i];
                RenderTarget target = get(bufferIndex);
                if (target == null) {
                    throw new IllegalStateException("Render target colortex" + bufferIndex + " is not configured");
                }

                int texture = stageWritesToMain.contains(bufferIndex) ? target.getMainTexture() : target.getAltTexture();
                framebuffer.addColorAttachment(i, texture);
            }

            framebuffer.drawBuffers(logicalDrawBuffers(drawBuffers.length));
            framebuffer.readBuffer(0);
            verifyComplete(framebuffer, "Render target color framebuffer for draw buffers "
                + Arrays.toString(drawBuffers));

            return framebuffer;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyFramebuffer(null, framebuffer));
            throw exception;
        }
    }

    private static void verifyComplete(GlFramebuffer framebuffer, String context) {
        if (!framebuffer.isComplete()) {
            throw new IllegalStateException(context + " is incomplete");
        }
    }

    private Set<Integer> invert(Set<Integer> base, int[] relevant) {
        Set<Integer> inverted = new HashSet<>();
        for (int index : relevant) {
            if (!base.contains(index)) {
                inverted.add(index);
            }
        }
        return inverted;
    }

    private static int[] logicalDrawBuffers(int count) {
        int[] buffers = new int[count];
        for (int i = 0; i < count; i++) {
            buffers[i] = i;
        }
        return buffers;
    }

    public void destroyFramebuffer(GlFramebuffer framebuffer) {
        rethrowCleanupFailure(destroyFramebuffer(null, framebuffer));
    }

    private Throwable destroyFramebuffer(Throwable failure, GlFramebuffer framebuffer) {
        if (framebuffer == null) {
            return failure;
        }
        try {
            framebuffer.destroy();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        } finally {
            ownedFramebuffers.remove(framebuffer);
        }
        return failure;
    }

    /**
     * Resizes all render targets and reattaches a recreated depth texture without rebuilding
     * every framebuffer/program object that can safely retain ownership.
     */
    public boolean resizeIfNeeded(int newDepthTextureId, int newDepthBufferVersion, int newWidth, int newHeight,
                                  DepthBufferFormat newDepthFormat) {
        requireLive("resize render targets");
        int safeWidth = Math.max(1, newWidth);
        int safeHeight = Math.max(1, newHeight);
        DepthBufferFormat safeDepthFormat = newDepthFormat == null ? DepthBufferFormat.DEPTH : newDepthFormat;

        boolean recreateDepth = currentDepthTexture != newDepthTextureId
            || cachedDepthBufferVersion != newDepthBufferVersion;
        boolean sizeChanged = safeWidth != cachedWidth || safeHeight != cachedHeight;
        boolean depthFormatChanged = safeDepthFormat != currentDepthFormat;
        DepthCopyStrategy replacementCopyStrategy = depthFormatChanged
            ? DepthCopyStrategy.fastest(safeDepthFormat)
            : copyStrategy;
        int previousDepthTexture = currentDepthTexture;
        int previousDepthBufferVersion = cachedDepthBufferVersion;
        DepthBufferFormat previousDepthFormat = currentDepthFormat;
        DepthCopyStrategy previousCopyStrategy = copyStrategy;
        int previousWidth = cachedWidth;
        int previousHeight = cachedHeight;
        boolean previousFullClearRequired = fullClearRequired;
        boolean previousTranslucentDepthDirty = translucentDepthDirty;
        boolean previousHandDepthDirty = handDepthDirty;

        try {
            if (recreateDepth || depthFormatChanged) {
                requireValidDepthTexture(newDepthTextureId, "Render target depth reattachment");
                reattachOwnedDepthTexture(newDepthTextureId);
            }

            if (depthFormatChanged || sizeChanged) {
                noTranslucents.resize(safeWidth, safeHeight, safeDepthFormat);
                noHand.resize(safeWidth, safeHeight, safeDepthFormat);
                if (depthFormatChanged) {
                    noTranslucentsDestFramebuffer.addDepthAttachment(noTranslucents.getTextureId());
                    verifyComplete(noTranslucentsDestFramebuffer,
                        "No-translucents depth-copy framebuffer after depth format change");
                    noHandDestFramebuffer.addDepthAttachment(noHand.getTextureId());
                    verifyComplete(noHandDestFramebuffer,
                        "No-hand depth-copy framebuffer after depth format change");
                }
            }

            if (sizeChanged) {
                resizeColorTargets(safeWidth, safeHeight);
            }
        } catch (RuntimeException | Error exception) {
            Throwable failure = null;
            if (recreateDepth || depthFormatChanged) {
                failure = runCleanup(failure, () -> reattachOwnedDepthTexture(previousDepthTexture));
            }
            if (depthFormatChanged || sizeChanged) {
                failure = runCleanup(failure,
                    () -> noTranslucents.resize(previousWidth, previousHeight, previousDepthFormat));
                failure = runCleanup(failure,
                    () -> noHand.resize(previousWidth, previousHeight, previousDepthFormat));
                if (depthFormatChanged) {
                    failure = runCleanup(failure,
                        () -> {
                            noTranslucentsDestFramebuffer.addDepthAttachment(noTranslucents.getTextureId());
                            verifyComplete(noTranslucentsDestFramebuffer,
                                "Rolled-back no-translucents depth-copy framebuffer");
                        });
                    failure = runCleanup(failure,
                        () -> {
                            noHandDestFramebuffer.addDepthAttachment(noHand.getTextureId());
                            verifyComplete(noHandDestFramebuffer,
                                "Rolled-back no-hand depth-copy framebuffer");
                        });
                }
            }
            if (sizeChanged) {
                failure = rollbackColorTargets(failure, previousWidth, previousHeight);
            }

            currentDepthTexture = previousDepthTexture;
            cachedDepthBufferVersion = previousDepthBufferVersion;
            currentDepthFormat = previousDepthFormat;
            copyStrategy = previousCopyStrategy;
            cachedWidth = previousWidth;
            cachedHeight = previousHeight;
            fullClearRequired = previousFullClearRequired;
            translucentDepthDirty = previousTranslucentDepthDirty;
            handDepthDirty = previousHandDepthDirty;
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        if (recreateDepth) {
            currentDepthTexture = newDepthTextureId;
            cachedDepthBufferVersion = newDepthBufferVersion;
        }

        if (depthFormatChanged) {
            currentDepthFormat = safeDepthFormat;
            copyStrategy = replacementCopyStrategy;
        }

        if (depthFormatChanged || sizeChanged) {
            translucentDepthDirty = true;
            handDepthDirty = true;
        }

        if (sizeChanged) {
            cachedWidth = safeWidth;
            cachedHeight = safeHeight;
            fullClearRequired = true;
        }

        return sizeChanged;
    }

    private void resizeColorTargets(int width, int height) {
        for (int i = 0; i < targets.length; i++) {
            RenderTarget target = targets[i];
            if (target != null) {
                Vector2i dimensions = packDirectives.getTextureScaleOverride(i, width, height);
                target.resize(Math.max(1, dimensions.x()), Math.max(1, dimensions.y()));
            }
        }
    }

    private Throwable rollbackColorTargets(Throwable failure, int width, int height) {
        for (int i = 0; i < targets.length; i++) {
            RenderTarget target = targets[i];
            if (target != null) {
                Vector2i dimensions = packDirectives.getTextureScaleOverride(i, width, height);
                final int targetWidth = Math.max(1, dimensions.x());
                final int targetHeight = Math.max(1, dimensions.y());
                failure = runCleanup(failure, () -> target.resize(targetWidth, targetHeight));
            }
        }
        return failure;
    }

    private void reattachOwnedDepthTexture(int replacementDepthTexture) {
        if (replacementDepthTexture <= 0) {
            return;
        }

        for (GlFramebuffer framebuffer : ownedFramebuffers) {
            if (framebuffer == noTranslucentsDestFramebuffer || framebuffer == noHandDestFramebuffer) {
                continue;
            }
            if (framebuffer.hasDepthAttachment()) {
                framebuffer.addDepthAttachment(replacementDepthTexture);
            }
        }
    }

    /**
     * Resizes all render targets.
     */
    public void resize(int width, int height) {
        requireLive("resize render targets");
        resizeIfNeeded(currentDepthTexture, cachedDepthBufferVersion, width, height, currentDepthFormat);
    }

    public void copyPreTranslucentDepth() {
        requireLive("copy pre-translucent depth");
        requireValidDepthTexture(currentDepthTexture, "Pre-translucent depth copy");
        boolean allocate = translucentDepthDirty;
        copyDepth(noTranslucents, noTranslucentsDestFramebuffer, allocate);
        translucentDepthDirty = false;
    }

    public void copyPreHandDepth() {
        requireLive("copy pre-hand depth");
        requireValidDepthTexture(currentDepthTexture, "Pre-hand depth copy");
        boolean allocate = handDepthDirty;
        copyDepth(noHand, noHandDestFramebuffer, allocate);
        handDepthDirty = false;
    }

    private static void requireValidDepthTexture(int depthTexture, String operation) {
        if (depthTexture <= 0) {
            throw new IllegalStateException(operation + " requires a valid Minecraft depth texture");
        }
    }

    private void copyDepth(DepthTexture destination, GlFramebuffer destinationFramebuffer, boolean allocate) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        int previousTexture = 0;
        boolean restoreTexture = false;
        Throwable failure = null;
        try {
            OculusRenderSystem.restoreDefaultActiveTexture();
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            restoreTexture = true;
            if (allocate) {
                depthSourceFramebuffer.bindAsReadBuffer();
                GlStateManager.bindTexture(destination.getTextureId());
                OculusRenderSystem.copyTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    currentDepthFormat.getGlInternalFormat(),
                    0,
                    0,
                    cachedWidth,
                    cachedHeight,
                    0
                );
            } else {
                copyStrategy.copy(depthSourceFramebuffer, currentDepthTexture, destinationFramebuffer,
                    destination.getTextureId(), cachedWidth, cachedHeight);
            }
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (restoreTexture) {
                cleanupFailure = restoreDefaultTextureBinding(cleanupFailure, previousTexture);
            }
            cleanupFailure = restoreFramebufferBindings(cleanupFailure, previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
            cleanupFailure = restoreActiveTexture(cleanupFailure, previousActiveTexture);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private GlFramebuffer createDepthCopyDestinationFramebuffer(DepthTexture depthTexture) {
        GlFramebuffer framebuffer = createGbufferFramebuffer(Collections.emptySet(), new int[] {0});
        try {
            framebuffer.addDepthAttachment(depthTexture.getTextureId());
            verifyComplete(framebuffer, "Depth-copy destination framebuffer");
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyFramebuffer(null, framebuffer));
            throw exception;
        }
    }

    public boolean isFullClearRequired() {
        requireLive("read full-clear state");
        return fullClearRequired;
    }

    public void onFullClear() {
        requireLive("consume full-clear state");
        fullClearRequired = false;
    }

    private void requireLive(String operation) {
        if (destroyed) {
            throw new IllegalStateException("Cannot " + operation + " after render targets were destroyed");
        }
    }

    private static Throwable destroyFramebufferResource(Throwable failure, GlFramebuffer framebuffer) {
        if (framebuffer == null) {
            return failure;
        }
        try {
            framebuffer.destroy();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable destroyRenderTarget(Throwable failure, RenderTarget target) {
        if (target == null) {
            return failure;
        }
        try {
            target.destroy();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable destroyDepthTexture(Throwable failure, DepthTexture texture) {
        if (texture == null) {
            return failure;
        }
        try {
            texture.destroy();
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

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable restoreDefaultTextureBinding(Throwable failure, int texture) {
        try {
            OculusRenderSystem.restoreDefaultActiveTexture();
            GlStateManager.bindTexture(texture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable restoreFramebufferBindings(Throwable failure, int previousFramebuffer,
                                                       int previousReadFramebuffer,
                                                       int previousDrawFramebuffer) {
        try {
            OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,
                previousDrawFramebuffer);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable restoreActiveTexture(Throwable failure, int previousActiveTexture) {
        try {
            OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
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
