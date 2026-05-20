package net.oculus.postprocess;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.oculus.client.OculusRuntimeValidation;
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
import net.oculus.rendertarget.RenderTargets;
import net.oculus.samplers.IrisImages;
import net.oculus.samplers.IrisSamplers;
import net.oculus.shader.ShaderSourcePreparer;
import net.oculus.shaderpack.ComputeSource;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ProgramDirectives;
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
 * Renders prepare, deferred, and composite shader stages. Each stage entry can contain
 * one or more compute programs followed by an optional fullscreen raster pass, matching
 * the Iris 1.16.5 pass ordering.
 */
public class CompositeRenderer {
    private static final Logger LOGGER = LogManager.getLogger(CompositeRenderer.class);
    private static final int COMPUTE_BARRIER = GL42.GL_TEXTURE_FETCH_BARRIER_BIT
        | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
    private static final int[] VALIDATION_DUMP_BUFFERS = new int[] {0, 2, 3, 4, 5, 6, 7};

    private final RenderTargets renderTargets;
    private final PackDirectives packDirectives;
    private final Supplier<ShadowMap> shadowMapSupplier;
    private final List<Pass> passes;
    private final FrameUpdateNotifier updateNotifier;
    private final BufferFlipper bufferFlipper;
    private final IntSupplier noiseTexture;
    private final CenterDepthSampler centerDepthSampler;
    private final CustomTextureManager customTextureManager;
    private final CustomImageManager customImageManager;
    private final CustomUniformExpressionManager customUniforms;
    private final String packName;
    private final List<StringPair> environmentDefines;
    private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
    private boolean destroyed = false;

