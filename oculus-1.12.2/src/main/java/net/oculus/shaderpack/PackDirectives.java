package net.oculus.shaderpack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.oculus.Oculus;
import net.oculus.gl.texture.TextureScaleOverride;
import net.oculus.shaderpack.directives.DirectiveHolder;
import net.oculus.vendored.joml.Vector2i;

public final class PackDirectives {
    private boolean supportsColorCorrection;
    private int noiseTextureResolution;
    private float sunPathRotation;
    private float ambientOcclusionLevel;
    private float wetnessHalfLife;
    private float drynessHalfLife;
    private float eyeBrightnessHalfLife;
    private float centerDepthHalfLife;
    private CloudSetting cloudSetting;
    private boolean underwaterOverlay;
    private boolean vignette;
    private boolean sun;
    private boolean moon;
    private boolean backFaceSolid;
    private boolean backFaceCutout;
    private boolean backFaceCutoutMipped;
    private boolean backFaceTranslucent;
    private boolean rainDepth;
    private boolean beaconBeamDepth;
    private boolean separateAo;
    private boolean frustumCulling;
    private boolean occlusionCulling;
    private boolean oldLighting;
    private boolean concurrentCompute;
    private boolean oldHandLight;
    private ParticleRenderingOrder particleRenderingOrder;
    private boolean prepareBeforeShadow;

    private final PackRenderTargetDirectives renderTargetDirectives;
    private final PackShadowDirectives shadowDirectives;
    private final Map<String, Map<String, Boolean>> explicitFlips;
    private final Map<String, TextureScaleOverride> scaleOverrides;

    public PackDirectives() {
        this(PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS, ShaderProperties.empty());
    }

    public PackDirectives(ShaderProperties properties) {
        this(PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS, properties);
    }

    public PackDirectives(Set<Integer> supportedRenderTargets, ShaderProperties properties) {
        this.renderTargetDirectives = new PackRenderTargetDirectives(supportedRenderTargets);
        this.shadowDirectives = new PackShadowDirectives(properties);
        this.explicitFlips = properties.getExplicitFlips();
        this.scaleOverrides = properties.getTextureScaleOverrides();

        noiseTextureResolution = 256;
        sunPathRotation = 0.0F;
        ambientOcclusionLevel = 1.0F;
        wetnessHalfLife = 600.0f;
        drynessHalfLife = 200.0f;
        eyeBrightnessHalfLife = 10.0f;
        centerDepthHalfLife = 1.0F;

        cloudSetting = properties.getCloudSetting();
        underwaterOverlay = properties.getUnderwaterOverlay().orElse(false);
        vignette = properties.getVignette().orElse(false);
        sun = properties.getSun().orElse(true);
        moon = properties.getMoon().orElse(true);
        backFaceSolid = properties.getBackFaceSolid().orElse(false);
        backFaceCutout = properties.getBackFaceCutout().orElse(false);
        backFaceCutoutMipped = properties.getBackFaceCutoutMipped().orElse(false);
        backFaceTranslucent = properties.getBackFaceTranslucent().orElse(false);
        rainDepth = properties.getRainDepth().orElse(false);
        beaconBeamDepth = properties.getBeaconBeamDepth().orElse(false);
        separateAo = properties.getSeparateAo().orElse(false);
        frustumCulling = properties.getFrustumCulling().orElse(true);
        occlusionCulling = properties.getOcclusionCulling().orElse(true);
        oldLighting = properties.getOldLighting().orElse(false);
        supportsColorCorrection = properties.supportsColorCorrection().orElse(false);
        concurrentCompute = properties.getConcurrentCompute().orElse(false);
        oldHandLight = properties.getOldHandLight().orElse(true);
        particleRenderingOrder = properties.getParticleRenderingOrder()
            .orElseGet(() -> properties.getParticlesBeforeDeferred().orElse(false)
                ? ParticleRenderingOrder.BEFORE
                : ParticleRenderingOrder.DEFAULT);
        prepareBeforeShadow = properties.getPrepareBeforeShadow().orElse(false);
    }

