package net.oculus.shaderpack;

/**
 * Aggregated shader pack directives. The 1.16.5 code exposes a large number of
 * options; the 1.12.2 skeleton keeps the surface area but answers with safe
 * defaults until real parsing is ported.
 */
public final class PackDirectives {
    private final PackRenderTargetDirectives renderTargetDirectives;

    public PackDirectives() {
        this.renderTargetDirectives = new PackRenderTargetDirectives();
    }

    public PackDirectives(ShaderProperties properties) {
        this();
    }

    public CloudSetting getCloudSetting() {
        return CloudSetting.DEFAULT;
    }

    public boolean underwaterOverlay() {
        return true;
    }

    public boolean vignette() {
        return true;
    }

    public boolean shouldRenderSun() {
        return true;
    }

    public boolean shouldRenderMoon() {
        return true;
    }

    public boolean rainDepth() {
        return false;
    }

    public boolean areParticlesBeforeDeferred() {
        return false;
    }

    public boolean getConcurrentCompute() {
        return false;
    }

    public boolean isPrepareBeforeShadow() {
        return false;
    }

    public boolean isOldLighting() {
        return false;
    }

    public float getSunPathRotation() {
        return 0.0F;
    }

    public PackRenderTargetDirectives getRenderTargetDirectives() {
        return renderTargetDirectives;
    }
}
