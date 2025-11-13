package net.oculus.shaderpack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.oculus.shaderpack.directives.DirectiveHolder;

/**
 * Aggregated shader pack directives. The 1.16.5 code exposes a large number of
 * options; the 1.12.2 skeleton keeps the surface area but answers with safe
 * defaults until real parsing is ported.
 */
public final class PackDirectives {
    private final PackRenderTargetDirectives renderTargetDirectives;
    private final Map<String, Map<Integer, Boolean>> explicitFlips;

    private CloudSetting cloudSetting = CloudSetting.DEFAULT;
    private boolean underwaterOverlay = true;
    private boolean vignette = true;
    private boolean sun = true;
    private boolean moon = true;
    private boolean rainDepth = false;
    private boolean particlesBeforeDeferred = false;
    private boolean concurrentCompute = false;
    private boolean prepareBeforeShadow = false;
    private boolean oldLighting = false;
    private float sunPathRotation = 0.0F;

    public PackDirectives() {
        this(PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS, ShaderProperties.empty());
    }

    public PackDirectives(ShaderProperties properties) {
        this(PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS, properties);
    }

    public PackDirectives(Set<Integer> supportedRenderTargets, ShaderProperties properties) {
        this.renderTargetDirectives = new PackRenderTargetDirectives(supportedRenderTargets);
        this.explicitFlips = new HashMap<>();
        // Future work: populate state from properties when option parsing is ported.
    }

    public void acceptDirectivesFrom(DirectiveHolder directives) {
        renderTargetDirectives.acceptDirectives(directives);
        // Shadow, weather, and other directives will be forwarded once parsing is active.
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

    public boolean rainDepth() {
        return rainDepth;
    }

    public boolean areParticlesBeforeDeferred() {
        return particlesBeforeDeferred;
    }

    public boolean getConcurrentCompute() {
        return concurrentCompute;
    }

    public boolean isPrepareBeforeShadow() {
        return prepareBeforeShadow;
    }

    public boolean isOldLighting() {
        return oldLighting;
    }

    public float getSunPathRotation() {
        return sunPathRotation;
    }

    public Map<Integer, Boolean> getExplicitFlips(String pass) {
        return explicitFlips.getOrDefault(pass, Collections.emptyMap());
    }

    public PackRenderTargetDirectives getRenderTargetDirectives() {
        return renderTargetDirectives;
    }
}
