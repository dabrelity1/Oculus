package net.oculus.pipeline.sampler;

import java.util.Map;
import java.util.Objects;

import net.oculus.gl.program.SamplerOverrideMap;
import net.oculus.gl.program.SamplerOverrideProvider;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;

/**
 * Centralizes the rules for mapping shader sampler names to texture units. The
 * configurator keeps the 1.12.2 pipeline aligned with the Iris contract by
 * reserving the same well-known sampler slots for render targets, shadow maps,
 * and utility textures (noise, flipped buffers, etc.).
 */
public final class SamplerOverrideConfigurator {
    private static final int NOISE_TEXTURE_UNIT = 12;
    private static final int SHADOW_DEPTH_UNIT = 5;
    private static final int SHADOW_COLOR_UNIT = 6;
    private static final int SHADOW_COLOR_UNIT_1 = 7;
    private static final int SHADOW_COLOR_UNIT_2 = 8;
    private static final int DEPTH_TEXTURE_UNIT = 9;

    private final PackDirectives directives;

    private SamplerOverrideConfigurator(PackDirectives directives) {
        this.directives = Objects.requireNonNull(directives, "directives");
    }

    public static SamplerOverrideConfigurator create(PackDirectives directives) {
        return new SamplerOverrideConfigurator(directives);
    }

    public SamplerOverrideProvider buildProvider() {
        SamplerOverrideMap overrides = buildGlobalOverrides();
        return programName -> overrides;
    }

    private SamplerOverrideMap buildGlobalOverrides() {
        SamplerOverrideMap.Builder builder = SamplerOverrideMap.builder();
        registerRenderTargetAliases(builder);
        registerShadowAliases(builder);
        registerNoiseAliases(builder);
        registerDepthAliases(builder);
        return builder.build();
    }

    private void registerRenderTargetAliases(SamplerOverrideMap.Builder builder) {
        PackRenderTargetDirectives renderTargets = directives.getRenderTargetDirectives();
        for (Map.Entry<Integer, PackRenderTargetDirectives.RenderTargetSettings> entry : renderTargets.getRenderTargetSettings().entrySet()) {
            int index = entry.getKey();
            builder.put("colortex" + index, index);

            if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
                builder.put(PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index), index);
            }

            // Additional aliases reserved for pack-defined overrides.
            builder.put("oculus_rt" + index, index);
            builder.put("oculus_flipped_rt" + index, index);
        }
    }

    private void registerShadowAliases(SamplerOverrideMap.Builder builder) {
        builder.put("oculus_shadow_depth", SHADOW_DEPTH_UNIT);
        builder.put("oculus_shadow_color", SHADOW_COLOR_UNIT);
        builder.put("oculus_shadow_color1", SHADOW_COLOR_UNIT_1);
        builder.put("oculus_shadow_color2", SHADOW_COLOR_UNIT_2);
    }

    private void registerNoiseAliases(SamplerOverrideMap.Builder builder) {
        builder.put("oculus_noise", NOISE_TEXTURE_UNIT);
        builder.put("custom_noise", NOISE_TEXTURE_UNIT);
        builder.put("noise_texture", NOISE_TEXTURE_UNIT);
    }

    private void registerDepthAliases(SamplerOverrideMap.Builder builder) {
        builder.put("gdepthtex", DEPTH_TEXTURE_UNIT);
        builder.put("oculus_depth", DEPTH_TEXTURE_UNIT);
    }
}
