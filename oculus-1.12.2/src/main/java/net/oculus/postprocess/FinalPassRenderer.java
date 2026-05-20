package net.oculus.postprocess;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.ComputeProgram;
import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.ProgramImages;
import net.oculus.gl.program.ProgramSamplers;
import net.oculus.gl.program.ProgramUniforms;
import net.oculus.gl.program.SamplerOverrideMap;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.sampler.SamplerLimits;
import net.oculus.gl.shader.ShaderType;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.pipeline.texture.CustomImageManager;
import net.oculus.pipeline.texture.CustomTextureManager;
import net.oculus.rendertarget.MinecraftFramebufferExt;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.samplers.IrisImages;
import net.oculus.samplers.IrisSamplers;
import net.oculus.shader.ShaderSourcePreparer;
import net.oculus.shaderpack.ComputeSource;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.shaderpack.StringPair;
import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.custom.CustomUniformExpressionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL42;

/**
 * Renders the final shaderpack pass to the default framebuffer after composite stages.
 */
public class FinalPassRenderer {
    private static final Logger LOGGER = LogManager.getLogger(FinalPassRenderer.class);
    private static final int COMPUTE_BARRIER = GL42.GL_TEXTURE_FETCH_BARRIER_BIT
        | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;

    private final RenderTargets renderTargets;
    private final PackDirectives packDirectives;
    private final Supplier<ShadowMap> shadowMapSupplier;
    private final Program program;
    private final ComputeProgram[] computes;
    private final IntSupplier noiseTexture;
    private final FrameUpdateNotifier updateNotifier;
    private final CenterDepthSampler centerDepthSampler;
    private final BufferFlipper bufferFlipper;
    private final CustomTextureManager customTextureManager;
    private final CustomImageManager customImageManager;
    private final CustomUniformExpressionManager customUniforms;
    private final String packName;
    private final List<StringPair> environmentDefines;
    private final ImmutableSet<Integer> stageReadsFromAlt;
    private final ImmutableSet<Integer> flippedAtLeastOnce;
    private final ImmutableSet<Integer> mipmappedBuffers;
    private final List<SwapPass> swapPasses;
    private final GlFramebuffer baseline;
    private final GlFramebuffer colorHolder;
    private int lastColorTextureId;
    private int lastColorTextureVersion;
    private boolean destroyed = false;