    public void acceptDirectivesFrom(DirectiveHolder directives) {
        renderTargetDirectives.acceptDirectives(directives);
        shadowDirectives.acceptDirectives(directives);

        directives.acceptConstIntDirective("noiseTextureResolution", value -> this.noiseTextureResolution = value);
        directives.acceptConstFloatDirective("sunPathRotation", value -> this.sunPathRotation = value);
        directives.acceptConstFloatDirective("ambientOcclusionLevel", value -> this.ambientOcclusionLevel = clamp(value, 0.0f, 1.0f));
        directives.acceptConstFloatDirective("wetnessHalflife", value -> this.wetnessHalfLife = value);
        // Mirrors the local Oculus 1.16.5 PackDirectives assignment exactly.
        directives.acceptConstFloatDirective("drynessHalflife", value -> this.wetnessHalfLife = value);
        directives.acceptConstFloatDirective("eyeBrightnessHalflife", value -> this.eyeBrightnessHalfLife = value);
        directives.acceptConstFloatDirective("centerDepthHalflife", value -> this.centerDepthHalfLife = value);
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public CloudSetting getCloudSetting() {
        return cloudSetting;
    }

    public boolean underwaterOverlay() {
        return underwaterOverlay;
    }

    public boolean vignette() {
        return vignette;
    }

    public boolean shouldRenderSun() {
        return sun;
    }

    public boolean shouldRenderMoon() {
        return moon;
    }

    public boolean shouldRenderSolidBackFaces() {
        return backFaceSolid;
    }

    public boolean shouldRenderCutoutBackFaces() {
        return backFaceCutout;
    }

    public boolean shouldRenderCutoutMippedBackFaces() {
        return backFaceCutoutMipped;
    }

    public boolean shouldRenderTranslucentBackFaces() {
        return backFaceTranslucent;
    }

    public boolean rainDepth() {
        return rainDepth;
    }

    public boolean beaconBeamDepth() {
        return beaconBeamDepth;
    }

    public boolean shouldUseSeparateAo() {
        return separateAo;
    }

    public boolean shouldUseFrustumCulling() {
        return frustumCulling;
    }

    public boolean shouldUseOcclusionCulling() {
        return occlusionCulling;
    }

    public boolean isOldLighting() {
        return oldLighting;
    }

    public boolean getConcurrentCompute() {
        return concurrentCompute;
    }

    public boolean isOldHandLight() {
        return oldHandLight;
    }

    public boolean areParticlesBeforeDeferred() {
        return particleRenderingOrder == ParticleRenderingOrder.BEFORE;
    }

    public ParticleRenderingOrder getParticleRenderingOrder() {
        return particleRenderingOrder;
    }

    public boolean isPrepareBeforeShadow() {
        return prepareBeforeShadow;
    }

    public float getSunPathRotation() {
        return sunPathRotation;
    }

    public Vector2i getTextureScaleOverride(int index, int width, int height) {
        String name = "colortex" + index;
        TextureScaleOverride override = scaleOverrides.get(name);

        if (index < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
            String legacyName = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(index);
            override = scaleOverrides.getOrDefault(legacyName, override);
        }

        if (override == null) {
            return new Vector2i(width, height);
        }

        return new Vector2i(override.getX(width), override.getY(height));
    }

    public Map<Integer, Boolean> getExplicitFlips(String pass) {
        Map<String, Boolean> flips = explicitFlips.get(pass);
        if (flips == null || flips.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, Boolean> resolved = new HashMap<>();
        flips.forEach((buffer, shouldFlip) -> {
            int index = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(buffer);
            if (index == -1 && buffer.startsWith("colortex")) {
                try {
                    index = Integer.parseInt(buffer.substring("colortex".length()));
                } catch (NumberFormatException ex) {
                    index = -1;
                }
            }

            if (index != -1) {
                if (resolved.containsKey(index)) {
                    throw new IllegalArgumentException("Multiple entries with same key: " + index);
                }
                resolved.put(index, shouldFlip);
            } else {
                Oculus.LOGGER.warn("Unknown buffer '{}' in flip directive for pass {}", buffer, pass);
            }
        });
        return Collections.unmodifiableMap(resolved);
    }

    public PackRenderTargetDirectives getRenderTargetDirectives() {
        return renderTargetDirectives;
    }

    public PackShadowDirectives getShadowDirectives() {
        return shadowDirectives;
    }

    public boolean supportsColorCorrection() {
        return supportsColorCorrection;
    }

    public int getNoiseTextureResolution() {
        return noiseTextureResolution;
    }

    public float getAmbientOcclusionLevel() {
        return ambientOcclusionLevel;
    }

    public float getWetnessHalfLife() {
        return wetnessHalfLife;
    }

    public float getDrynessHalfLife() {
        return drynessHalfLife;
    }

    public float getEyeBrightnessHalfLife() {
        return eyeBrightnessHalfLife;
    }

    public float getCenterDepthHalfLife() {
        return centerDepthHalfLife;
    }
}
