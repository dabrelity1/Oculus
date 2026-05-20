package net.oculus.samplers;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.renderer.OpenGlHelper;
import net.oculus.gl.state.StateUpdateNotifiers;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.TextureBinding;
import net.oculus.pipeline.InputAvailability;
import net.oculus.pipeline.WorldRenderingPipeline;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.shaderpack.IrisLimits;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.texture.FallbackTextures;

/**
 * Iris-style sampler helpers for render targets. The texture suppliers intentionally
 * look up the render target at update time so long-lived bindings follow buffer state.
 */
public final class IrisSamplers {
    public static final ImmutableSet<Integer> WORLD_RESERVED_TEXTURE_UNITS = ImmutableSet.of(0, 1, 2);
    public static final ImmutableSet<Integer> COMPOSITE_RESERVED_TEXTURE_UNITS = ImmutableSet.of(1, 2);
    private static final String[] FULLSCREEN_COLORTEX0_ALIASES = {
        "colortex0",
        "gcolor",
        "gtexture0",
        "s_texture",
        "tex",
        "texture",
        "gtexture",
        "gaux0"
    };
    private static final int FIRST_UNSUPPORTED_RENDER_TARGET = IrisLimits.MAX_COLOR_BUFFERS;
    private static final int MAX_UNSUPPORTED_RENDER_TARGET_SCAN = 99;

    private IrisSamplers() {
    }

    public static void addRenderTargetSamplerBindings(ProgramBuilder builder,
                                                      Supplier<? extends Set<Integer>> flipped,
                                                      RenderTargets renderTargets) {
        addRenderTargetSamplerBindings(builder, flipped, renderTargets, true);
    }

