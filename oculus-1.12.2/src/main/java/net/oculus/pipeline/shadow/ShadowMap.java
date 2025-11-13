package net.oculus.pipeline.shadow;

import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.util.Config;

/**
 * Tracks the state of the primary shadow map framebuffer. Rendering code will
 * eventually populate this with GL handles; for now it simply captures the
 * configuration derived from the shader pack.
 */
public final class ShadowMap {
    private final boolean enabled;
    private final int resolution;

    public ShadowMap(PackDirectives directives, ShaderProperties properties, Config config) {
        this.enabled = config.shadowsEnabled(directives);
        this.resolution = enabled ? config.shadowResolution(directives) : 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getResolution() {
        return resolution;
    }

    public void destroy() {
        // Stub: real implementation deletes GL framebuffers.
    }
}