    public FinalPassRenderer(PackDirectives packDirectives,
                             ProgramSource source,
                             ComputeSource[] computeSources,
                             RenderTargets renderTargets,
                             Supplier<ShadowMap> shadowMapSupplier,
                             IntSupplier noiseTexture,
                             FrameUpdateNotifier updateNotifier,
                             CenterDepthSampler centerDepthSampler,
                             BufferFlipper bufferFlipper,
                             Set<Integer> flippedBuffers,
                             Set<Integer> flippedAtLeastOnce,
                             CustomTextureManager customTextureManager,
                             CustomImageManager customImageManager,
                             String packName,
                             List<StringPair> environmentDefines,
                             CustomUniformExpressionManager customUniforms) {
        this.renderTargets = renderTargets;
        this.packDirectives = packDirectives;
        this.shadowMapSupplier = shadowMapSupplier;
        this.noiseTexture = noiseTexture;
        this.updateNotifier = updateNotifier;
        this.centerDepthSampler = centerDepthSampler;
        this.bufferFlipper = bufferFlipper;
        this.customTextureManager = customTextureManager;
        this.customImageManager = customImageManager;
        this.customUniforms = customUniforms == null ? CustomUniformExpressionManager.empty() : customUniforms;
        this.packName = packName == null ? "" : packName;
        this.environmentDefines = environmentDefines == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(environmentDefines);
        ImmutableSet<Integer> createdStageReadsFromAlt = ImmutableSet.copyOf(flippedBuffers);
        this.stageReadsFromAlt = createdStageReadsFromAlt;
        ImmutableSet<Integer> createdFlippedAtLeastOnce = flippedAtLeastOnce == null
            ? ImmutableSet.of()
            : ImmutableSet.copyOf(flippedAtLeastOnce);
        this.flippedAtLeastOnce = createdFlippedAtLeastOnce;

        List<SwapPass> createdSwapPasses = Collections.emptyList();
        GlFramebuffer createdBaseline = null;
        GlFramebuffer createdColorHolder = null;
        Program createdProgram = null;
        ComputeProgram[] createdComputes = new ComputeProgram[0];
        ImmutableSet<Integer> createdMipmappedBuffers = ImmutableSet.of();
        boolean complete = false;
        try {
            if (source == null || !source.isValid()) {
                LOGGER.info("No final pass source provided, using passthrough");
            } else {
                createdMipmappedBuffers = ImmutableSet.copyOf(source.getDirectives().getMipmappedBuffers());

                String vertexSource = source.getVertexSource().orElse(null);
                String fragmentSource = source.getFragmentSource().orElse(null);
                String geometrySource = source.getGeometrySource().orElse(null);

                if (vertexSource == null || fragmentSource == null) {
                    throw new IllegalStateException("Final pass " + source.getName()
                        + " is missing vertex or fragment source");
                }

                ShaderSourcePreparer.PreparedProgram prepared = ShaderSourcePreparer.prepareProgram(
                    packName,
                    source.getName(),
                    vertexSource,
                    geometrySource,
                    fragmentSource,
                    environmentDefines);
                vertexSource = prepared.getVertexSource();
                fragmentSource = prepared.getFragmentSource();
                geometrySource = prepared.getGeometrySource();

                ProgramBuilder builder = ProgramBuilder.begin(
                    source.getName(), vertexSource, geometrySource, fragmentSource,
                    SamplerOverrideMap.empty(), customUniforms, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS, null,
                    updateNotifier, packDirectives);
                overrideRenderTargetSamplerBindings(builder, createdStageReadsFromAlt);
                bindNoiseSampler(builder);
                IrisSamplers.addCompositeSamplerBindings(builder, renderTargets);
                bindShadowTargetSamplerBindings(builder, source.getName());
                bindCenterDepthSmooth(builder);
                applyCustomBindings(source.getName(), builder, createdFlippedAtLeastOnce);
                createdProgram = builder.build();
                createdComputes = createComputes(computeSources, createdStageReadsFromAlt);
                LOGGER.info("Final pass shader compiled successfully: {}", source.getName());
            }

            createdBaseline = renderTargets.createGbufferFramebuffer(createdStageReadsFromAlt, new int[] {0});
            createdColorHolder = new GlFramebuffer();
            this.lastColorTextureId = 0;
            this.lastColorTextureVersion = -1;
            createdSwapPasses = createSwapPasses(packDirectives, createdStageReadsFromAlt);
            complete = true;
        } catch (RuntimeException | Error exception) {
            if (!complete) {
                Throwable failure = null;
                failure = destroyProgram(failure, createdProgram);
                failure = destroyComputePrograms(failure, createdComputes);
                failure = destroyFramebuffer(failure, createdBaseline);
                failure = destroyGlFramebuffer(failure, createdColorHolder);
                failure = destroySwapPasses(failure, createdSwapPasses);
                addSuppressedCleanupFailure(exception, failure);
            }
            throw exception;
        }

        this.swapPasses = createdSwapPasses;
        this.baseline = createdBaseline;
        this.colorHolder = createdColorHolder;
        this.program = createdProgram;
        this.computes = createdComputes;
        this.mipmappedBuffers = createdMipmappedBuffers;
        try {
            unbindReadFramebufferAfterConstruction();
        } catch (RuntimeException | Error exception) {
            Throwable failure = null;
            failure = destroyProgram(failure, createdProgram);
            failure = destroyComputePrograms(failure, createdComputes);
            failure = destroyFramebuffer(failure, createdBaseline);
            failure = destroyGlFramebuffer(failure, createdColorHolder);
            failure = destroySwapPasses(failure, createdSwapPasses);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }
    }