    public static void addRenderTargetSamplerBindings(ProgramBuilder builder,
                                                      Supplier<? extends Set<Integer>> flipped,
                                                      RenderTargets renderTargets,
                                                      boolean fullscreenPass) {
        if (builder == null) {
            return;
        }

        int startIndex = firstRenderTargetSamplerIndex(fullscreenPass);
        if (renderTargets == null) {
            failIfProgramReferencesMissingRenderTargetsBeyondCount(builder, startIndex, 0);
            return;
        }

        int renderTargetCount = renderTargets.getRenderTargetCount();
        for (int index = startIndex; index < renderTargetCount; index++) {
            RenderTarget target = renderTargets.get(index);
            if (target == null) {
                failIfProgramReferencesMissingRenderTarget(builder, index);
                continue;
            }

            TextureBinding current = renderTargetBinding(renderTargets, flipped, index, false);
            TextureBinding opposite = renderTargetBinding(renderTargets, flipped, index, true);
            String name = "colortex" + index;

            if (index == 0 && fullscreenPass) {
                builder.addDefaultSampler(current, FULLSCREEN_COLORTEX0_ALIASES);
            } else {
                builder.overrideSamplerBinding(name, current);
                if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
                    builder.overrideSamplerBinding(PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index), current);
                }
            }
            builder.overrideSamplerBinding("oculus_rt" + index, current);
            builder.overrideSamplerBinding("oculus_flipped_rt" + index, opposite);
        }

        failIfProgramReferencesMissingRenderTargetsBeyondCount(builder, startIndex, renderTargetCount);
    }

    private static void failIfProgramReferencesMissingRenderTarget(ProgramBuilder builder, int index) {
        if (builder.hasSampler("colortex" + index)
            || hasLegacyRenderTargetSampler(builder, index)
            || builder.hasSampler("oculus_rt" + index)
            || builder.hasSampler("oculus_flipped_rt" + index)) {
            throw new IllegalStateException("Render target colortex" + index + " is not configured");
        }
    }

    private static void failIfProgramReferencesMissingRenderTargetsBeyondCount(ProgramBuilder builder,
                                                                              int startIndex,
                                                                              int renderTargetCount) {
        int firstMissingIndex = Math.max(startIndex, renderTargetCount);
        for (int index = firstMissingIndex; index < IrisLimits.MAX_COLOR_BUFFERS; index++) {
            failIfProgramReferencesMissingRenderTarget(builder, index);
        }
        failIfProgramReferencesUnsupportedRenderTargets(builder);
    }

    private static void failIfProgramReferencesUnsupportedRenderTargets(ProgramBuilder builder) {
        String unsupportedResource = findUnsupportedRenderTargetSampler(builder);
        if (unsupportedResource != null) {
            throw unsupportedRenderTargetException(unsupportedResource);
        }
    }

    private static String findUnsupportedRenderTargetSampler(ProgramBuilder builder) {
        for (String name : builder.getActiveSamplerUniformNames()) {
            int index = renderTargetIndex(name);
            if (index >= IrisLimits.MAX_COLOR_BUFFERS) {
                return "colortex" + index;
            }
        }

        for (int index = FIRST_UNSUPPORTED_RENDER_TARGET; index <= MAX_UNSUPPORTED_RENDER_TARGET_SCAN; index++) {
            if (builder.hasSampler("colortex" + index)
                || builder.hasSampler("oculus_rt" + index)
                || builder.hasSampler("oculus_flipped_rt" + index)) {
                return "colortex" + index;
            }
        }
        return null;
    }

    private static IllegalStateException unsupportedRenderTargetException(String resource) {
        return new IllegalStateException("Render target " + resource
            + " is not supported; Oculus 1.16.5 exposes up to "
            + IrisLimits.MAX_COLOR_BUFFERS + " color render targets.");
    }

    private static int renderTargetIndex(String name) {
        if (name == null) {
            return -1;
        }
        int index = indexAfterPrefix(name, "colortex");
        if (index >= 0) {
            return index;
        }
        index = indexAfterPrefix(name, "oculus_rt");
        if (index >= 0) {
            return index;
        }
        return indexAfterPrefix(name, "oculus_flipped_rt");
    }

    private static int indexAfterPrefix(String name, String prefix) {
        if (!name.startsWith(prefix) || name.length() == prefix.length()) {
            return -1;
        }
        long value = 0L;
        for (int i = prefix.length(); i < name.length(); i++) {
            char character = name.charAt(i);
            if (character < '0' || character > '9') {
                return -1;
            }
            value = value * 10 + character - '0';
            if (value > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) value;
    }

    private static boolean hasLegacyRenderTargetSampler(ProgramBuilder builder, int index) {
        return index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()
            && builder.hasSampler(PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index));
    }

    static int firstRenderTargetSamplerIndex(boolean fullscreenPass) {
        return fullscreenPass ? 0 : 4;
    }

    public static void addCompositeSamplerBindings(ProgramBuilder builder, RenderTargets renderTargets) {
        if (builder == null) {
            return;
        }
        if (renderTargets == null) {
            failIfProgramReferencesCompositeDepthSampler(builder);
            return;
        }

        TextureBinding depth = TextureBinding.texture2D(renderTargets::getCurrentDepthTexture);
        TextureBinding depthNoTranslucents = TextureBinding.texture2D(() ->
            renderTargets.getDepthTextureNoTranslucents().getTextureId());
        TextureBinding depthNoHand = TextureBinding.texture2D(() ->
            renderTargets.getDepthTextureNoHand().getTextureId());

        builder.overrideSamplerBinding("gdepthtex", depth);
        builder.overrideSamplerBinding("depthtex0", depth);
        builder.overrideSamplerBinding("depthtex1", depthNoTranslucents);
        builder.overrideSamplerBinding("depthtex2", depthNoHand);
        builder.overrideSamplerBinding("oculus_depth", depth);
    }

    public static void addWorldDepthSamplerBindings(ProgramBuilder builder, RenderTargets renderTargets) {
        if (builder == null) {
            return;
        }
        if (renderTargets == null) {
            failIfProgramReferencesWorldDepthSampler(builder);
            return;
        }

        TextureBinding depth = TextureBinding.texture2D(renderTargets::getCurrentDepthTexture);
        TextureBinding depthNoTranslucents = TextureBinding.texture2D(() ->
            renderTargets.getDepthTextureNoTranslucents().getTextureId());

        builder.overrideSamplerBinding("depthtex0", depth);
        builder.overrideSamplerBinding("depthtex1", depthNoTranslucents);
    }

    private static void failIfProgramReferencesCompositeDepthSampler(ProgramBuilder builder) {
        if (builder.hasSampler("gdepthtex")
            || builder.hasSampler("depthtex0")
            || builder.hasSampler("depthtex1")
            || builder.hasSampler("depthtex2")
            || builder.hasSampler("oculus_depth")) {
            throw new IllegalStateException("Composite depth samplers require configured render targets");
        }
    }

    private static void failIfProgramReferencesWorldDepthSampler(ProgramBuilder builder) {
        if (builder.hasSampler("depthtex0") || builder.hasSampler("depthtex1")) {
            throw new IllegalStateException("World depth samplers require configured render targets");
        }
    }

    public static void addLevelSamplerBindings(ProgramBuilder builder,
                                               WorldRenderingPipeline pipeline,
                                               InputAvailability availability) {
        if (builder == null) {
            return;
        }

        Objects.requireNonNull(pipeline, "pipeline");
        InputAvailability inputs = availability == null
            ? new InputAvailability(true, true, true)
            : availability;

        if (inputs.texture) {
            builder.addExternalSampler(0, "tex", "texture", "gtexture");
        } else {
            builder.addDynamicSampler(FallbackTextures::getWhiteTexture,
                "tex", "texture", "gtexture", "gcolor", "colortex0");
        }

        if (inputs.lightmap) {
            builder.addExternalSampler(getLightmapTextureUnit(), "lightmap");
        } else {
            builder.addDynamicSampler(FallbackTextures::getWhiteTexture, "lightmap");
        }

        if (inputs.overlay) {
            builder.addExternalSampler(getOverlayTextureUnit(), "iris_overlay");
        } else {
            builder.addDynamicSampler(FallbackTextures::getWhiteTexture, "iris_overlay");
        }

        builder.addDynamicSampler(pipeline::getCurrentNormalTexture,
            StateUpdateNotifiers.normalTextureChangeNotifier, "normals");
        builder.addDynamicSampler(pipeline::getCurrentSpecularTexture,
            StateUpdateNotifiers.specularTextureChangeNotifier, "specular");
    }

    public static TextureBinding renderTargetBinding(RenderTargets renderTargets,
                                                     Supplier<? extends Set<Integer>> flipped,
                                                     int index,
                                                     boolean opposite) {
        return TextureBinding.texture2D(() -> renderTargetTextureId(renderTargets, flipped, index, opposite));
    }

    public static int renderTargetTextureId(RenderTargets renderTargets,
                                            Supplier<? extends Set<Integer>> flipped,
                                            int index,
                                            boolean opposite) {
        if (renderTargets == null) {
            throw new IllegalStateException("Render targets are not configured");
        }

        RenderTarget target = renderTargets.get(index);
        if (target == null) {
            throw new IllegalStateException("Render target colortex" + index + " is not configured");
        }

        Set<Integer> flippedBuffers = flipped == null ? Collections.emptySet() : flipped.get();
        boolean readFromAlt = flippedBuffers != null && flippedBuffers.contains(index);
        boolean useAlt = opposite ? !readFromAlt : readFromAlt;
        return useAlt ? target.getAltTexture() : target.getMainTexture();
    }

    private static int getLightmapTextureUnit() {
        return Math.max(0, OpenGlHelper.lightmapTexUnit - OpenGlHelper.defaultTexUnit);
    }

    private static int getOverlayTextureUnit() {
        return Math.max(0, OpenGlHelper.GL_TEXTURE2 - OpenGlHelper.defaultTexUnit);
    }
}