    public CompositeRenderer(PackDirectives packDirectives,
                             ProgramSource[] sources,
                             ComputeSource[][] computes,
                             RenderTargets renderTargets,
                             Supplier<ShadowMap> shadowMapSupplier,
                             IntSupplier noiseTexture,
                             FrameUpdateNotifier updateNotifier,
                             CenterDepthSampler centerDepthSampler,
                             BufferFlipper bufferFlipper,
                             CustomTextureManager customTextureManager,
                             CustomImageManager customImageManager,
                             String packName,
                             List<StringPair> environmentDefines,
                             Map<Integer, Boolean> explicitPreFlips,
                             CustomUniformExpressionManager customUniforms) {
        this.renderTargets = renderTargets;
        this.packDirectives = packDirectives;
        this.shadowMapSupplier = shadowMapSupplier;
        this.passes = new ArrayList<>();
        this.updateNotifier = updateNotifier;
        this.noiseTexture = noiseTexture;
        this.centerDepthSampler = centerDepthSampler;
        this.bufferFlipper = bufferFlipper;
        this.customTextureManager = customTextureManager;
        this.customImageManager = customImageManager;
        this.customUniforms = customUniforms == null ? CustomUniformExpressionManager.empty() : customUniforms;
        this.packName = packName == null ? "" : packName;
        this.environmentDefines = environmentDefines == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(environmentDefines);

        ImmutableSet.Builder<Integer> flippedAtLeastOnce = ImmutableSet.builder();
        int passCount = sources == null ? 0 : sources.length;

        Map<Integer, Boolean> preFlips = explicitPreFlips == null
            ? Collections.emptyMap()
            : explicitPreFlips;
        preFlips.forEach((buffer, shouldFlip) -> {
            if (Boolean.TRUE.equals(shouldFlip)) {
                bufferFlipper.flip(buffer);
            }
        });

        Pass pendingPass = null;
        try {
            for (int i = 0; i < passCount; i++) {
                ProgramSource source = sources[i];
                ComputeSource[] computeSources = computesAt(computes, i);
                ImmutableSet<Integer> stageReadsFromAlt = bufferFlipper.snapshot();
                ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();

                if (source == null || !source.isValid()) {
                    if (computeSources != null) {
                        ComputeOnlyPass pass = new ComputeOnlyPass();
                        pendingPass = pass;
                        pass.index = i;
                        pass.name = source == null ? "compute-only" : source.getName();
                        pass.computes = createComputes(computeSources, stageReadsFromAlt, flippedAtLeastOnceSnapshot);
                        pass.stageReadsFromAlt = stageReadsFromAlt;
                        pass.stageReadsAfter = stageReadsFromAlt;
                        pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;
                        passes.add(pass);
                        pendingPass = null;
                    }
                    continue;
                }

                Pass pass = createPass(source, i, computeSources, stageReadsFromAlt, flippedAtLeastOnceSnapshot);
                pendingPass = pass;
                passes.add(pass);
                pendingPass = null;

                Map<Integer, Boolean> explicitFlips = source.getDirectives().getExplicitFlips();
                for (int buffer : pass.drawBuffers) {
                    if (explicitFlips.get(buffer) == Boolean.FALSE) {
                        continue;
                    }

                    bufferFlipper.flip(buffer);
                    flippedAtLeastOnce.add(buffer);
                }

                explicitFlips.forEach((buffer, shouldFlip) -> {
                    if (Boolean.TRUE.equals(shouldFlip)) {
                        bufferFlipper.flip(buffer);
                        flippedAtLeastOnce.add(buffer);
                    }
                });
                pass.stageReadsAfter = bufferFlipper.snapshot();

                LOGGER.debug("Created composite pass {} from source {}", i, source.getName());
            }
        } catch (RuntimeException | Error exception) {
            try {
                destroyPendingAndCreatedPasses(pendingPass);
            } catch (RuntimeException | Error cleanupException) {
                if (cleanupException != exception) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }

        this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();
        LOGGER.info("Created {} postprocess pass(es)", passes.size());
        try {
            unbindReadFramebufferAfterConstruction();
        } catch (RuntimeException | Error exception) {
            try {
                destroyCreatedPasses();
            } catch (RuntimeException | Error cleanupException) {
                if (cleanupException != exception) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }
    }

    public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
        return flippedAtLeastOnceFinal;
    }

    private static ComputeSource[] computesAt(ComputeSource[][] computes, int index) {
        return computes != null && index >= 0 && index < computes.length ? computes[index] : null;
    }

    private static void unbindReadFramebufferAfterConstruction() {
        OculusRenderSystem.bindReadFramebuffer(0);
    }

    private Pass createPass(ProgramSource source, int index, ComputeSource[] computeSources,
                            ImmutableSet<Integer> stageReadsFromAlt,
                            ImmutableSet<Integer> flippedAtLeastOnceSnapshot) {
        ProgramDirectives directives = source.getDirectives();

        String vertexSource = source.getVertexSource().orElse(null);
        String fragmentSource = source.getFragmentSource().orElse(null);
        String geometrySource = source.getGeometrySource().orElse(null);

        if (vertexSource == null || fragmentSource == null) {
            throw new IllegalStateException("Composite pass " + index + " is missing vertex or fragment source");
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

        Program program = null;
        ComputeProgram[] compiledComputes = new ComputeProgram[0];
        GlFramebuffer framebuffer = null;
        boolean complete = false;

        try {
            ProgramBuilder builder = ProgramBuilder.begin(
                source.getName(), vertexSource, geometrySource, fragmentSource,
                SamplerOverrideMap.empty(), customUniforms, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS, null,
                updateNotifier, packDirectives);
            overrideRenderTargetSamplerBindings(builder, stageReadsFromAlt);
            bindNoiseSampler(builder);
            IrisSamplers.addCompositeSamplerBindings(builder, renderTargets);
            bindShadowTargetSamplerBindings(builder, source.getName());
            bindCenterDepthSmooth(builder);
            applyCustomBindings(source.getName(), builder, flippedAtLeastOnceSnapshot);
            program = builder.build();
            compiledComputes = createComputes(computeSources, stageReadsFromAlt, flippedAtLeastOnceSnapshot);

            int[] drawBuffers = directives.getDrawBuffers();

            int viewWidth = 0;
            int viewHeight = 0;
            for (int buffer : drawBuffers) {
                RenderTarget target = requireRenderTarget(buffer, "Postprocess pass " + source.getName());
                if ((viewWidth > 0 && viewWidth != target.getWidth())
                        || (viewHeight > 0 && viewHeight != target.getHeight())) {
                    throw new IllegalStateException("Postprocess pass " + source.getName()
                        + " writes to targets with mismatched sizes");
                }
                viewWidth = target.getWidth();
                viewHeight = target.getHeight();
            }

            framebuffer = renderTargets.createColorFramebuffer(stageReadsFromAlt, drawBuffers);

            Pass pass = new Pass();
            pass.program = program;
            pass.computes = compiledComputes;
            pass.framebuffer = framebuffer;
            pass.drawBuffers = drawBuffers;
            pass.viewWidth = viewWidth;
            pass.viewHeight = viewHeight;
            pass.viewportScale = directives.getViewportScale();
            pass.stageReadsFromAlt = stageReadsFromAlt;
            pass.stageReadsAfter = stageReadsFromAlt;
            pass.mipmappedBuffers = ImmutableSet.copyOf(directives.getMipmappedBuffers());
            pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;
            pass.index = index;
            pass.name = source.getName();
            complete = true;
            return pass;
        } catch (RuntimeException | Error exception) {
            if (!complete) {
                Throwable failure = null;
                failure = destroyProgram(failure, program);
                failure = destroyComputePrograms(failure, compiledComputes);
                failure = destroyFramebuffer(failure, framebuffer);
                addSuppressedCleanupFailure(exception, failure);
            }
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

    public void renderAll() {
        if (destroyed) {
            throw new IllegalStateException("Cannot render a destroyed composite renderer");
        }

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean fogWasEnabled = GL11.glIsEnabled(GL11.GL_FOG);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);

        boolean fullscreenQuadBegun = false;
        Throwable failure = null;
        try {
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.disableFog();
            GlStateManager.colorMask(true, true, true, true);

            FullScreenQuadRenderer.INSTANCE.begin();
            fullscreenQuadBegun = true;

            for (Pass pass : passes) {
                boolean ranCompute = dispatchComputes(pass.computes);
                if (ranCompute) {
                    OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);
                }

                Program.unbind();

                if (pass instanceof ComputeOnlyPass) {
                    if (ranCompute) {
                        dumpValidationRenderTargetsAfterPass(pass);
                    }
                    continue;
                }

                renderPass(pass);
                dumpValidationRenderTargetsAfterPass(pass);
            }

            try {
                FullScreenQuadRenderer.INSTANCE.end();
            } finally {
                fullscreenQuadBegun = false;
            }
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
            cleanupFailure = restoreAfterRenderAll(cleanupFailure, previousActiveTexture, blendWasEnabled,
                alphaWasEnabled, fogWasEnabled, previousColorMask);
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private Throwable restoreAfterRenderAll(
            Throwable failure,
            int previousActiveTexture,
            boolean blendWasEnabled,
            boolean alphaWasEnabled,
            boolean fogWasEnabled,
            ByteBuffer previousColorMask) {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer mainFramebuffer = mc == null ? null : mc.getFramebuffer();
        if (mainFramebuffer != null) {
            failure = runCleanup(failure, () -> mainFramebuffer.bindFramebuffer(true));
        } else {
            failure = runCleanup(failure, () -> OculusRenderSystem.restoreFramebufferBindings(0, 0, 0));
        }
        if (mc != null && mainFramebuffer == null) {
            failure = runCleanup(failure, () -> GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight));
        }

        failure = runCleanup(failure, ProgramUniforms::clearActiveUniforms);
        failure = runCleanup(failure, ProgramSamplers::clearActiveSamplers);
        failure = runCleanup(failure, ProgramImages::clearActiveImages);
        failure = runCleanup(failure, () -> GL20.glUseProgram(0));
        failure = runCleanup(failure,
            () -> OculusRenderSystem.unbindTexture2DFromUnits(SamplerLimits.get().getMaxTextureUnits()));
        failure = runCleanup(failure, () -> OculusRenderSystem.setActiveTextureUnit(previousActiveTexture));
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

    private boolean dispatchComputes(ComputeProgram[] computes) {
        if (computes == null || computes.length == 0) {
            return false;
        }

        boolean ranCompute = false;
        int width = 0;
        int height = 0;
        for (ComputeProgram compute : computes) {
            if (compute == null) {
                continue;
            }

            if (!ranCompute) {
                Framebuffer mainFramebuffer = requireMainFramebuffer(Minecraft.getMinecraft());
                width = Math.max(1, mainFramebuffer.framebufferWidth);
                height = Math.max(1, mainFramebuffer.framebufferHeight);
            }
            compute.dispatch(width, height);
            ranCompute = true;
        }

        return ranCompute;
    }

    private static Framebuffer requireMainFramebuffer(Minecraft minecraft) {
        if (minecraft == null) {
            throw new IllegalStateException("Composite compute dispatch requires a Minecraft instance");
        }
        Framebuffer mainFramebuffer = minecraft.getFramebuffer();
        if (mainFramebuffer == null) {
            throw new IllegalStateException("Composite compute dispatch requires a Minecraft main framebuffer");
        }
        return mainFramebuffer;
    }

    private void renderPass(Pass pass) {
        if (!pass.mipmappedBuffers.isEmpty()) {
            OculusRenderSystem.restoreDefaultActiveTexture();
            for (int index : pass.mipmappedBuffers) {
                setupMipmapping(renderTargets.get(index), index, pass.stageReadsFromAlt.contains(index));
            }
        }

        int scaledWidth = Math.max(1, (int) (pass.viewWidth * pass.viewportScale));
        int scaledHeight = Math.max(1, (int) (pass.viewHeight * pass.viewportScale));
        GL11.glViewport(0, 0, scaledWidth, scaledHeight);

        pass.framebuffer.bind();
        pass.program.use();
        logValidationSamplerBinding(pass, "colortex0");
        logValidationSamplerBinding(pass, "depthtex0");
        logValidationSamplerBinding(pass, "depthtex1");
        FullScreenQuadRenderer.INSTANCE.renderQuad();
    }

    private void logValidationSamplerBinding(Pass pass, String samplerName) {
        if (!OculusRuntimeValidation.isRenderTargetDumpEnabled() || pass == null || pass.program == null
                || samplerName == null) {
            return;
        }

        int location = GL20.glGetUniformLocation(pass.program.getProgramId(), samplerName);
        if (location < 0) {
            return;
        }

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            IntBuffer unitBuffer = BufferUtils.createIntBuffer(1);
            GL20.glGetUniform(pass.program.getProgramId(), location, unitBuffer);
            int unit = unitBuffer.get(0);
            OculusRenderSystem.setActiveTextureUnit(GL13.GL_TEXTURE0 + unit);
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            LOGGER.info(
                "Oculus runtime validation postprocess sampler pass={} index={} sampler={} unit={} texture={} readsAlt={} drawBuffers={}",
                pass.name,
                pass.index,
                samplerName,
                unit,
                texture,
                pass.stageReadsFromAlt,
                java.util.Arrays.toString(pass.drawBuffers)
            );
        } finally {
            OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
        }
    }

    private void dumpValidationRenderTargetsAfterPass(Pass pass) {
        if (!OculusRuntimeValidation.isRenderTargetDumpEnabled() || renderTargets == null || pass == null) {
            return;
        }

        ImmutableSet<Integer> readBuffers = pass.stageReadsAfter == null
            ? pass.stageReadsFromAlt
            : pass.stageReadsAfter;
        String passName = pass.name == null ? "unknown" : pass.name;
        for (int bufferIndex : VALIDATION_DUMP_BUFFERS) {
            RenderTarget target = renderTargets.get(bufferIndex);
            if (target == null) {
                continue;
            }

            boolean readsAlt = readBuffers != null && readBuffers.contains(bufferIndex);
            int texture = readsAlt ? target.getAltTexture() : target.getMainTexture();
            OculusRuntimeValidation.dumpRenderTargetTexture(
                "postprocess-pass-" + pass.index + "-" + passName
                    + "-colortex" + bufferIndex + "-" + (readsAlt ? "alt" : "main"),
                texture,
                target.getWidth(),
                target.getHeight()
            );
        }
    }

    public void recalculateSizes() {
        if (destroyed) {
            throw new IllegalStateException("Cannot resize a destroyed composite renderer");
        }

        List<ResizeReplacement> replacements = new ArrayList<>();
        GlFramebuffer pendingReplacementFramebuffer = null;
        try {
            for (Pass pass : passes) {
                if (pass instanceof ComputeOnlyPass) {
                    continue;
                }

                int viewWidth = 0;
                int viewHeight = 0;
                for (int buffer : pass.drawBuffers) {
                    RenderTarget target = requireRenderTarget(buffer, "Postprocess resize");
                    if ((viewWidth > 0 && viewWidth != target.getWidth()) || (viewHeight > 0 && viewHeight != target.getHeight())) {
                        throw new IllegalStateException("Postprocess pass writes to targets with mismatched sizes");
                    }
                    viewWidth = target.getWidth();
                    viewHeight = target.getHeight();
                }

                GlFramebuffer replacementFramebuffer =
                    renderTargets.createColorFramebuffer(pass.stageReadsFromAlt, pass.drawBuffers);
                pendingReplacementFramebuffer = replacementFramebuffer;
                replacements.add(new ResizeReplacement(pass, replacementFramebuffer, viewWidth, viewHeight));
                pendingReplacementFramebuffer = null;
            }
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyFramebuffer(null, pendingReplacementFramebuffer);
            failure = destroyResizeReplacements(failure, replacements);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }

        List<GlFramebuffer> previousFramebuffers = new ArrayList<>();
        for (ResizeReplacement replacement : replacements) {
            previousFramebuffers.add(replacement.pass.framebuffer);
        }

        for (ResizeReplacement replacement : replacements) {
            Pass pass = replacement.pass;
            pass.framebuffer = replacement.framebuffer;
            replacement.framebuffer = null;
            pass.viewWidth = replacement.viewWidth;
            pass.viewHeight = replacement.viewHeight;
        }

        rethrowCleanupFailure(destroyFramebuffers(null, previousFramebuffers));
    }

    private void destroyResizeReplacements(List<ResizeReplacement> replacements) {
        rethrowCleanupFailure(destroyResizeReplacements(null, replacements));
    }

    private Throwable destroyResizeReplacements(Throwable failure, List<ResizeReplacement> replacements) {
        for (ResizeReplacement replacement : replacements) {
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

    private static final class ResizeReplacement {
        final Pass pass;
        GlFramebuffer framebuffer;
        final int viewWidth;
        final int viewHeight;

        ResizeReplacement(Pass pass, GlFramebuffer framebuffer, int viewWidth, int viewHeight) {
            this.pass = pass;
            this.framebuffer = framebuffer;
            this.viewWidth = viewWidth;
            this.viewHeight = viewHeight;
        }
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        try {
            Throwable failure = null;
            for (Pass pass : passes) {
                failure = destroyPass(failure, pass, renderTargets);
            }
            rethrowCleanupFailure(failure);
        } finally {
            passes.clear();
            destroyed = true;
        }
    }

    private void destroyCreatedPasses() {
        destroyPendingAndCreatedPasses(null);
    }

    private void destroyPendingAndCreatedPasses(Pass pendingPass) {
        try {
            Throwable failure = null;
            failure = destroyPass(failure, pendingPass, renderTargets);
            for (Pass pass : passes) {
                failure = destroyPass(failure, pass, renderTargets);
            }
            rethrowCleanupFailure(failure);
        } finally {
            passes.clear();
        }
    }

    public int getPassCount() {
        return passes.size();
    }

    private ComputeProgram[] createComputes(
            ComputeSource[] computes,
            ImmutableSet<Integer> stageReadsFromAlt,
            ImmutableSet<Integer> flippedAtLeastOnce) {
        if (computes == null || computes.length == 0) {
            return new ComputeProgram[0];
        }

        ComputeProgram[] programs = new ComputeProgram[computes.length];
        try {
            for (int i = 0; i < computes.length; i++) {
                ComputeSource source = computes[i];
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

    private static class Pass {
        Program program;
        ComputeProgram[] computes = new ComputeProgram[0];
        GlFramebuffer framebuffer;
        int[] drawBuffers = new int[0];
        int viewWidth;
        int viewHeight;
        float viewportScale = 1.0f;
        ImmutableSet<Integer> stageReadsFromAlt = ImmutableSet.of();
        ImmutableSet<Integer> stageReadsAfter = ImmutableSet.of();
        ImmutableSet<Integer> mipmappedBuffers = ImmutableSet.of();
        ImmutableSet<Integer> flippedAtLeastOnce = ImmutableSet.of();
        int index = -1;
        String name = "unknown";

        void destroy(RenderTargets renderTargets) {
            try {
                Throwable failure = null;
                failure = destroyProgram(failure, program);
                failure = destroyComputes(failure);
                failure = destroyFramebuffer(failure, framebuffer, renderTargets);
                rethrowCleanupFailure(failure);
            } finally {
                program = null;
                computes = new ComputeProgram[0];
                framebuffer = null;
            }
        }

        void destroyComputes() {
            try {
                Throwable failure = destroyComputePrograms(null, computes);
                rethrowCleanupFailure(failure);
            } finally {
                computes = new ComputeProgram[0];
            }
        }

        Throwable destroyComputes(Throwable failure) {
            return destroyComputePrograms(failure, computes);
        }
    }

    private static void destroyComputePrograms(ComputeProgram[] computes) {
        rethrowCleanupFailure(destroyComputePrograms(null, computes));
    }

    private static Throwable destroyComputePrograms(Throwable failure, ComputeProgram[] computes) {
        if (computes == null) {
            return failure;
        }
        for (ComputeProgram compute : computes) {
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
        return destroyFramebuffer(failure, framebuffer, renderTargets);
    }

    private static Throwable destroyFramebuffer(Throwable failure, GlFramebuffer framebuffer,
                                               RenderTargets renderTargets) {
        if (framebuffer == null || renderTargets == null) {
            return failure;
        }
        try {
            renderTargets.destroyFramebuffer(framebuffer);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable destroyPass(Throwable failure, Pass pass, RenderTargets renderTargets) {
        if (pass == null) {
            return failure;
        }
        try {
            pass.destroy(renderTargets);
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

    private static class ComputeOnlyPass extends Pass {
        @Override
        void destroy(RenderTargets renderTargets) {
            try {
                Throwable failure = destroyComputes(null);
                rethrowCleanupFailure(failure);
            } finally {
                computes = new ComputeProgram[0];
            }
        }
    }
}