    private void bindCenterDepthSmooth(ProgramBuilder builder) {
        if (centerDepthSampler == null) {
            return;
        }

        centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture,
            "iris_centerDepthSmooth"));
    }

    private static void unbindReadFramebufferAfterConstruction() {
        OculusRenderSystem.bindReadFramebuffer(0);
    }

    public void render() {
        if (destroyed) {
            throw new IllegalStateException("Cannot render a destroyed final pass renderer");
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        Framebuffer mainFramebuffer = requireMainFramebuffer(minecraft);

        int viewportWidth = Math.max(1, mainFramebuffer.framebufferWidth);
        int viewportHeight = Math.max(1, mainFramebuffer.framebufferHeight);

        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean fogWasEnabled = GL11.glIsEnabled(GL11.GL_FOG);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);

        boolean fullscreenQuadBegun = false;
        Throwable failure = null;
        try {
            GlStateManager.depthMask(false);
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.disableFog();
            GlStateManager.colorMask(true, true, true, true);

            ensureColorHolderAttachment(mainFramebuffer);
            if (program != null) {
                colorHolder.bind();
                FullScreenQuadRenderer.INSTANCE.begin();
                fullscreenQuadBegun = true;

                dispatchComputes(viewportWidth, viewportHeight);
                OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);

                if (!mipmappedBuffers.isEmpty()) {
                    OculusRenderSystem.restoreDefaultActiveTexture();
                    for (int index : mipmappedBuffers) {
                        setupMipmapping(renderTargets.get(index), index, stageReadsFromAlt.contains(index));
                    }
                }

                program.use();
                FullScreenQuadRenderer.INSTANCE.renderQuad();
                try {
                    FullScreenQuadRenderer.INSTANCE.end();
                } finally {
                    fullscreenQuadBegun = false;
                }
            } else {
                copyBaselineToMain(mainFramebuffer, viewportWidth, viewportHeight);
            }

            OculusRenderSystem.restoreDefaultActiveTexture();
            resetRenderTargetMipmaps();
            runSwapPasses();
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (fullscreenQuadBegun) {
                try {
                    FullScreenQuadRenderer.INSTANCE.end();
                } catch (RuntimeException | Error exception) {
                    cleanupFailure = addCleanupFailure(cleanupFailure, exception);
                }
            }
            cleanupFailure = restoreAfterRender(cleanupFailure, mainFramebuffer, previousDepthMask, blendWasEnabled,
                alphaWasEnabled, fogWasEnabled, previousActiveTexture, previousColorMask);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private Throwable restoreAfterRender(
            Throwable failure,
            Framebuffer mainFramebuffer,
            boolean previousDepthMask,
            boolean blendWasEnabled,
            boolean alphaWasEnabled,
            boolean fogWasEnabled,
            int previousActiveTexture,
            ByteBuffer previousColorMask) {
        failure = runCleanup(failure, () -> mainFramebuffer.bindFramebuffer(true));
        failure = runCleanup(failure, ProgramUniforms::clearActiveUniforms);
        failure = runCleanup(failure, ProgramSamplers::clearActiveSamplers);
        failure = runCleanup(failure, ProgramImages::clearActiveImages);
        failure = runCleanup(failure, () -> GL20.glUseProgram(0));
        failure = runCleanup(failure, FinalPassRenderer::unbindAllSamplerTextures);
        failure = runCleanup(failure, () -> OculusRenderSystem.setActiveTextureUnit(previousActiveTexture));
        failure = runCleanup(failure, () -> GlStateManager.depthMask(previousDepthMask));
        failure = runCleanup(failure, () -> restoreBlendState(blendWasEnabled));
        failure = runCleanup(failure, () -> restoreAlphaState(alphaWasEnabled));
        failure = runCleanup(failure, () -> restoreFogState(fogWasEnabled));
        failure = runCleanup(failure, () -> restoreColorMask(previousColorMask));
        return failure;
    }

    private static void restoreBlendState(boolean blendWasEnabled) {
        if (blendWasEnabled) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
    }

    private static void restoreAlphaState(boolean alphaWasEnabled) {
        if (alphaWasEnabled) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
    }

    private static void restoreFogState(boolean fogWasEnabled) {
        if (fogWasEnabled) {
            GlStateManager.enableFog();
        } else {
            GlStateManager.disableFog();
        }
    }

    private static void restoreColorMask(ByteBuffer previousColorMask) {
        GlStateManager.colorMask(
            previousColorMask.get(0) != 0,
            previousColorMask.get(1) != 0,
            previousColorMask.get(2) != 0,
            previousColorMask.get(3) != 0);
    }

    private void dispatchComputes(int width, int height) {
        if (computes == null || computes.length == 0) {
            return;
        }

        for (ComputeProgram compute : computes) {
            if (compute == null) {
                continue;
            }
            compute.dispatch(width, height);
        }
    }

    public void recalculateSwapPassSize() {
        if (destroyed) {
            throw new IllegalStateException("Cannot resize a destroyed final pass renderer");
        }

        List<SwapPassReplacement> replacements = new ArrayList<>();
        GlFramebuffer pendingReplacementFramebuffer = null;
        try {
            for (SwapPass swapPass : swapPasses) {
                RenderTarget target = requireRenderTarget(swapPass.target, "Final swap pass resize");
                GlFramebuffer replacementFramebuffer =
                    renderTargets.createColorFramebuffer(ImmutableSet.of(), new int[] {swapPass.target});
                pendingReplacementFramebuffer = replacementFramebuffer;
                replacements.add(new SwapPassReplacement(
                    swapPass,
                    replacementFramebuffer,
                    target.getWidth(),
                    target.getHeight(),
                    target.getMainTexture()));
                pendingReplacementFramebuffer = null;
            }
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyFramebuffer(null, pendingReplacementFramebuffer);
            failure = destroySwapPassReplacements(failure, replacements);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        List<GlFramebuffer> previousFramebuffers = new ArrayList<>();
        for (SwapPassReplacement replacement : replacements) {
            previousFramebuffers.add(replacement.swapPass.from);
        }

        for (SwapPassReplacement replacement : replacements) {
            SwapPass swapPass = replacement.swapPass;
            swapPass.from = replacement.framebuffer;
            replacement.framebuffer = null;
            swapPass.width = replacement.width;
            swapPass.height = replacement.height;
            swapPass.targetTexture = replacement.targetTexture;
        }

        rethrowCleanupFailure(destroyFramebuffers(null, previousFramebuffers));
    }

    private void destroySwapPassReplacements(List<SwapPassReplacement> replacements) {
        rethrowCleanupFailure(destroySwapPassReplacements(null, replacements));
    }

    private Throwable destroySwapPassReplacements(Throwable failure, List<SwapPassReplacement> replacements) {
        for (SwapPassReplacement replacement : replacements) {
            if (replacement != null && replacement.framebuffer != null) {
                failure = destroyFramebuffer(failure, replacement.framebuffer);
                replacement.framebuffer = null;
            }
        }
        return failure;
    }

    private Throwable destroyFramebuffers(Throwable failure, List<GlFramebuffer> framebuffers) {
        if (framebuffers == null) {
            return failure;
        }
        for (GlFramebuffer framebuffer : framebuffers) {
            failure = destroyFramebuffer(failure, framebuffer);
        }
        return failure;
    }

    private static final class SwapPassReplacement {
        final SwapPass swapPass;
        GlFramebuffer framebuffer;
        final int width;
        final int height;
        final int targetTexture;

        SwapPassReplacement(SwapPass swapPass, GlFramebuffer framebuffer, int width, int height, int targetTexture) {
            this.swapPass = swapPass;
            this.framebuffer = framebuffer;
            this.width = width;
            this.height = height;
            this.targetTexture = targetTexture;
        }
    }

    private List<SwapPass> createSwapPasses(PackDirectives packDirectives, Set<Integer> flippedBuffers) {
        if (flippedBuffers == null || flippedBuffers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> clearedBuffers = packDirectives.getRenderTargetDirectives().getBuffersToBeCleared();
        List<SwapPass> passes = new ArrayList<>();
        SwapPass pendingSwapPass = null;
        try {
            for (Integer buffer : flippedBuffers) {
                if (buffer == null) {
                    throw new IllegalStateException("Final swap pass references a null render target index");
                }

                int bufferIndex = buffer;
                if (clearedBuffers.contains(bufferIndex)) {
                    continue;
                }

                RenderTarget target = requireRenderTarget(bufferIndex, "Final swap pass");
                SwapPass swapPass = new SwapPass();
                pendingSwapPass = swapPass;
                swapPass.target = bufferIndex;
                swapPass.width = target.getWidth();
                swapPass.height = target.getHeight();
                swapPass.from = renderTargets.createColorFramebuffer(ImmutableSet.of(), new int[] {bufferIndex});
                swapPass.targetTexture = target.getMainTexture();
                passes.add(swapPass);
                pendingSwapPass = null;
            }
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyFramebuffer(null, pendingSwapPass == null ? null : pendingSwapPass.from);
            if (pendingSwapPass != null) {
                pendingSwapPass.from = null;
            }
            failure = destroySwapPasses(failure, passes);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        return passes.isEmpty() ? Collections.emptyList() : passes;
    }

    private void runSwapPasses() {
        for (SwapPass swapPass : swapPasses) {
            swapPass.from.bind();
            OculusRenderSystem.copyTexSubImage2D(
                swapPass.targetTexture,
                GL11.GL_TEXTURE_2D,
                0,
                0,
                0,
                0,
                0,
                swapPass.width,
                swapPass.height
            );
        }
    }

    private void ensureColorHolderAttachment(Framebuffer mainFramebuffer) {
        int colorTexture = requireMainColorTexture(mainFramebuffer, "Final pass color holder");
        int colorTextureVersion = getColorBufferVersion(mainFramebuffer);
        if (colorTexture == lastColorTextureId && colorTextureVersion == lastColorTextureVersion) {
            return;
        }

        colorHolder.addColorAttachment(0, colorTexture);
        colorHolder.drawBuffers(new int[] {0});
        colorHolder.readBuffer(0);
        if (!colorHolder.isComplete()) {
            throw new IllegalStateException("Minecraft main color framebuffer attachment is incomplete");
        }
        lastColorTextureId = colorTexture;
        lastColorTextureVersion = colorTextureVersion;
    }

    private static int getColorBufferVersion(Framebuffer mainFramebuffer) {
        if (mainFramebuffer instanceof MinecraftFramebufferExt) {
            return ((MinecraftFramebufferExt) mainFramebuffer).oculus$getColorBufferVersion();
        }
        return 0;
    }

    private static Framebuffer requireMainFramebuffer(Minecraft minecraft) {
        if (minecraft == null) {
            throw new IllegalStateException("Final pass requires a Minecraft instance");
        }
        Framebuffer mainFramebuffer = minecraft.getFramebuffer();
        if (mainFramebuffer == null) {
            throw new IllegalStateException("Final pass requires a Minecraft main framebuffer");
        }
        return mainFramebuffer;
    }

    private static int requireMainColorTexture(Framebuffer mainFramebuffer, String context) {
        int colorTexture = mainFramebuffer.framebufferTexture;
        if (colorTexture <= 0) {
            throw new IllegalStateException(context + " requires a valid Minecraft main color texture");
        }
        return colorTexture;
    }

    private static void unbindAllSamplerTextures() {
        OculusRenderSystem.unbindTexture2DFromUnits(SamplerLimits.get().getMaxTextureUnits());
    }

    private void copyBaselineToMain(Framebuffer mainFramebuffer, int width, int height) {
        int colorTexture = requireMainColorTexture(mainFramebuffer, "Final pass baseline copy");
        baseline.bindAsReadBuffer();
        OculusRenderSystem.restoreDefaultActiveTexture();
        OculusRenderSystem.copyTexSubImage2D(
            colorTexture,
            GL11.GL_TEXTURE_2D,
            0,
            0,
            0,
            0,
            0,
            width,
            height
        );
    }

    public boolean hasCustomShader() {
        return program != null;
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        try {
            Throwable failure = null;
            failure = destroyProgram(failure, program);
            failure = destroyComputePrograms(failure, computes);
            failure = destroyFramebuffer(failure, baseline);
            failure = destroyGlFramebuffer(failure, colorHolder);
            failure = destroySwapPasses(failure, swapPasses);
            rethrowCleanupFailure(failure);
        } finally {
            clearSwapPassFramebuffers(swapPasses);
            destroyed = true;
        }
    }

    private ComputeProgram[] createComputes(ComputeSource[] computeSources, Set<Integer> stageReadsFromAlt) {
        if (computeSources == null || computeSources.length == 0) {
            return new ComputeProgram[0];
        }

        ComputeProgram[] programs = new ComputeProgram[computeSources.length];
        try {
            for (int i = 0; i < computeSources.length; i++) {
                ComputeSource source = computeSources[i];
                if (source == null || !source.isValid() || !source.getSource().isPresent()) {
                    continue;
                }

                String computeSource = prepareSource(source.getSource().get(), ShaderType.COMPUTE, source.getName());
                ProgramBuilder builder = ProgramBuilder.beginCompute(
                    source.getName(), computeSource,
                    SamplerOverrideMap.empty(), customUniforms, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS,
                    updateNotifier, packDirectives);
                overrideRenderTargetSamplerBindings(builder, stageReadsFromAlt);
                bindNoiseSampler(builder);
                IrisSamplers.addCompositeSamplerBindings(builder, renderTargets);
                bindShadowTargetSamplerBindings(builder, source.getName());
                bindCenterDepthSmooth(builder);
                applyCustomBindings(source.getName(), builder, flippedAtLeastOnce);
                ComputeProgram program = builder.buildCompute();
                programs[i] = program;
                program.setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());
            }
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, destroyComputePrograms(null, programs));
            throw exception;
        }

        return programs;
    }

    private void destroySwapPasses(List<SwapPass> passes) {
        Throwable failure = destroySwapPasses(null, passes);
        try {
            rethrowCleanupFailure(failure);
        } finally {
            clearSwapPassFramebuffers(passes);
        }
    }

    private Throwable destroySwapPasses(Throwable failure, List<SwapPass> passes) {
        if (passes == null) {
            return failure;
        }
        for (SwapPass swapPass : passes) {
            if (swapPass != null && swapPass.from != null) {
                failure = destroyFramebuffer(failure, swapPass.from);
            }
        }
        return failure;
    }

    private static void clearSwapPassFramebuffers(List<SwapPass> passes) {
        if (passes == null) {
            return;
        }
        for (SwapPass swapPass : passes) {
            if (swapPass != null) {
                swapPass.from = null;
            }
        }
    }

    private static void destroyComputePrograms(ComputeProgram[] programs) {
        rethrowCleanupFailure(destroyComputePrograms(null, programs));
    }

    private static Throwable destroyComputePrograms(Throwable failure, ComputeProgram[] programs) {
        if (programs == null) {
            return failure;
        }
        for (ComputeProgram compute : programs) {
            if (compute != null) {
                try {
                    compute.destroy();
                } catch (RuntimeException | Error exception) {
                    failure = addCleanupFailure(failure, exception);
                }
            }
        }
        return failure;
    }

    private static Throwable destroyProgram(Throwable failure, Program program) {
        if (program == null) {
            return failure;
        }
        try {
            program.destroy();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private Throwable destroyFramebuffer(Throwable failure, GlFramebuffer framebuffer) {
        if (framebuffer == null) {
            return failure;
        }
        try {
            renderTargets.destroyFramebuffer(framebuffer);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable destroyGlFramebuffer(Throwable failure, GlFramebuffer framebuffer) {
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

    private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure) {
        if (primary != null && cleanupFailure != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private void overrideRenderTargetSamplerBindings(ProgramBuilder builder, Set<Integer> stageReadsFromAlt) {
        IrisSamplers.addRenderTargetSamplerBindings(builder, () -> stageReadsFromAlt, renderTargets, true);
        IrisImages.addRenderTargetImages(builder, () -> stageReadsFromAlt, renderTargets);
    }

    private void bindShadowTargetSamplerBindings(ProgramBuilder builder, String programName) {
        ShadowMap shadowMap = ShadowMap.requireShadowTargets(builder, shadowMapSupplier, programName);
        if (shadowMap != null) {
            shadowMap.applySamplerBindings(builder, programName);
            IrisImages.addShadowColorImages(builder, shadowMap);
        }
    }

    private void applyCustomBindings(String programName, ProgramBuilder builder, ImmutableSet<Integer> flippedAtLeastOnce) {
        if (customTextureManager != null) {
            customTextureManager.applyCustomSamplers(programName, builder.samplers(), flippedAtLeastOnce);
        }
        if (customImageManager != null) {
            customImageManager.applyToProgram(builder);
        }
    }

    private void bindNoiseSampler(ProgramBuilder builder) {
        if (noiseTexture != null) {
            builder.overrideSamplerBinding("noisetex", TextureBinding.texture2D(noiseTexture));
        }
    }

    private String prepareSource(String source, ShaderType shaderType, String programName) {
        return ShaderSourcePreparer.prepare(packName, programName, shaderType, source, environmentDefines);
    }

    private RenderTarget requireRenderTarget(int index, String context) {
        RenderTarget target = renderTargets.get(index);
        if (target == null) {
            throw new IllegalStateException(context + " references unconfigured colortex" + index);
        }
        return target;
    }

    private static void setupMipmapping(RenderTarget target, int index, boolean readFromAlt) {
        if (target == null) {
            throw new IllegalStateException("Mipmapped render target colortex" + index + " is not configured");
        }
        int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();
        OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
            OculusRenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);

            int filter = target.getInternalFormat().getPixelFormat().isInteger()
                ? GL11.GL_NEAREST_MIPMAP_NEAREST
                : GL11.GL_LINEAR_MIPMAP_LINEAR;
            OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        });
    }

    private void resetRenderTargetMipmaps() {
        for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
            resetRenderTarget(renderTargets.get(i), i);
        }
    }

    private static void resetRenderTarget(RenderTarget target, int index) {
        if (target == null) {
            throw new IllegalStateException("Render target colortex" + index + " is not configured");
        }

        int filter = target.getInternalFormat().getPixelFormat().isInteger() ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
            OculusRenderSystem.texParameteri(target.getMainTexture(), GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, filter);
            OculusRenderSystem.texParameteri(target.getAltTexture(), GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, filter);
        });
        OculusRenderSystem.bindTexture2DToUnit(0, 0);
    }

    private static final class SwapPass {
        int target;
        int width;
        int height;
        GlFramebuffer from;
        int targetTexture;
    }
}
